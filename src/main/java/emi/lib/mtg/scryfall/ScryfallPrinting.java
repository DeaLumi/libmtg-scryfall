package emi.lib.mtg.scryfall;

import emi.lib.mtg.Card;
import emi.lib.mtg.characteristic.CardRarity;
import emi.lib.mtg.scryfall.util.MirrorMap;

import java.util.*;

class ScryfallPrinting implements Card.Printing {

	private final static Comparator<String> COLLECTOR_NUMBER_COMPARATOR = (s1, s2) -> {
		int n1 = 0;
		int n2 = 0;

		for (int i = 0; i < Math.min(s1.length(), s2.length()); ++i) {
			int c1 = s1.charAt(i);
			int c2 = s2.charAt(i);

			boolean c1d = c1 >= '0' && c1 <= '9';
			boolean c2d = c2 >= '0' && c2 <= '9';

			if (c1d) {
				if (c2d) {
					n1 *= 10;
					n1 += c1 - '0';

					n2 *= 10;
					n2 += c2 - '0';
				} else {
					return 1;
				}
			} else {
				if (c2d) {
					return -1;
				} else {
					if (n1 != n2) {
						return n1 - n2;
					}

					if (c1 != c2) {
						return c1 - c2;
					}
				}
			}
		}

		return 0;
	};

	private final ScryfallSet set;
	private final ScryfallCard card;
	final emi.lib.mtg.scryfall.api.Card cardJson;

	final MirrorMap<Card.Face.Kind, ScryfallPrintedFace> faces;

	private int variation;

	ScryfallPrinting(ScryfallSet set, ScryfallCard card, emi.lib.mtg.scryfall.api.Card cardJson) {
		this.set = set;
		this.card = card;
		this.cardJson = cardJson;

		this.faces = new MirrorMap<>(() -> new EnumMap<>(Card.Face.Kind.class));

		this.variation = -1;
	}

	@Override
	public ScryfallCard card() {
		return card;
	}

	@Override
	public Set<ScryfallPrintedFace> faces() {
		return faces.valueSet();
	}

	@Override
	public ScryfallPrintedFace face(Card.Face.Kind kind) {
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
		return cardJson.multiverseIds == null || cardJson.multiverseIds.isEmpty() ? null : cardJson.multiverseIds.iterator().next();
	}

	@Override
	public int variation() {
		if (variation < 0) {
			Iterator<String> cns = set.printings().stream()
					.filter(print -> print.card().name().equals(this.card().name()))
					.map(Card.Printing::collectorNumber)
					.sorted(COLLECTOR_NUMBER_COMPARATOR)
					.iterator();

			for (int i = 1; cns.hasNext(); ++i) {
				if (Objects.equals(cns.next(), collectorNumber())) {
					variation = i;
					break;
				}
			}

			if (variation < 0) {
				variation = 1;
			}
		}

		return variation;
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
	public boolean promo() {
		return cardJson.promo;
	}

	@Override
	public UUID id() {
		return cardJson.id;
	}
}
