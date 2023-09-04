package emi.lib.mtg.scryfall;

import emi.lib.mtg.Card;
import emi.lib.mtg.enums.Color;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.scryfall.api.enums.GameFormat;

import java.util.*;

class ScryfallCard implements Card {
	// TODO: Branch this out into at *least* a child class for simple, single-faced cards.

	private final UUID oracleId;
	private Map<ScryfallFace, ScryfallFace> faces, mainFaces;
	private Map<ScryfallFace, Set<ScryfallFace>> transformedFaces;
	private Map<ScryfallFace, ScryfallFace> flippedFaces;
	private Set<ScryfallPrinting> printings;
	private final HashMap<UUID, ScryfallPrinting> printingsById;
	private final HashMap<String, ScryfallPrinting> printingsByCn;
	private final EnumMap<Format, Legality> legalities;
	private final Color.Combination colorIdentity;

	ScryfallCard(emi.lib.mtg.scryfall.api.Card jsonCard) {
		this.oracleId = jsonCard.oracleId();
		this.faces = Collections.emptyMap();
		this.mainFaces = Collections.emptyMap();
		this.transformedFaces = Collections.emptyMap();
		this.flippedFaces = Collections.emptyMap();
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

	ScryfallFace addFace(emi.lib.mtg.scryfall.api.Card cardJson, emi.lib.mtg.scryfall.api.Card.Face faceJson, boolean main) {
		ScryfallFace face = new ScryfallFace(cardJson, faceJson);
		if (faces.containsKey(face)) return faces.get(face);
		faces = Util.addElem(faces, face, LinkedHashMap::new);

		// TODO: mainFaces should stay consistent with faces; we should manually resort it.
		if (main) mainFaces = Util.addElem(mainFaces, face, LinkedHashMap::new);

		return face;
	}

	ScryfallFace addFace(emi.lib.mtg.scryfall.api.Card cardJson, boolean main) {
		return addFace(cardJson, null, main);
	}

	ScryfallFace addTransformedFace(ScryfallFace source, emi.lib.mtg.scryfall.api.Card cardJson, emi.lib.mtg.scryfall.api.Card.Face faceJson) {
		ScryfallFace face = new ScryfallFace(cardJson, faceJson);
		if (faces.containsKey(face)) return faces.get(face);
		faces = Util.addElem(faces, face, LinkedHashMap::new);

		if (!transformedFaces.containsKey(source)) transformedFaces = Util.addElem(transformedFaces, source, Collections.emptySet(), LinkedHashMap::new);
		Util.addElem(transformedFaces, source, Util.addElem(transformedFaces.get(source), face, LinkedHashSet::new), LinkedHashMap::new);

		return face;
	}

	ScryfallFace addFlippedFace(ScryfallFace source, emi.lib.mtg.scryfall.api.Card cardJson, emi.lib.mtg.scryfall.api.Card.Face faceJson) {
		ScryfallFace face = new ScryfallFace(cardJson, faceJson);
		if (faces.containsKey(face)) return faces.get(face);
		faces = Util.addElem(faces, face, LinkedHashMap::new);
		flippedFaces = Util.addElem(flippedFaces, source, face, LinkedHashMap::new);

		return face;
	}

	ScryfallPrinting addPrinting(ScryfallSet set, emi.lib.mtg.scryfall.api.Card jsonCard) {
		if (!oracleId.equals(jsonCard.oracleId())) throw new IllegalArgumentException(String.format("Attempt to add %s to %s when oracle IDs differ.", jsonCard.name, this.fullName()));
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
		return faces.keySet();
	};

	@Override
	public Set<ScryfallFace> mainFaces() {
		return mainFaces.keySet();
	}

	@Override
	public Set<? extends Face> transformed(Face source) {
		if (!(source instanceof ScryfallFace)) throw new IllegalArgumentException(String.format("%s is not a face of %s", source.name(), name()));
		return transformedFaces.get(source);
	}

	@Override
	public Face flipped(Face source) {
		if (!(source instanceof ScryfallFace)) throw new IllegalArgumentException(String.format("%s is not a face of %s", source.name(), name()));
		return flippedFaces.get(source);
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
