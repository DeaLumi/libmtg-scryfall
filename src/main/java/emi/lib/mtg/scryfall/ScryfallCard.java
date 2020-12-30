package emi.lib.mtg.scryfall;

import com.google.common.collect.EnumHashBiMap;
import com.google.common.collect.HashBiMap;
import emi.lib.mtg.Card;
import emi.lib.mtg.characteristic.Color;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.scryfall.api.enums.GameFormat;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

class ScryfallCard implements Card {
	private final String name;

	final EnumHashBiMap<Face.Kind, ScryfallFace> faces;
	final HashBiMap<UUID, ScryfallPrinting> printings;
	final EnumMap<Format, Legality> legalities;
	final Set<Color> colorIdentity;

	ScryfallCard(emi.lib.mtg.scryfall.api.Card jsonCard) {
		this.name = jsonCard.name;
		this.faces = EnumHashBiMap.create(Face.Kind.class);
		this.printings = HashBiMap.create();

		this.colorIdentity = Util.mapColor(Util.orEmpty(jsonCard.colorIdentity));
		this.legalities = new EnumMap<>(Format.class);
		if (jsonCard.legalities != null && !jsonCard.legalities.isEmpty()) {
			for (Map.Entry<GameFormat, emi.lib.mtg.scryfall.api.enums.Legality> entry : jsonCard.legalities.entrySet()) {
				if (entry.getKey().libMtgFormat != null) {
					this.legalities.put(entry.getKey().libMtgFormat, entry.getValue().libMtgLegality);
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

	@Override
	public Set<Color> colorIdentity() {
		return this.colorIdentity;
	}
}
