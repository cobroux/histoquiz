package io.histoquiz.question;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class QuestionBankTest {

	@Autowired
	QuestionBank bank;

	@Test
	void everyPeriodHasEnoughQuestions() {
		assertThat(bank.countByPeriod().values()).allSatisfy(count -> assertThat(count).isGreaterThanOrEqualTo(15));
	}

	@Test
	void drawnQuestionsKeepTrackOfTheCorrectChoice() {
		List<RoundQuestion> drawn = bank.draw(EnumSet.allOf(Period.class), 200, new Random(42));
		assertThat(drawn).hasSize(bank.countFor(EnumSet.allOf(Period.class)));
		assertThat(drawn).allSatisfy(q -> {
			assertThat(q.choices()).hasSize(4).doesNotHaveDuplicates();
			assertThat(q.correctIndex()).isBetween(0, 3);
		});
	}

	@Test
	void drawOnlyUsesRequestedPeriods() {
		assertThat(bank.draw(Set.of(Period.GUERRE_FROIDE), 10, new Random(1)))
				.allSatisfy(q -> assertThat(q.period()).isEqualTo(Period.GUERRE_FROIDE));
	}
}
