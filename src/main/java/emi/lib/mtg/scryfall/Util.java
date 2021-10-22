package emi.lib.mtg.scryfall;

import emi.lib.mtg.characteristic.CardRarity;
import emi.lib.mtg.characteristic.Color;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

class Util {
	static int or(Integer in, int def) {
		return in != null ? in : def;
	}

	static String or(String in, String def) {
		return in != null ? in : def;
	}

	static <T> T or(T in, T def) {
		return in != null ? in : def;
	}

	static <T> Set<T> orEmpty(Set<T> in) {
		return in != null ? in : Collections.emptySet();
	}

	static Color.Combination mapColor(Set<emi.lib.mtg.scryfall.api.enums.Color> apiColors) {
		return apiColors == null ? Color.Combination.Empty : apiColors.stream().map(c -> c.libMtgColor).collect(Color.Combination.COLOR_COLLECTOR);
	}

	static CardRarity mapRarity(emi.lib.mtg.scryfall.api.Card card) {
		if (card.typeLine != null && card.typeLine.contains("Basic Land")) {
			return CardRarity.BasicLand;
		}

		assert card.rarity.libMtgRarity != null : "The rarity of card " + card.name + " was unrecognized.";
		return card.rarity.libMtgRarity;
	}
}
