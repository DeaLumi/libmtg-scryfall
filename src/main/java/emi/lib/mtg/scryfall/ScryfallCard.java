package emi.lib.mtg.scryfall;

import com.google.common.collect.EnumHashBiMap;
import com.google.common.collect.HashBiMap;
import emi.lib.mtg.Card;

import java.util.Set;
import java.util.UUID;

class ScryfallCard implements Card {

	private final String name;

	final EnumHashBiMap<Face.Kind, ScryfallFace> faces;
	final HashBiMap<UUID, ScryfallPrinting> printings;

	ScryfallCard(String name) {
		this.name = name;
		this.faces = EnumHashBiMap.create(Face.Kind.class);
		this.printings = HashBiMap.create();
	}

	@Override
	public Set<ScryfallFace> faces() {
		return faces.values();
	}

	@Override
	public ScryfallFace face(Face.Kind kind) {
		return faces.get(kind);
	}

	@Override
	public Set<ScryfallPrinting> printings() {
		return printings.values();
	}

	@Override
	public ScryfallPrinting printing(UUID id) {
		return printings.get(id);
	}

	@Override
	public String name() {
		return this.name;
	}
}
