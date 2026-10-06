package io.histoquiz.game;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import io.histoquiz.question.Period;
import io.histoquiz.question.QuestionBank;
import jakarta.annotation.PreDestroy;

/**
 * Owns every room in memory, drives timers and broadcasts room snapshots on
 * {@code /topic/rooms/{code}}. All mutations of a room happen while holding its monitor.
 */
@Service
public class RoomService {

	private static final Logger log = LoggerFactory.getLogger(RoomService.class);
	private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final int CODE_LENGTH = 5;
	/** Grace period after the deadline, to absorb network latency. */
	static final Duration ANSWER_GRACE = Duration.ofMillis(500);
	static final Duration AUTO_ADVANCE = Duration.ofSeconds(8);
	static final Duration ROOM_TTL = Duration.ofMinutes(30);

	private final Map<String, Room> rooms = new ConcurrentHashMap<>();
	/** STOMP session id → (room code, player id), to detect disconnections. */
	private final Map<String, SessionRef> sessions = new ConcurrentHashMap<>();
	private final QuestionBank questionBank;
	private final SimpMessagingTemplate messaging;
	private final Clock clock;
	private final ScheduledExecutorService scheduler;
	private final RandomGenerator random = new SecureRandom();

	private record SessionRef(String code, String playerId) {
	}

	public RoomService(QuestionBank questionBank, SimpMessagingTemplate messaging, Clock clock) {
		this.questionBank = questionBank;
		this.messaging = messaging;
		this.clock = clock;
		this.scheduler = Executors.newScheduledThreadPool(2, Thread.ofPlatform().name("game-timer-", 0).daemon()
				.factory());
		scheduler.scheduleAtFixedRate(this::evictIdleRooms, 5, 5, TimeUnit.MINUTES);
	}

	@PreDestroy
	void shutdown() {
		scheduler.shutdownNow();
	}

	public Room.JoinResult create(String hostName, QuizSettings settings, boolean solo) {
		if (questionBank.countFor(settings.periods()) == 0) {
			throw new GameException(HttpStatus.BAD_REQUEST, "Aucune question disponible pour ces périodes.");
		}
		long now = now();
		for (int attempt = 0; attempt < 20; attempt++) {
			String code = randomCode();
			Room room = new Room(code, settings, solo, now);
			if (rooms.putIfAbsent(code, room) == null) {
				synchronized (room) {
					return room.join(hostName, now);
				}
			}
		}
		throw new GameException(HttpStatus.SERVICE_UNAVAILABLE, "Impossible de créer un salon, réessaie.");
	}

	public Room.JoinResult join(String code, String name) {
		Room room = room(code);
		synchronized (room) {
			Room.JoinResult result = room.join(name, now());
			broadcast(room);
			return result;
		}
	}

	public RoomView view(String code) {
		Room room = room(code);
		synchronized (room) {
			return room.view(now());
		}
	}

	/** Called when a client (re)connects its websocket and identifies itself. */
	public void hello(String code, String token, String sessionId) {
		withPlayer(code, token, (room, playerId) -> {
			sessions.put(sessionId, new SessionRef(room.code(), playerId));
			room.setConnected(playerId, true, now());
		});
	}

	public void disconnected(String sessionId) {
		SessionRef ref = sessions.remove(sessionId);
		if (ref == null) {
			return;
		}
		Room room = rooms.get(ref.code());
		if (room == null) {
			return;
		}
		synchronized (room) {
			boolean stillConnected = sessions.values().stream().anyMatch(ref::equals);
			if (!stillConnected) {
				room.setConnected(ref.playerId(), false, now());
				revealIfComplete(room);
				broadcast(room);
			}
		}
	}

	public void leave(String code, String token) {
		withPlayer(code, token, (room, playerId) -> {
			sessions.values().removeIf(ref -> ref.code().equals(room.code()) && ref.playerId().equals(playerId));
			room.leave(playerId, now());
			if (room.isEmpty()) {
				rooms.remove(room.code());
			}
			revealIfComplete(room);
		});
	}

	public void start(String code, String token) {
		withHost(code, token, room -> {
			Set<Period> periods = room.settings().periods();
			room.start(questionBank.draw(periods, room.settings().questionCount(), random), now());
			scheduleQuestionTimeout(room);
		});
	}

	public void answer(String code, String token, int questionIndex, int choice) {
		withPlayer(code, token, (room, playerId) -> {
			if (room.answer(playerId, questionIndex, choice, now())) {
				revealIfComplete(room);
			}
		});
	}

	/** Lets the host skip the end-of-round pause. */
	public void next(String code, String token) {
		withHost(code, token, room -> {
			if (room.phase() == Phase.REVEAL) {
				advance(room);
			}
		});
	}

	public void restart(String code, String token) {
		withHost(code, token, room -> room.restart(now()));
	}

	// --- timers -------------------------------------------------------------------------------

	private void scheduleQuestionTimeout(Room room) {
		long step = room.step();
		long delay = room.settings().secondsPerQuestion() * 1000L + ANSWER_GRACE.toMillis();
		schedule(room, step, delay, () -> {
			room.reveal(now());
			afterReveal(room);
		});
	}

	private void revealIfComplete(Room room) {
		if (room.phase() == Phase.QUESTION && room.everyoneAnswered()) {
			room.reveal(now());
			afterReveal(room);
		}
	}

	private void afterReveal(Room room) {
		// In solo mode the player reads the explanation at their own pace.
		if (!room.solo()) {
			schedule(room, room.step(), AUTO_ADVANCE.toMillis(), () -> advance(room));
		}
	}

	private void advance(Room room) {
		room.advance(now());
		if (room.phase() == Phase.QUESTION) {
			scheduleQuestionTimeout(room);
		}
	}

	/** Runs {@code action} later, unless the room moved on to another phase in the meantime. */
	private void schedule(Room room, long step, long delayMillis, Runnable action) {
		scheduler.schedule(() -> {
			try {
				synchronized (room) {
					if (room.step() == step && rooms.containsKey(room.code())) {
						action.run();
						broadcast(room);
					}
				}
			} catch (RuntimeException e) {
				log.error("Timer failed for room {}", room.code(), e);
			}
		}, delayMillis, TimeUnit.MILLISECONDS);
	}

	void evictIdleRooms() {
		long limit = now() - ROOM_TTL.toMillis();
		rooms.values().removeIf(room -> {
			synchronized (room) {
				return room.lastActivity() < limit && !room.hasConnectedPlayers();
			}
		});
	}

	// --- helpers ------------------------------------------------------------------------------

	private void withPlayer(String code, String token, PlayerAction action) {
		Room room = room(code);
		synchronized (room) {
			String playerId = room.playerIdForToken(token)
					.orElseThrow(() -> new GameException(HttpStatus.FORBIDDEN, "Joueur inconnu dans ce salon."));
			action.run(room, playerId);
			broadcast(room);
		}
	}

	private void withHost(String code, String token, Consumer<Room> action) {
		withPlayer(code, token, (room, playerId) -> {
			if (!room.isHost(playerId)) {
				throw new GameException(HttpStatus.FORBIDDEN, "Seul l'hôte peut faire ça.");
			}
			action.accept(room);
		});
	}

	@FunctionalInterface
	private interface PlayerAction {
		void run(Room room, String playerId);
	}

	private Room room(String code) {
		Room room = code == null ? null : rooms.get(code.strip().toUpperCase());
		if (room == null) {
			throw new GameException(HttpStatus.NOT_FOUND, "Salon introuvable.");
		}
		return room;
	}

	private void broadcast(Room room) {
		messaging.convertAndSend("/topic/rooms/" + room.code(), room.view(now()));
	}

	private String randomCode() {
		StringBuilder sb = new StringBuilder(CODE_LENGTH);
		for (int i = 0; i < CODE_LENGTH; i++) {
			sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
		}
		return sb.toString();
	}

	private long now() {
		return clock.millis();
	}
}
