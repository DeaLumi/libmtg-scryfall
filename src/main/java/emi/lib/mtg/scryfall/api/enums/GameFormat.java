package emi.lib.mtg.scryfall.api.enums;

import emi.lib.mtg.game.Format;

public enum GameFormat implements ApiEnum {
	Standard (Format.Standard),
	Modern (Format.Modern),
	Legacy (Format.Legacy),
	Vintage (Format.Vintage),
	Commander (Format.Commander),
	Pauper (Format.Pauper),
	Penny (Format.Penny),
	Duel (null),
	Future (Format.Future),
	Brawl (Format.Brawl),
	OldSchool (null),
	Pioneer (Format.Pioneer),
	Historic (Format.Historic),
	Unrecognized (null);

	public final Format libMtgFormat;
	private final String serialized;

	GameFormat(Format libMtgFormat) {
		this.libMtgFormat = libMtgFormat;
		this.serialized = name().toLowerCase();
	}

	GameFormat(Format libMtgFormat, String serialized) {
		this.libMtgFormat = libMtgFormat;
		this.serialized = serialized.toLowerCase();
	}

	@Override
	public String serialized() {
		return serialized;
	}
}
