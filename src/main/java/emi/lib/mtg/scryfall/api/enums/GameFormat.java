package emi.lib.mtg.scryfall.api.enums;

import emi.lib.mtg.game.Format;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public enum GameFormat implements ApiEnum {
	Standard (Format.Standard),
	Modern (Format.Modern),
	Legacy (Format.Legacy),
	Vintage (Format.Vintage),
	Commander (Format.Commander),
	PauperCommander (null),
	Pauper (Format.Pauper),
	Penny (Format.Penny),
	Duel (null),
	Future (Format.Future),
	Brawl (Format.Brawl),
	OldSchool (null),
	Oathbreaker (null),
	Pioneer (Format.Pioneer),
	Historic (Format.Historic),
	HistoricBrawl (null),
	Gladiator (null),
	Premodern (null),
	Predh (null),
	Alchemy (Format.Alchemy),
	Explorer (Format.Explorer),
	Unrecognized (null);

	private static final Map<String, GameFormat> nameMap = createNameMap();

	static Map<String, GameFormat> createNameMap() {
		Map<String, GameFormat> tmp = new HashMap<>();

		for (GameFormat format : GameFormat.values()) {
			tmp.put(format.serialized, format);
		}

		return Collections.unmodifiableMap(tmp);
	}

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

	public static GameFormat byName(String name) {
		return nameMap.get(name);
	}
}
