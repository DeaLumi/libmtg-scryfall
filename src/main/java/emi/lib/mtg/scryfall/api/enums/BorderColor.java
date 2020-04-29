package emi.lib.mtg.scryfall.api.enums;

public enum BorderColor implements ApiEnum {
	Black,
	White,
	Silver,
	Gold,
	Borderless,
	Unrecognized;

	@Override
	public String serialized() {
		return name().toLowerCase();
	}
}
