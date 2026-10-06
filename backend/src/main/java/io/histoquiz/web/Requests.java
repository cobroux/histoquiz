package io.histoquiz.web;

import java.util.Set;

import io.histoquiz.question.Period;

/** Payloads received from clients, over REST and STOMP. */
public final class Requests {

	private Requests() {
	}

	public record CreateRoom(String name, Set<Period> periods, Integer questionCount, Integer secondsPerQuestion,
			boolean solo) {
	}

	public record JoinRoom(String name) {
	}

	public record PlayerAction(String token) {
	}

	public record AnswerAction(String token, int questionIndex, int choice) {
	}
}
