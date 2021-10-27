package emi.lib.mtg.scryfall.api.enums;

import emi.lib.mtg.characteristic.CardRarity;

public enum Rarity implements ApiEnum {
	Common (CardRarity.Common),
	Uncommon (CardRarity.Uncommon),
	Rare (CardRarity.Rare),
	Mythic (CardRarity.MythicRare),
	Bonus (CardRarity.Special),
	Special (CardRarity.Special),
	Unrecognized (null);

	public final CardRarity libMtgRarity;

	private Rarity(CardRarity libMtgRarity) {
		this.libMtgRarity = libMtgRarity;
	}

	@Override
	public String serialized() {
		return this.name().toLowerCase();
	}
}
