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

	static EnumSet<Color> mapColor(Set<emi.lib.mtg.scryfall.api.enums.Color> apiColors) {
		EnumSet<Color> out = EnumSet.noneOf(Color.class);

		if (apiColors == null) {
			return out;
		}

		for (emi.lib.mtg.scryfall.api.enums.Color color : apiColors) {
			assert color.libMtgColor != null : "Bwuh?";
			out.add(color.libMtgColor);
		}

		return out;
	}

	static CardRarity mapRarity(emi.lib.mtg.scryfall.api.Card card) {
		if (card.typeLine != null && card.typeLine.contains("Basic Land")) {
			return CardRarity.BasicLand;
		}

		assert card.rarity.libMtgRarity != null : "The rarity of card " + card.name + " was unrecognized.";
		return card.rarity.libMtgRarity;
	}
}
