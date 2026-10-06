package io.histoquiz.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import io.histoquiz.question.RoundQuestion;

/**
 * State machine of one game. Not thread-safe: {@link RoomService} synchronises on the room.
 * Every method takes the current time so the logic stays deterministic in tests.
 */
public class Room {

	public static final int MAX_PLAYERS = 30;
	public static final int MAX_NAME_LENGTH = 20;
	static final int BASE_POINTS = 500;
	static final int SPEED_POINTS = 500;

	private final String code;
	private final QuizSettings settings;
	private final boolean solo;
	private final Map<String, Player> players = new LinkedHashMap<>();
	private String hostId;

	private Phase phase = Phase.LOBBY;
	private List<RoundQuestion> questions = List.of();
	private int index = -1;
	private long questionStart;
	private long deadline;
	private final Map<String, Answer> answers = new HashMap<>();
	/** Incremented on every phase change so stale timers can detect they are outdated. */
	private long step;
	private long lastActivity;

	private record Answer(int choice, long at) {
	}

	public Room(String code, QuizSettings settings, boolean solo, long now) {
		this.code = code;
		this.settings = settings;
		this.solo = solo;
		this.lastActivity = now;
	}

	public String code() {
		return code;
	}

	public QuizSettings settings() {
		return settings;
	}

	public boolean solo() {
		return solo;
	}

	public Phase phase() {
		return phase;
	}

	public long step() {
		return step;
	}

	public long lastActivity() {
		return lastActivity;
	}

	public boolean hasConnectedPlayers() {
		return players.values().stream().anyMatch(p -> p.connected);
	}

	/** Adds a player and returns its secret token. The first player becomes the host. */
	public JoinResult join(String rawName, long now) {
		String name = rawName == null ? "" : rawName.strip();
		if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
			throw new GameException(HttpStatus.BAD_REQUEST,
					"Le pseudo doit faire entre 1 et " + MAX_NAME_LENGTH + " caractères.");
		}
		if (phase != Phase.LOBBY) {
			throw new GameException(HttpStatus.CONFLICT, "La partie a déjà commencé.");
		}
		if (solo && !players.isEmpty()) {
			throw new GameException(HttpStatus.CONFLICT, "Cette partie est en mode solo.");
		}
		if (players.size() >= MAX_PLAYERS) {
			throw new GameException(HttpStatus.CONFLICT, "Le salon est complet.");
		}
		if (players.values().stream().anyMatch(p -> p.name.equalsIgnoreCase(name))) {
			throw new GameException(HttpStatus.CONFLICT, "Ce pseudo est déjà pris dans ce salon.");
		}
		String id = UUID.randomUUID().toString().substring(0, 8);
		String token = UUID.randomUUID().toString();
		players.put(id, new Player(id, token, name));
		if (hostId == null) {
			hostId = id;
		}
		touch(now);
		return new JoinResult(code, id, token);
	}

	public Optional<String> playerIdForToken(String token) {
		if (token == null) {
			return Optional.empty();
		}
		return players.values().stream().filter(p -> p.token.equals(token)).map(p -> p.id).findFirst();
	}

	public boolean isHost(String playerId) {
		return playerId != null && playerId.equals(hostId);
	}

	public void setConnected(String playerId, boolean connected, long now) {
		Player player = players.get(playerId);
		if (player == null) {
			return;
		}
		player.connected = connected;
		if (!connected && isHost(playerId)) {
			players.values().stream().filter(p -> p.connected).findFirst().ifPresent(p -> hostId = p.id);
		}
		Player host = hostId == null ? null : players.get(hostId);
		if (connected && (host == null || !host.connected)) {
			hostId = playerId;
		}
		touch(now);
	}

	/** Leaving the lobby removes the player; leaving mid-game only marks them disconnected. */
	public void leave(String playerId, long now) {
		if (phase == Phase.LOBBY) {
			players.remove(playerId);
			if (isHost(playerId)) {
				hostId = players.values().stream().filter(p -> p.connected).map(p -> p.id).findFirst()
						.orElse(players.keySet().stream().findFirst().orElse(null));
			}
			touch(now);
		} else {
			setConnected(playerId, false, now);
		}
	}

	public boolean isEmpty() {
		return players.isEmpty();
	}

	public void start(List<RoundQuestion> drawn, long now) {
		if (phase != Phase.LOBBY) {
			throw new GameException(HttpStatus.CONFLICT, "La partie a déjà commencé.");
		}
		if (drawn.isEmpty()) {
			throw new GameException(HttpStatus.BAD_REQUEST, "Aucune question disponible pour ces périodes.");
		}
		questions = List.copyOf(drawn);
		players.values().forEach(p -> {
			p.score = 0;
			p.correctCount = 0;
			p.streak = 0;
			p.bestStreak = 0;
			p.resetRound();
		});
		index = -1;
		nextQuestion(now);
	}

	/** Records an answer. Returns true when every connected player has answered. */
	public boolean answer(String playerId, int questionIndex, int choice, long now) {
		if (phase != Phase.QUESTION || questionIndex != index || !players.containsKey(playerId)
				|| answers.containsKey(playerId) || now > deadline) {
			return false;
		}
		if (choice < 0 || choice >= currentQuestion().choices().size()) {
			return false;
		}
		answers.put(playerId, new Answer(choice, now));
		touch(now);
		return everyoneAnswered();
	}

	public boolean everyoneAnswered() {
		List<Player> connected = players.values().stream().filter(p -> p.connected).toList();
		return !connected.isEmpty() && connected.stream().allMatch(p -> answers.containsKey(p.id));
	}

	/** Closes the current question and awards points. */
	public void reveal(long now) {
		if (phase != Phase.QUESTION) {
			return;
		}
		RoundQuestion q = currentQuestion();
		long duration = Math.max(1, deadline - questionStart);
		for (Player p : players.values()) {
			Answer a = answers.get(p.id);
			boolean correct = a != null && a.choice() == q.correctIndex();
			int points = 0;
			if (correct) {
				long remaining = Math.max(0, deadline - a.at());
				points = BASE_POINTS + (int) Math.round(SPEED_POINTS * (double) remaining / duration);
			}
			p.lastCorrect = a == null ? null : correct;
			p.lastPoints = points;
			p.score += points;
			if (correct) {
				p.correctCount++;
				p.streak++;
				p.bestStreak = Math.max(p.bestStreak, p.streak);
			} else {
				p.streak = 0;
			}
		}
		phase = Phase.REVEAL;
		step++;
		touch(now);
	}

	/** Moves from the reveal to the next question, or to the final leaderboard. */
	public void advance(long now) {
		if (phase != Phase.REVEAL) {
			return;
		}
		if (index + 1 < questions.size()) {
			nextQuestion(now);
		} else {
			phase = Phase.FINISHED;
			step++;
			touch(now);
		}
	}

	/** Back to the lobby with the same players, to play again. */
	public void restart(long now) {
		if (phase != Phase.FINISHED) {
			return;
		}
		players.values().removeIf(p -> !p.connected);
		players.values().forEach(p -> {
			p.score = 0;
			p.correctCount = 0;
			p.streak = 0;
			p.bestStreak = 0;
			p.resetRound();
		});
		questions = List.of();
		index = -1;
		answers.clear();
		phase = Phase.LOBBY;
		step++;
		touch(now);
	}

	private void nextQuestion(long now) {
		index++;
		answers.clear();
		players.values().forEach(Player::resetRound);
		questionStart = now;
		deadline = now + settings.secondsPerQuestion() * 1000L;
		phase = Phase.QUESTION;
		step++;
		touch(now);
	}

	private RoundQuestion currentQuestion() {
		return questions.get(index);
	}

	private void touch(long now) {
		lastActivity = now;
	}

	public RoomView view(long now) {
		boolean revealed = phase == Phase.REVEAL;
		List<RoomView.PlayerView> playerViews = new ArrayList<>();
		for (Player p : players.values()) {
			playerViews.add(new RoomView.PlayerView(p.id, p.name, p.score, p.correctCount, p.streak, p.bestStreak, p.connected, answers.containsKey(p.id),
					revealed ? p.lastPoints : null, revealed ? p.lastCorrect : null));
		}
		RoomView.QuestionView question = null;
		RoomView.Reveal reveal = null;
		if (phase == Phase.QUESTION || phase == Phase.REVEAL) {
			RoundQuestion q = currentQuestion();
			question = new RoomView.QuestionView(q.period(), q.period().label(), q.text(), q.choices());
			if (revealed) {
				reveal = new RoomView.Reveal(q.correctIndex(), q.explanation(), answerCounts(q, answers.values()));
			}
		}
		return new RoomView(code, phase, solo, hostId,
				new RoomView.Settings(settings.periods(), settings.questionCount(), settings.secondsPerQuestion()),
				playerViews, index, questions.isEmpty() ? settings.questionCount() : questions.size(), question,
				phase == Phase.QUESTION ? deadline : 0, now, reveal);
	}

	private static List<Integer> answerCounts(RoundQuestion q, Collection<Answer> answers) {
		int[] counts = new int[q.choices().size()];
		answers.forEach(a -> counts[a.choice()]++);
		return Arrays.stream(counts).boxed().toList();
	}

	long deadline() {
		return deadline;
	}

	public record JoinResult(String code, String playerId, String token) {
	}
}
