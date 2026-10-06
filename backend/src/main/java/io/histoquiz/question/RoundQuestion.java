package io.histoquiz.question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

/** A question as played in a game: choices shuffled, correct answer tracked by index. */
public record RoundQuestion(Period period, String text, List<String> choices, int correctIndex, String explanation) {

	public static RoundQuestion from(Question q, RandomGenerator random) {
		String correct = q.choices().getFirst();
		List<String> shuffled = new ArrayList<>(q.choices());
		Collections.shuffle(shuffled, random);
		return new RoundQuestion(q.period(), q.question(), List.copyOf(shuffled), shuffled.indexOf(correct),
				q.explanation());
	}
}
