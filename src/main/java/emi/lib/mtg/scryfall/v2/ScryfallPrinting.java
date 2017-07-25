package emi.lib.mtg.scryfall.v2;

import emi.lib.mtg.characteristic.CardRarity;
import emi.lib.mtg.v2.Card;
import emi.lib.mtg.v2.Set;

import java.util.UUID;

public class ScryfallPrinting implements Card.Printing {

	private ScryfallCard card;
	private ScryfallSet set;

	emi.lib.scryfall.api.Card cardJson;

	public ScryfallPrinting(ScryfallCard card, ScryfallSet set, emi.lib.scryfall.api.Card cardJson) {
		this.card = card;
		this.set = set;
		this.cardJson = cardJson;

		this.card.printings.put(cardJson.id, this);
		this.set.printings.put(cardJson.id, this);
	}

	@Override
	public Card card() {
		return this.card;
	}

	@Override
	public Set set() {
		return this.set;
	}

	@Override
	public CardRarity rarity() {
		return Util.mapRarity(this.cardJson);
	}

	@Override
	public Integer multiverseId() {
		return this.cardJson.multiverseId;
	}

	@Override
	public int variation() {
		return 0; // TODO: Figure this out!
	}

	@Override
	public String collectorNumber() {
		return this.cardJson.collectorNumber;
	}

	@Override
	public Integer mtgoCatalogId() {
		return this.cardJson.mtgoId;
	}

	@Override
	public UUID id() {
		return this.cardJson.id;
	}
}
