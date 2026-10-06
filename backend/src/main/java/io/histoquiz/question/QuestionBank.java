package io.histoquiz.question;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component
public class QuestionBank {

	private final List<Question> questions;

	public QuestionBank(JsonMapper mapper) throws IOException {
		try (InputStream in = new ClassPathResource("questions.json").getInputStream()) {
			this.questions = List.copyOf(mapper.readValue(in, new TypeReference<List<Question>>() {
			}));
		}
	}

	public Map<Period, Integer> countByPeriod() {
		Map<Period, Integer> counts = new EnumMap<>(Period.class);
		for (Period p : Period.values()) {
			counts.put(p, 0);
		}
		questions.forEach(q -> counts.merge(q.period(), 1, Integer::sum));
		return counts;
	}

	public int countFor(Collection<Period> periods) {
		return (int) questions.stream().filter(q -> periods.contains(q.period())).count();
	}

	/** Picks up to {@code count} random questions from the given periods, with shuffled choices. */
	public List<RoundQuestion> draw(Collection<Period> periods, int count, RandomGenerator random) {
		List<Question> pool = new ArrayList<>(questions.stream().filter(q -> periods.contains(q.period())).toList());
		Collections.shuffle(pool, random);
		return pool.stream().limit(count).map(q -> RoundQuestion.from(q, random)).toList();
	}
}
