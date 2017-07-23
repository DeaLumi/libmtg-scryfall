package emi.lib.mtg.scryfall.v2;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import emi.lib.mtg.v2.Card;
import emi.lib.mtg.v2.Set;

import java.util.UUID;

public class ScryfallSet implements Set {
	private emi.lib.scryfall.api.Set set;

	BiMap<UUID, ScryfallPrinting> printings;

	public ScryfallSet(emi.lib.scryfall.api.Set set) {
		this.set = set;
		this.printings = HashBiMap.create();

		this.set.name = Util.or(this.set.name, "");
		this.set.code = Util.or(this.set.code, "");
	}

	@Override
	public String name() {
		return this.set.name;
	}

	@Override
	public String code() {
		return this.set.code;
	}

	@Override
	public java.util.Set<? extends Card.Printing> printings() {
		return printings.values();
	}

	@Override
	public Card.Printing printing(UUID id) {
		return printings.get(id);
	}
}
