package io.histoquiz.question;

import java.util.List;

/**
 * A question as stored in questions.json. The first choice is always the
 * correct one; choices are shuffled when a game is built.
 */
public record Question(Period period, String question, List<String> choices, String explanation) {

	public Question {
		if (choices == null || choices.size() < 2) {
			throw new IllegalArgumentException("A question needs at least two choices: " + question);
		}
		choices = List.copyOf(choices);
	}
}
