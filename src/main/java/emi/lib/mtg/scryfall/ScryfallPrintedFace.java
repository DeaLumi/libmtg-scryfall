package emi.lib.mtg.scryfall;

import emi.lib.mtg.Card;

import static emi.lib.mtg.scryfall.Util.or;

class ScryfallPrintedFace implements Card.Printing.Face {

	private final ScryfallPrinting printing;
	private final ScryfallFace face;

	final emi.lib.mtg.scryfall.api.Card cardJson;
	final emi.lib.mtg.scryfall.api.Card.Face faceJson;

	final Card.Face.Kind kind;

	ScryfallPrintedFace(Card.Face.Kind kind, ScryfallPrinting printing, ScryfallFace face, emi.lib.mtg.scryfall.api.Card cardJson, emi.lib.mtg.scryfall.api.Card.Face faceJson) {
		this.kind = kind;
		this.printing = printing;
		this.face = face;
		this.cardJson = cardJson;
		this.faceJson = faceJson;
	}

	ScryfallPrintedFace(ScryfallPrinting printing, ScryfallFace face, emi.lib.mtg.scryfall.api.Card cardJson, emi.lib.mtg.scryfall.api.Card.Face faceJson) {
		this(face.kind(), printing, face, cardJson, faceJson);
	}

	@Override
	public Card.Printing printing() {
		return printing;
	}

	@Override
	public ScryfallFace face() {
		return face;
	}

	@Override
	public String flavor() {
		return or(faceJson != null ? faceJson.flavorText : cardJson.flavorText, "");
	}

	@Override
	public Card.Face.Kind kind() {
		return kind;
	}
}
