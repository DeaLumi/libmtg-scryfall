package emi.lib.mtg.scryfall.v2_1;

import emi.lib.mtg.Card;
import emi.lib.mtg.characteristic.CardRarity;

import java.util.Set;
import java.util.UUID;

public class ScryfallPrinting implements Card.Printing {
	@Override
	public Card card() {
		return null;
	}

	@Override
	public Set<? extends Card.Face.Printing> faces() {
		return null;
	}

	@Override
	public Card.Face.Printing face(Card.Face.Kind kind) {
		return null;
	}

	@Override
	public emi.lib.mtg.Set set() {
		return null;
	}

	@Override
	public CardRarity rarity() {
		return null;
	}

	@Override
	public Integer multiverseId() {
		return null;
	}

	@Override
	public int variation() {
		return 0;
	}

	@Override
	public String collectorNumber() {
		return null;
	}

	@Override
	public Integer mtgoCatalogId() {
		return null;
	}

	@Override
	public UUID id() {
		return null;
	}
}
