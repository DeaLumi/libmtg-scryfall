package emi.lib.mtg.scryfall;

import emi.lib.mtg.enums.Rarity;
import emi.lib.mtg.enums.Color;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.Function;

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

	static Rarity mapRarity(emi.lib.mtg.scryfall.api.Card card) {
		if (card.typeLine != null && card.typeLine.contains("Basic Land")) {
			return Rarity.BasicLand;
		}

		assert card.rarity.libMtgRarity != null : "The rarity of card " + card.name + " was unrecognized.";
		return card.rarity.libMtgRarity;
	}

	static String cardPrintingKey(String setCode, String collectorNumber) {
		return setCode.toUpperCase() + " " + collectorNumber;
	}

	/**
	 * Utility method to gradually expand a set implementation. To try to keep memory down, cards and printings in this
	 * library start with various sets as EMPTY_SETs. Adding an element converts it to a Collections.singleton. After
	 * that, it's up to the constructor what it becomes. The idea is to minimize the complexity of the used set
	 * implementations.
	 *
	 * @param to The set to which to add the element. Can be null.
	 * @param element The element to add to the set.
	 * @param constructor The constructor to use if the set is being promoted to a full set implementation.
	 * @return The set to which the element was added. May differ from the passed element.
	 * @param <T> The type of elements contained in the set.
	 */
	static <T> Set<T> addElem(Set<T> to, T element, Function<Collection<T>, ? extends Set<T>> constructor) {
		if (to == null || to == Collections.emptySet()) {
			to = Collections.singleton(element);
		} else if (!to.contains(element)) {
			if (to.size() == 1) to = constructor.apply(to);
			to.add(element);
		}

		return to;
	}
}
