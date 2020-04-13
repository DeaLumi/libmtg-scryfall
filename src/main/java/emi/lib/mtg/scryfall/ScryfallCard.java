package emi.lib.mtg.scryfall;

import com.google.common.collect.EnumHashBiMap;
import com.google.common.collect.HashBiMap;
import emi.lib.mtg.Card;
import emi.lib.mtg.game.Format;
import emi.lib.scryfall.api.enums.GameFormat;
import emi.lib.scryfall.api.enums.Legality;

import java.util.*;

class ScryfallCard implements Card {
	private final String name;

	final EnumHashBiMap<Face.Kind, ScryfallFace> faces;
	final HashBiMap<UUID, ScryfallPrinting> printings;
	final EnumMap<Format, Legality> legalities;

	ScryfallCard(emi.lib.scryfall.api.Card jsonCard) {
		this.name = jsonCard.name;
		this.faces = EnumHashBiMap.create(Face.Kind.class);
		this.printings = HashBiMap.create();

		this.legalities = new EnumMap<>(Format.class);
		if (jsonCard.legalities != null && !jsonCard.legalities.isEmpty()) {
			for (Map.Entry<GameFormat, emi.lib.scryfall.api.enums.Legality> entry : jsonCard.legalities.entrySet()) {
				try {
					Format libFormat = Format.valueOf(entry.getKey().name());
					Legality libLegality = Legality.valueOf(entry.getValue().name());
					this.legalities.put(libFormat, libLegality);
				} catch (IllegalArgumentException iae) {
					System.err.println("Error mapping card legality: " + iae.getMessage());
				}
			}
		}
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

	@Override
	public Legality legality(Format format) {
		return this.legalities.getOrDefault(format, Legality.Unknown);
	}
}
