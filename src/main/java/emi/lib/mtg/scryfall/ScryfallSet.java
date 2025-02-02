package emi.lib.mtg.scryfall;

import emi.lib.mtg.Card;
import emi.lib.mtg.Set;
import emi.lib.mtg.scryfall.util.MirrorMap;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

class ScryfallSet implements Set {

	private final emi.lib.mtg.scryfall.api.Set setJson;

	final MirrorMap<UUID, ScryfallPrint> prints;
	final Map<String, ScryfallPrint> printsByCn;

	ScryfallSet(emi.lib.mtg.scryfall.api.Set setJson) {
		this.setJson = setJson;
		this.prints = new MirrorMap<>(HashMap::new);
		this.printsByCn = new HashMap<>();
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
	public Type type() {
		return setJson.setType.libmtgType;
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
	public java.util.Set<? extends Card.Print> prints() {
		return prints.valueSet();
	}

	@Override
	public Card.Print print(UUID id) {
		return prints.get(id);
	}

	@Override
	public Card.Print print(String collectorNumber) {
		return printsByCn.get(collectorNumber);
	}

	@Override
	public String toString() {
		return name() + " (" + code().toUpperCase() + ")";
	}
}
