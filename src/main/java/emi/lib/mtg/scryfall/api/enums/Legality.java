package emi.lib.mtg.scryfall.api.enums;

public enum Legality implements ApiEnum {
	Legal,
	NotLegal,
	Restricted,
	Banned,
	Unrecognized;

	@Override
	public String serialized() {
		return this.name().toLowerCase();
	}
}
