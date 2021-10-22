package emi.lib.mtg.scryfall.api.enums;

public enum Finish implements ApiEnum {
	Nonfoil,
	Foil,
	Etched,
	Glossy,
	Unrecognized;

	@Override
	public String serialized() {
		return name();
	}
}
