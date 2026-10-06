package io.histoquiz.game;

import java.util.EnumSet;
import java.util.Set;

import io.histoquiz.question.Period;

public record QuizSettings(Set<Period> periods, int questionCount, int secondsPerQuestion) {

	public static final int MIN_QUESTIONS = 3;
	public static final int MAX_QUESTIONS = 30;
	public static final int MIN_SECONDS = 5;
	public static final int MAX_SECONDS = 60;

	/** Normalises user input: no period means all of them, numbers are clamped to sane bounds. */
	public static QuizSettings of(Set<Period> periods, Integer questionCount, Integer secondsPerQuestion) {
		Set<Period> p = periods == null || periods.isEmpty() ? EnumSet.allOf(Period.class) : EnumSet.copyOf(periods);
		int count = clamp(questionCount == null ? 10 : questionCount, MIN_QUESTIONS, MAX_QUESTIONS);
		int seconds = clamp(secondsPerQuestion == null ? 20 : secondsPerQuestion, MIN_SECONDS, MAX_SECONDS);
		return new QuizSettings(Set.copyOf(p), count, seconds);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
