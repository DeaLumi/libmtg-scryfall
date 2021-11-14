package emi.lib.mtg.scryfall.api.enums;

import emi.lib.mtg.Set;

public enum SetType implements ApiEnum {
	Core (Set.Type.Standard),
	Expansion (Set.Type.Standard),
	Masters (Set.Type.Remaster),
	Masterpiece (Set.Type.Remaster),
	FromTheVault (Set.Type.Remaster),
	Spellbook (Set.Type.Remaster),
	PremiumDeck (Set.Type.Precon),
	DuelDeck (Set.Type.Precon),
	DraftInnovation (Set.Type.Standard),
	TreasureChest (Set.Type.Other),
	Commander (Set.Type.Precon),
	Planechase (Set.Type.Other),
	Archenemy (Set.Type.Other),
	Vanguard (Set.Type.Other),
	Funny (Set.Type.Standard),
	Starter (Set.Type.Standard),
	Box (Set.Type.Remaster),
	Promo (Set.Type.Other),
	Token (Set.Type.Other),
	Memorabilia (Set.Type.Other),
	Unrecognized (null);

	public final Set.Type libmtgType;

	SetType(Set.Type libmtgType) {
		this.libmtgType = libmtgType;
	}

	@Override
	public String serialized() {
		return this.name().toLowerCase();
	}
}
