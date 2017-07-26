package emi.lib.mtg.scryfall;

import emi.lib.mtg.characteristic.CardRarity;
import emi.lib.mtg.characteristic.Color;
import emi.lib.scryfall.api.enums.Rarity;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public class Util {
	private static int or(Integer in, int def) {
		return in != null ? in : def;
	}

	static String or(String in, String def) {
		return in != null ? in : def;
	}

	private static <T> Set<T> orEmpty(Set<T> in) {
		return in != null ? in : Collections.emptySet();
	}

	static EnumSet<Color> mapColor(Set<emi.lib.scryfall.api.enums.Color> apiColors) {
		EnumSet<Color> out = EnumSet.noneOf(Color.class);

		for (emi.lib.scryfall.api.enums.Color color : apiColors) {
			switch (color) {
				case White:
					out.add(Color.WHITE);
					break;
				case Blue:
					out.add(Color.BLUE);
					break;
				case Black:
					out.add(Color.BLACK);
					break;
				case Red:
					out.add(Color.RED);
					break;
				case Green:
					out.add(Color.GREEN);
					break;
				default:
					assert false : "Bwuh?";
					break;
			}
		}

		return out;
	}

	static CardRarity mapRarity(emi.lib.scryfall.api.Card card) {
		switch (card.rarity) {
			case Common:
				if (card.typeLine != null && card.typeLine.contains("Basic Land")) {
					return CardRarity.BasicLand;
				} else {
					return CardRarity.Common;
				}
			case Uncommon:
				return CardRarity.Uncommon;
			case Rare:
				return CardRarity.Rare;
			case Mythic:
				return CardRarity.MythicRare; // TODO: Try and figure out 'special' rarity cards?
			default:
				assert false;
				return null;
		}
	}
}
