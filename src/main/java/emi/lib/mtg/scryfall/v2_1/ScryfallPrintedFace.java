package emi.lib.mtg.scryfall.v2_1;

import emi.lib.mtg.Card;

import static emi.lib.mtg.scryfall.Util.or;

public class ScryfallPrintedFace implements Card.Printing.Face {

	private final ScryfallFace face;
	private final emi.lib.scryfall.api.Card cardJson;

	ScryfallPrintedFace(ScryfallFace face, emi.lib.scryfall.api.Card cardJson) {
		this.face = face;
		this.cardJson = cardJson;
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
