package emi.lib.mtg.scryfall.api.enums;

public enum SetType implements ApiEnum {
	Core,
	Expansion,
	Masters,
	Masterpiece,
	FromTheVault,
	PremiumDeck,
	DuelDeck,
	Commander,
	Planechase,
	Conspiracy,
	Archenemy,
	Vanguard,
	Funny,
	Starter,
	Box,
	Promo,
	Token,
	TreasureChest,
	Memorabilia,
	Spellbook,
	Draftinnovation,
	Unrecognized;

	@Override
	public String serialized() {
		return this.name().toLowerCase();
	}
}
