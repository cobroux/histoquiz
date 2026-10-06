package io.histoquiz.game;

public enum Phase {
	/** Waiting room: players join, the host starts the game. */
	LOBBY,
	/** A question is open and players can answer until the deadline. */
	QUESTION,
	/** The correct answer and the scores of the round are shown. */
	REVEAL,
	/** Final leaderboard. */
	FINISHED
}
