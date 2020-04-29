package emi.lib.mtg.scryfall.api.enums;

public enum GameFormat implements ApiEnum {
	Standard,
	Modern,
	Legacy,
	Vintage,
	Commander,
	Pauper,
	Penny,
	Duel,
	Future,
	Brawl,
	OldSchool,
	Pioneer,
	Historic,
	Unrecognized;

	private final String serialized;

	GameFormat() {
		this.serialized = name().toLowerCase();
	}

	GameFormat(String serialized) {
		this.serialized = serialized.toLowerCase();
	}

	@Override
	public String serialized() {
		return serialized;
	}
}
