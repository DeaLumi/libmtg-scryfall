package emi.lib.mtg.scryfall.api.enums;

public enum Rarity implements ApiEnum {
	Common (emi.lib.mtg.enums.Rarity.Common),
	Uncommon (emi.lib.mtg.enums.Rarity.Uncommon),
	Rare (emi.lib.mtg.enums.Rarity.Rare),
	Mythic (emi.lib.mtg.enums.Rarity.MythicRare),
	Bonus (emi.lib.mtg.enums.Rarity.Special),
	Special (emi.lib.mtg.enums.Rarity.Special),
	Unrecognized (null);

	public final emi.lib.mtg.enums.Rarity libMtgRarity;

	private Rarity(emi.lib.mtg.enums.Rarity libMtgRarity) {
		this.libMtgRarity = libMtgRarity;
	}

	@Override
	public String serialized() {
		return this.name().toLowerCase();
	}
}
