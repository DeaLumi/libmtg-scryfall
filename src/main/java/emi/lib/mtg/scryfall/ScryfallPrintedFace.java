package emi.lib.mtg.scryfall;

import emi.lib.mtg.Card;
import emi.lib.mtg.enums.StandardFrame;

import java.util.Objects;

import static emi.lib.mtg.scryfall.Util.or;

class ScryfallPrintedFace implements Card.Print.Face {

	private final ScryfallPrint print;
	private final ScryfallFace face;
	private final boolean back;
	private final StandardFrame frame;

	final emi.lib.mtg.scryfall.api.Card cardJson;
	final emi.lib.mtg.scryfall.api.Card.Face faceJson;

	ScryfallPrintedFace(ScryfallPrint print, ScryfallFace face, boolean back, StandardFrame frame, emi.lib.mtg.scryfall.api.Card cardJson, emi.lib.mtg.scryfall.api.Card.Face faceJson) {
		this.back = back;
		this.frame = frame;
		this.print = print;
		this.face = face;
		this.cardJson = cardJson;
		this.faceJson = faceJson;
	}

	@Override
	public Card.Print print() {
		return print;
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
	public boolean onBack() {
		return back;
	}

	@Override
	public Frame frame() {
		return frame;
	}

	@Override
	public int hashCode() {
		return Objects.hash(cardJson.id, back, frame);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof ScryfallPrintedFace)) return false;
		ScryfallPrintedFace other = (ScryfallPrintedFace) obj;

		return cardJson.id.equals(other.cardJson.id) && back == other.back && frame == other.frame;
	}
}
