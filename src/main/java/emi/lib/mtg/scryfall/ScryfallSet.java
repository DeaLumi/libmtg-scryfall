package emi.lib.mtg.scryfall;

import emi.lib.mtg.Card;
import emi.lib.mtg.Set;
import emi.lib.mtg.scryfall.util.MirrorMap;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.UUID;

class ScryfallSet implements Set {

	private final emi.lib.mtg.scryfall.api.Set setJson;

	final MirrorMap<UUID, ScryfallPrinting> printings;

	ScryfallSet(emi.lib.mtg.scryfall.api.Set setJson) {
		this.setJson = setJson;
		this.printings = new MirrorMap<>(HashMap::new);
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
	public LocalDate releaseDate() {
		return setJson.releasedAt;
	}

	@Override
	public boolean digital() {
		return setJson.digital;
	}

	@Override
	public java.util.Set<? extends Card.Printing> printings() {
		return printings.valueSet();
	}

	@Override
	public Card.Printing printing(UUID id) {
		return printings.get(id);
	}
}
