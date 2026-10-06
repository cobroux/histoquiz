package io.histoquiz.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.histoquiz.question.Period;
import io.histoquiz.question.RoundQuestion;

class RoomTest {

	private static final List<RoundQuestion> QUESTIONS = List.of(
			new RoundQuestion(Period.PREMIERE_GUERRE, "Q1", List.of("a", "b", "c", "d"), 2, "e1"),
			new RoundQuestion(Period.GUERRE_FROIDE, "Q2", List.of("a", "b", "c", "d"), 0, "e2"));

	private Room room;
	private Room.JoinResult alice;
	private Room.JoinResult bob;

	@BeforeEach
	void setUp() {
		room = new Room("ABCDE", QuizSettings.of(Set.of(), 2, 10), false, 0);
		alice = room.join("Alice", 0);
		bob = room.join("Bob", 0);
	}

	@Test
	void firstPlayerIsHost() {
		assertThat(room.isHost(alice.playerId())).isTrue();
		assertThat(room.isHost(bob.playerId())).isFalse();
	}

	@Test
	void rejectsDuplicateOrInvalidNames() {
		assertThatThrownBy(() -> room.join("alice", 0)).isInstanceOf(GameException.class);
		assertThatThrownBy(() -> room.join("   ", 0)).isInstanceOf(GameException.class);
		assertThatThrownBy(() -> room.join("x".repeat(21), 0)).isInstanceOf(GameException.class);
	}

	@Test
	void questionViewHidesAnswerUntilReveal() {
		room.start(QUESTIONS, 1_000);
		RoomView view = room.view(1_000);
		assertThat(view.phase()).isEqualTo(Phase.QUESTION);
		assertThat(view.question().text()).isEqualTo("Q1");
		assertThat(view.reveal()).isNull();
		assertThat(view.deadline()).isEqualTo(11_000);
	}

	@Test
	void fasterCorrectAnswersEarnMorePoints() {
		room.start(QUESTIONS, 0);
		assertThat(room.answer(alice.playerId(), 0, 2, 0)).isFalse();
		assertThat(room.answer(bob.playerId(), 0, 2, 5_000)).isTrue();
		room.reveal(5_000);

		RoomView view = room.view(5_000);
		assertThat(view.reveal().correctIndex()).isEqualTo(2);
		assertThat(view.reveal().answerCounts()).containsExactly(0, 0, 2, 0);
		assertThat(view.players()).extracting(RoomView.PlayerView::score).containsExactly(1000, 750);
		assertThat(view.players()).extracting(RoomView.PlayerView::correctCount).containsExactly(1, 1);
	}

	@Test
	void wrongLateOrDuplicateAnswersEarnNothing() {
		room.start(QUESTIONS, 0);
		room.answer(alice.playerId(), 0, 1, 1_000);
		assertThat(room.answer(alice.playerId(), 0, 2, 2_000)).isFalse();
		assertThat(room.answer(bob.playerId(), 0, 2, 20_000)).isFalse();
		room.reveal(20_000);

		RoomView view = room.view(20_000);
		assertThat(view.players()).extracting(RoomView.PlayerView::score).containsExactly(0, 0);
		assertThat(view.players()).extracting(RoomView.PlayerView::lastCorrect).containsExactly(false, null);
	}

	@Test
	void tracksStreaks() {
		room.start(QUESTIONS, 0);
		room.answer(alice.playerId(), 0, 2, 0);
		room.answer(bob.playerId(), 0, 1, 0);
		room.reveal(0);
		room.advance(0);
		room.answer(alice.playerId(), 1, 0, 0);
		room.reveal(0);

		RoomView view = room.view(0);
		assertThat(view.players()).extracting(RoomView.PlayerView::streak).containsExactly(2, 0);
		assertThat(view.players()).extracting(RoomView.PlayerView::bestStreak).containsExactly(2, 0);
	}

	@Test
	void disconnectedPlayersDoNotBlockTheRound() {
		room.start(QUESTIONS, 0);
		room.setConnected(bob.playerId(), false, 0);
		assertThat(room.answer(alice.playerId(), 0, 2, 100)).isTrue();
	}

	@Test
	void hostIsHandedOverWhenItDisconnects() {
		room.setConnected(alice.playerId(), false, 0);
		assertThat(room.isHost(bob.playerId())).isTrue();
	}

	@Test
	void gameFinishesAfterLastQuestionAndCanRestart() {
		room.start(QUESTIONS, 0);
		room.reveal(10_000);
		room.advance(10_000);
		assertThat(room.view(10_000).questionIndex()).isEqualTo(1);
		room.reveal(20_000);
		room.advance(20_000);
		assertThat(room.phase()).isEqualTo(Phase.FINISHED);

		room.restart(30_000);
		assertThat(room.phase()).isEqualTo(Phase.LOBBY);
		assertThat(room.view(30_000).players()).hasSize(2);
	}

	@Test
	void cannotJoinOnceStartedOrASoloRoom() {
		room.start(QUESTIONS, 0);
		assertThatThrownBy(() -> room.join("Carol", 0)).isInstanceOf(GameException.class);

		Room solo = new Room("SOLO1", QuizSettings.of(Set.of(), 5, 10), true, 0);
		solo.join("Alice", 0);
		assertThatThrownBy(() -> solo.join("Bob", 0)).isInstanceOf(GameException.class);
	}
}
