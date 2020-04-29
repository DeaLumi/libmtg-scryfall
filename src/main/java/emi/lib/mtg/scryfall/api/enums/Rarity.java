package emi.lib.mtg.scryfall.api.enums;

public enum Rarity implements ApiEnum {
	Common,
	Uncommon,
	Rare,
	Mythic,
	Unrecognized;

	@Override
	public String serialized() {
		return this.name().toLowerCase();
	}
}
