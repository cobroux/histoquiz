package io.histoquiz.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import io.histoquiz.game.GameException;
import io.histoquiz.game.RoomService;

/** Game actions sent by clients to {@code /app/rooms/{code}/...}. State comes back on the room topic. */
@Controller
public class GameSocketController {

	private static final Logger log = LoggerFactory.getLogger(GameSocketController.class);

	private final RoomService rooms;

	public GameSocketController(RoomService rooms) {
		this.rooms = rooms;
	}

	@MessageMapping("/rooms/{code}/hello")
	public void hello(@DestinationVariable String code, @Payload Requests.PlayerAction action,
			SimpMessageHeaderAccessor headers) {
		rooms.hello(code, action.token(), headers.getSessionId());
	}

	@MessageMapping("/rooms/{code}/start")
	public void start(@DestinationVariable String code, @Payload Requests.PlayerAction action) {
		rooms.start(code, action.token());
	}

	@MessageMapping("/rooms/{code}/answer")
	public void answer(@DestinationVariable String code, @Payload Requests.AnswerAction action) {
		rooms.answer(code, action.token(), action.questionIndex(), action.choice());
	}

	@MessageMapping("/rooms/{code}/next")
	public void next(@DestinationVariable String code, @Payload Requests.PlayerAction action) {
		rooms.next(code, action.token());
	}

	@MessageMapping("/rooms/{code}/restart")
	public void restart(@DestinationVariable String code, @Payload Requests.PlayerAction action) {
		rooms.restart(code, action.token());
	}

	@MessageMapping("/rooms/{code}/leave")
	public void leave(@DestinationVariable String code, @Payload Requests.PlayerAction action) {
		rooms.leave(code, action.token());
	}

	@EventListener
	public void onDisconnect(SessionDisconnectEvent event) {
		rooms.disconnected(event.getSessionId());
	}

	@MessageExceptionHandler(GameException.class)
	public void onGameException(GameException e) {
		log.debug("Rejected game action: {}", e.getMessage());
	}
}
