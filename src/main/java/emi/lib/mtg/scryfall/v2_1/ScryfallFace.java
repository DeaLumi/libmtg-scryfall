package emi.lib.mtg.scryfall.v2_1;

import emi.lib.mtg.Card;
import emi.lib.mtg.characteristic.CardTypeLine;
import emi.lib.mtg.characteristic.Color;
import emi.lib.mtg.characteristic.ManaCost;

import java.util.Set;

public class ScryfallFace implements Card.Face {
	@Override
	public Kind kind() {
		return null;
	}

	@Override
	public Card card() {
		return null;
	}

	@Override
	public Set<? extends Printing> printings() {
		return null;
	}

	@Override
	public Printing printing(Card.Printing cardPrinting) {
		return null;
	}

	@Override
	public String name() {
		return null;
	}

	@Override
	public ManaCost manaCost() {
		return null;
	}

	@Override
	public Set<Color> colorIndicator() {
		return null;
	}

	@Override
	public CardTypeLine type() {
		return null;
	}

	@Override
	public String rules() {
		return null;
	}

	@Override
	public String power() {
		return null;
	}

	@Override
	public String toughness() {
		return null;
	}

	@Override
	public String loyalty() {
		return null;
	}

	@Override
	public String handModifier() {
		return null;
	}

	@Override
	public String lifeModifier() {
		return null;
	}
}
