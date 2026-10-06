package io.histoquiz.question;

public enum Period {
	PREMIERE_GUERRE("Première Guerre mondiale", "1914-1918"),
	ENTRE_DEUX_GUERRES("Entre-deux-guerres", "1919-1939"),
	SECONDE_GUERRE("Seconde Guerre mondiale", "1939-1945"),
	GUERRE_FROIDE("Guerre froide", "1947-1991");

	private final String label;
	private final String years;

	Period(String label, String years) {
		this.label = label;
		this.years = years;
	}

	public String label() {
		return label;
	}

	public String years() {
		return years;
	}
}
