package emi.lib.mtg.scryfall.v2_1;

import emi.lib.mtg.Card;

import static emi.lib.mtg.scryfall.Util.or;

class ScryfallPrintedFace implements Card.Printing.Face {

	private final ScryfallPrinting printing;
	private final ScryfallFace face;
	private final emi.lib.scryfall.api.Card cardJson;

	ScryfallPrintedFace(ScryfallPrinting printing, ScryfallFace face, emi.lib.scryfall.api.Card cardJson) {
		this.printing = printing;
		this.face = face;
		this.cardJson = cardJson;
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
		return or(cardJson.flavorText, "");
	}
}
