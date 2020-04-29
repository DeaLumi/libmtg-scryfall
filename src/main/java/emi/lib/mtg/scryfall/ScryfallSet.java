package emi.lib.mtg.scryfall;

import com.google.common.collect.HashBiMap;
import emi.lib.mtg.Card;
import emi.lib.mtg.Set;

import java.util.UUID;

class ScryfallSet implements Set {

	private final emi.lib.mtg.scryfall.api.Set setJson;

	final HashBiMap<UUID, ScryfallPrinting> printings;

	ScryfallSet(emi.lib.mtg.scryfall.api.Set setJson) {
		this.setJson = setJson;
		this.printings = HashBiMap.create();
	}

	@Override
	public String name() {
		return setJson.name;
	}

	@Override
	public String code() {
		return setJson.code;
	}

	@Override
	public java.util.Set<? extends Card.Printing> printings() {
		return printings.values();
	}

	@Override
	public Card.Printing printing(UUID id) {
		return printings.get(id);
	}

	ScryfallSet printing(ScryfallPrinting printing) {
		this.printings.put(printing.id(), printing);
		return this;
	}
}
