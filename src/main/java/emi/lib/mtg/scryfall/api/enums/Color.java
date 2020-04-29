package emi.lib.mtg.scryfall.api.enums;

public enum Color implements ApiEnum {
	White,
	Blue ("U"),
	Black,
	Red,
	Green,
	Unrecognized ("unrecognized");

	private final String serialized;

	Color() {
		this.serialized = name().substring(0, 1);
	}

	Color(String serialized) {
		this.serialized = serialized;
	}

	@Override
	public String serialized() {
		return serialized;
	}
}
