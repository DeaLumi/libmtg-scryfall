package emi.lib.mtg.scryfall;

import com.google.common.collect.EnumHashBiMap;
import emi.lib.mtg.Card;
import emi.lib.mtg.characteristic.CardRarity;

import java.util.Set;
import java.util.UUID;

class ScryfallPrinting implements Card.Printing {

	private final ScryfallSet set;
	private final ScryfallCard card;
	private final emi.lib.scryfall.api.Card cardJson;

	final EnumHashBiMap<Card.Face.Kind, ScryfallPrintedFace> faces;

	ScryfallPrinting(ScryfallSet set, ScryfallCard card, emi.lib.scryfall.api.Card cardJson) {
		this.set = set;
		this.card = card;
		this.cardJson = cardJson;

		this.faces = EnumHashBiMap.create(Card.Face.Kind.class);
	}

	@Override
	public Card card() {
		return card;
	}

	@Override
	public Set<? extends Face> faces() {
		return faces.values();
	}

	@Override
	public Face face(Card.Face.Kind kind) {
		return faces.get(kind);
	}

	@Override
	public emi.lib.mtg.Set set() {
		return set;
	}

	@Override
	public CardRarity rarity() {
		return Util.mapRarity(cardJson);
	}

	@Override
	public Integer multiverseId() {
		return cardJson.multiverseId;
	}

	@Override
	public int variation() {
		return 0; // TODO: WORK THIS OUT! Necessary for XLHQ image source!
	}

	@Override
	public String collectorNumber() {
		return cardJson.collectorNumber;
	}

	@Override
	public Integer mtgoCatalogId() {
		return cardJson.mtgoId;
	}

	@Override
	public UUID id() {
		return cardJson.id;
	}
}
