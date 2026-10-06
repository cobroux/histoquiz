package io.histoquiz.game;

import java.util.List;
import java.util.Set;

import io.histoquiz.question.Period;

/** Snapshot of a room broadcast to every client. It never leaks the answer before the reveal. */
public record RoomView(
		String code,
		Phase phase,
		boolean solo,
		String hostId,
		Settings settings,
		List<PlayerView> players,
		int questionIndex,
		int questionCount,
		QuestionView question,
		long deadline,
		long serverTime,
		Reveal reveal) {

	public record Settings(Set<Period> periods, int questionCount, int secondsPerQuestion) {
	}

	public record PlayerView(String id, String name, int score, int correctCount, int streak, int bestStreak,
			boolean connected, boolean answered,
			Integer lastPoints, Boolean lastCorrect) {
	}

	public record QuestionView(Period period, String periodLabel, String text, List<String> choices) {
	}

	public record Reveal(int correctIndex, String explanation, List<Integer> answerCounts) {
	}
}
