package emi.lib.mtg.scryfall;

import emi.lib.mtg.Card;
import emi.lib.mtg.enums.Color;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.scryfall.api.enums.GameFormat;
import emi.lib.mtg.scryfall.util.MirrorMap;

import java.util.*;

class ScryfallCard implements Card {
	private final String name;

	final MirrorMap<Face.Kind, ScryfallFace> faces;
	final MirrorMap<UUID, ScryfallPrinting> printings;
	final Map<String, ScryfallPrinting> printingsByCn;
	final EnumMap<Format, Legality> legalities;
	final Color.Combination colorIdentity;

	ScryfallCard(emi.lib.mtg.scryfall.api.Card jsonCard) {
		this.name = jsonCard.name;
		this.faces = new MirrorMap<>(() -> new EnumMap<>(Face.Kind.class));
		this.printings = new MirrorMap<>(HashMap::new);
		this.printingsByCn = new HashMap<>();

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
		return faces.valueSet();
	}

	@Override
	public ScryfallFace face(Face.Kind kind) {
		return faces.get(kind);
	}

	@Override
	public Set<ScryfallPrinting> printings() {
		return printings.valueSet();
	}

	@Override
	public ScryfallPrinting printing(UUID id) {
		return printings.get(id);
	}

	@Override
	public Printing printing(String setCode, String collectorNumber) {
		return printingsByCn.get(Util.cardPrintingKey(setCode, collectorNumber));
	}

	// Scryfall reports flip cards by full name here for some reason.
	// Use the mtglib default behavior for determining card name here.
//	@Override
//	public String name() {
//		return this.name;
//	}

	@Override
	public Legality legality(Format format) {
		return this.legalities.getOrDefault(format, Legality.Unknown);
	}

	@Override
	public Color.Combination colorIdentity() {
		return this.colorIdentity;
	}
}
