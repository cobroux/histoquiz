package io.histoquiz.web;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.histoquiz.game.QuizSettings;
import io.histoquiz.game.Room;
import io.histoquiz.game.RoomService;
import io.histoquiz.game.RoomView;
import io.histoquiz.question.Period;
import io.histoquiz.question.QuestionBank;

@RestController
@RequestMapping("/api")
public class RoomController {

	private final RoomService rooms;
	private final QuestionBank questionBank;

	public RoomController(RoomService rooms, QuestionBank questionBank) {
		this.rooms = rooms;
		this.questionBank = questionBank;
	}

	public record PeriodInfo(Period id, String label, String years, int questionCount) {
	}

	@GetMapping("/periods")
	public List<PeriodInfo> periods() {
		Map<Period, Integer> counts = questionBank.countByPeriod();
		return Arrays.stream(Period.values()).map(p -> new PeriodInfo(p, p.label(), p.years(), counts.get(p)))
				.toList();
	}

	@PostMapping("/rooms")
	@ResponseStatus(HttpStatus.CREATED)
	public Room.JoinResult create(@RequestBody Requests.CreateRoom request) {
		QuizSettings settings = QuizSettings.of(request.periods(), request.questionCount(),
				request.secondsPerQuestion());
		return rooms.create(request.name(), settings, request.solo());
	}

	@PostMapping("/rooms/{code}/players")
	@ResponseStatus(HttpStatus.CREATED)
	public Room.JoinResult join(@PathVariable String code, @RequestBody Requests.JoinRoom request) {
		return rooms.join(code, request.name());
	}

	@GetMapping("/rooms/{code}")
	public RoomView room(@PathVariable String code) {
		return rooms.view(code);
	}
}
