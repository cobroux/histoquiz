package io.histoquiz.game;

class Player {

	final String id;
	/** Secret used by the client to act as this player; never broadcast. */
	final String token;
	final String name;
	int score;
	int correctCount;
	boolean connected = true;
	Integer lastPoints;
	Boolean lastCorrect;

	Player(String id, String token, String name) {
		this.id = id;
		this.token = token;
		this.name = name;
	}

	void resetRound() {
		lastPoints = null;
		lastCorrect = null;
	}
}
