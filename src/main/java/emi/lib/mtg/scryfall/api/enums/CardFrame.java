package emi.lib.mtg.scryfall.api.enums;

public enum CardFrame implements ApiEnum {
	Old1993 ("1993"),
	Old1997 ("1997"),
	Modern2001 ("2001"),
	Modern2003 ("2003"),
	Khans2015 ("2015"),
	Future ("future"),
	Unrecognized("unrecognized");

	private final String serialized;

	CardFrame(String serialized) {
		this.serialized = serialized;
	}

	@Override
	public String serialized() {
		return this.serialized;
	}
}
