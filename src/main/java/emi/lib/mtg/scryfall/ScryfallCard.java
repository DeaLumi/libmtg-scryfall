package emi.lib.mtg.scryfall;

import emi.lib.mtg.Card;
import emi.lib.mtg.enums.Color;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.scryfall.api.enums.GameFormat;

import java.util.*;

class ScryfallCard implements Card {
	public enum FaceType {
		Main,
		Alternate,
		Transformed,
		Flipped;
	}

	// TODO: Branch this out into at *least* a child class for simple, single-faced cards.

	private final UUID oracleId;
	private Set<ScryfallFace> faces, mainFaces, transformedFaces;
	private ScryfallFace flippedFace;
	private Set<ScryfallPrinting> printings;
	private final HashMap<UUID, ScryfallPrinting> printingsById;
	private final HashMap<String, ScryfallPrinting> printingsByCn;
	private final EnumMap<Format, Legality> legalities;
	private final Color.Combination colorIdentity;

	ScryfallCard(emi.lib.mtg.scryfall.api.Card jsonCard) {
		this.oracleId = jsonCard.oracleId;
		this.faces = Collections.emptySet();
		this.mainFaces = Collections.emptySet();
		this.transformedFaces = Collections.emptySet();
		this.flippedFace = null;
		this.printings = Collections.emptySet();
		this.printingsById = new HashMap<>();
		this.printingsByCn = new HashMap<>();

		this.colorIdentity = Util.mapColor(Util.orEmpty(jsonCard.colorIdentity));
		this.legalities = new EnumMap<>(Format.class);
		if (jsonCard.legalities != null && !jsonCard.legalities.isEmpty()) {
			for (Map.Entry<String, emi.lib.mtg.scryfall.api.enums.Legality> entry : jsonCard.legalities.entrySet()) {
				GameFormat scryfallFormat = GameFormat.byName(entry.getKey());
				if (scryfallFormat != null && scryfallFormat.libMtgFormat != null) {
					this.legalities.put(scryfallFormat.libMtgFormat, entry.getValue().libMtgLegality);
				} else if (scryfallFormat == null) {
					System.err.println("Warning: Scryfall is reporting legalities for an unrecognized game format \"" + entry.getKey() + "\" -- someone needs to update libmtg-scryfall!");
					// The alternative to this case -- this library knows the format but libmtg doesn't -- is a concern as well, but not enough of one to print a log message for every card.
				}
			}
		}
	}

	ScryfallFace addFace(emi.lib.mtg.scryfall.api.Card cardJson, emi.lib.mtg.scryfall.api.Card.Face faceJson, FaceType type) {
		// TODO: We could somehow check to see if we have this face already, although that shouldn't ever happen.
		ScryfallFace face = new ScryfallFace(cardJson, faceJson);

		faces = Util.addElem(faces, face, LinkedHashSet::new);

		// TODO: mainFaces should stay consistent with faces; we should manually resort it.
		switch (type) {
			case Main: {
				mainFaces = Util.addElem(mainFaces, face, LinkedHashSet::new);
				break;
			}

			case Alternate:
				break; // Nothing special here.

			case Transformed: {
				transformedFaces = Util.addElem(transformedFaces, face, LinkedHashSet::new);
				break;
			}

			case Flipped: {
				if (flippedFace != null) throw new IllegalStateException(String.format("%s already contains a flipped face! Can't add %s!", fullName(), face.name()));
				flippedFace = face;
				break;
			}
		}

		return face;
	}

	ScryfallFace addFace(emi.lib.mtg.scryfall.api.Card cardJson, FaceType type) {
		return addFace(cardJson, null, type);
	}

	ScryfallPrinting addPrinting(ScryfallSet set, emi.lib.mtg.scryfall.api.Card jsonCard) {
		if (!oracleId.equals(jsonCard.oracleId)) throw new IllegalArgumentException(String.format("Attempt to add %s to %s when oracle IDs differ.", jsonCard.name, this.fullName()));
		if (printingsById.containsKey(jsonCard.id)) return printingsById.get(jsonCard.id);

		ScryfallPrinting printing = new ScryfallPrinting(set, this, jsonCard);

		if (printings.isEmpty()) {
			printings = Collections.singleton(printing);
		} else {
			if (printings.size() == 1) printings = new LinkedHashSet<>(printings);
			printings.add(printing);
		}

		printingsById.put(printing.id(), printing);
		printingsByCn.put(Util.cardPrintingKey(printing.set().code(), printing.collectorNumber()), printing);

		return printing;
	}

	@Override
	public Set<ScryfallFace> faces() {
		return faces;
	};

	@Override
	public Set<ScryfallFace> mainFaces() {
		return mainFaces;
	}

	@Override
	public Set<? extends Face> transformedFaces() {
		return transformedFaces;
	}

	@Override
	public Set<ScryfallPrinting> printings() {
		return printings;
	}

	@Override
	public ScryfallPrinting printing(UUID id) {
		return printingsById.get(id);
	}

	@Override
	public Printing printing(String setCode, String collectorNumber) {
		return printingsByCn.get(Util.cardPrintingKey(setCode, collectorNumber));
	}

	@Override
	public Face flipped() {
		return flippedFace;
	}

	@Override
	public Legality legality(Format format) {
		return this.legalities.getOrDefault(format, Legality.Unknown);
	}

	@Override
	public Color.Combination colorIdentity() {
		return this.colorIdentity;
	}

	@Override
	public String toString() {
		return name();
	}
}
