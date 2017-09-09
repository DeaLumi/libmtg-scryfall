package emi.lib.mtg.scryfall;

import com.google.common.collect.BiMap;
import emi.lib.mtg.Card;
import emi.lib.scryfall.api.Set;

import java.util.Map;
import java.util.UUID;

class ScryfallCardFactory {
	static void create(BiMap<String, Set> jsonSets,
					   BiMap<UUID, emi.lib.scryfall.api.Card> jsonCards,
					   emi.lib.scryfall.api.Card jsonCard,
					   BiMap<String, ScryfallSet> sets,
					   BiMap<String, ScryfallCard> cards,
					   BiMap<UUID, ScryfallPrinting> printings) {
		switch (jsonCard.layout) {
			case Normal: {
				// TODO: Oh my god this is all so gross.
				if (jsonCard.cardFaces != null && !jsonCard.cardFaces.isEmpty()) {
					// Right now, the only card that trips this behavior is Curse of the Fire Penguin.
					if ("Curse of the Fire Penguin // ???".equals(jsonCard.name)) {
						createFlip(jsonSets, jsonCards, jsonCard, sets, cards, printings);
						return;
					} else {
						System.err.println("Add support for fancy split/flip card " + jsonCard.name);
					}
				} else if (jsonCard.allParts != null && jsonCard.allParts.size() > 1) {
					if ("Who".equals(jsonCard.name) || "What".equals(jsonCard.name) || "When".equals(jsonCard.name) || "Where".equals(jsonCard.name) || "Why".equals(jsonCard.name)) {
						// oh god
						System.err.println("I'm not supporting Who // What // When // Where // Why. I simply won't.");
						createSimple(jsonSets, jsonCards, jsonCard, sets, cards, printings);
						return;
					} else if ("B.F.M. (Big Furry Monster)".equals(jsonCard.name)) {
						// eh
						System.err.println("I can't tell halves of BFM apart. You're on your own with this one, sorry...");
						createSimple(jsonSets, jsonCards, jsonCard, sets, cards, printings);
						return;
					} else if (jsonCard.allParts.size() == 2) {
						if (jsonCard.allParts.stream().filter(p -> !jsonCard.name.equals(p.name)).findAny().get().uri.getPath().matches("/cards/t[a-z0-9]{3}/[0-9]+")) {
							// This is just a Card/Token pair. Tokens aren't cards, so ignore them.
							createSimple(jsonSets, jsonCards, jsonCard, sets, cards, printings);
							return;
						} else {
							System.err.println("Add support for fancy two-part card " + jsonCard.name);
							createSimple(jsonSets, jsonCards, jsonCard, sets, cards, printings);
							return;
						}
					} else {
						System.err.println("Add support for fancy n-part card " + jsonCard.name);
						createSimple(jsonSets, jsonCards, jsonCard, sets, cards, printings);
						return;
					}
				} else {
					createSimple(jsonSets, jsonCards, jsonCard, sets, cards, printings);
					return;
				}
				break;
			}

			case Split: {
				createSplit(jsonSets, jsonCards, jsonCard, sets, cards, printings);
				return;
			}

			case Flip: {
				createFlip(jsonSets, jsonCards, jsonCard, sets, cards, printings);
				return;
			}

			case Transform: {
				createTransform(jsonSets, jsonCards, jsonCard, sets, cards, printings);
				return;
			}

			case Meld: {
				createMeld(jsonSets, jsonCards, jsonCard, sets, cards, printings);
				return;
			}

			case Leveler:
			case Plane:
			case Phenomenon:
			case Scheme:
			case Vanguard: {
				createSimple(jsonSets, jsonCards, jsonCard, sets, cards, printings);
				return;
			}

			case Token:
			case Emblem: {
				System.err.println("Tokens are not cards! Please exclude the set " + jsonCard.setName + " / " + jsonCard.set + " if possible.");
				break;
			}

			default: {
				throw new Error("Unexpected card layout " + jsonCard.layout);
			}
		}

		System.err.println("Unhandled card printing: " + jsonCard.name + " / " + jsonCard.setName);
		jsonCards.values().remove(jsonCard);
	}

	private static void createSimple(BiMap<String, Set> jsonSets,
									 BiMap<UUID, emi.lib.scryfall.api.Card> jsonCards,
									 emi.lib.scryfall.api.Card jsonCard,
									 BiMap<String, ScryfallSet> sets,
									 BiMap<String, ScryfallCard> cards,
									 BiMap<UUID, ScryfallPrinting> printings) {
		jsonCards.values().remove(jsonCard);

		if (jsonCard.typeLine.startsWith("Token")) {
			System.err.println("Attempting to createSimple " + jsonCard.typeLine + " / " + jsonCard.name + " (" + jsonCard.setName + ")");
			return;
		} else if (jsonCard.typeLine.startsWith("Card")) {
			System.err.println("Attempting to createSimple " + jsonCard.typeLine + " / " + jsonCard.name + " (" + jsonCard.setName + ")");
			return;
		}

		ScryfallCard card = cards.computeIfAbsent(jsonCard.name, ScryfallCard::new);
		ScryfallFace front = card.faces.computeIfAbsent(Card.Face.Kind.Front, f -> new ScryfallFace(jsonCard));
		ScryfallSet set = sets.computeIfAbsent(jsonCard.set, s -> new ScryfallSet(jsonSets.get(s)));
		ScryfallPrinting print = card.printings.computeIfAbsent(jsonCard.id, f -> new ScryfallPrinting(set, card, jsonCard));
		ScryfallPrintedFace frontPrint = print.faces.computeIfAbsent(Card.Face.Kind.Front, f -> new ScryfallPrintedFace(print, front, jsonCard));

		set.printings.put(print.id(), print);
		printings.put(print.id(), print);
	}

	private static void createSplit(BiMap<String, Set> jsonSets,
									BiMap<UUID, emi.lib.scryfall.api.Card> jsonCards,
									emi.lib.scryfall.api.Card jsonCard,
									BiMap<String, ScryfallSet> sets,
									BiMap<String, ScryfallCard> cards,
									BiMap<UUID, ScryfallPrinting> printings) {
		ScryfallCard card = cards.computeIfAbsent(jsonCard.name, ScryfallCard::new);

		jsonCards.values().remove(jsonCard);

		ScryfallSet set = sets.computeIfAbsent(jsonCard.set, setCode -> new ScryfallSet(jsonSets.get(setCode)));

		ScryfallFace left = card.faces.computeIfAbsent(Card.Face.Kind.Left, f -> new ScryfallFace(f, jsonCard, jsonCard.cardFaces.get(0)));
		ScryfallFace right = card.faces.computeIfAbsent(Card.Face.Kind.Right, f -> new ScryfallFace(f, jsonCard, jsonCard.cardFaces.get(1)));

		ScryfallPrinting print = card.printings.computeIfAbsent(jsonCard.id, id -> new ScryfallPrinting(set, card, jsonCard));

		ScryfallPrintedFace leftPrint = print.faces.computeIfAbsent(Card.Face.Kind.Left, f -> new ScryfallPrintedFace(print, left, jsonCard));
		ScryfallPrintedFace rightPrint = print.faces.computeIfAbsent(Card.Face.Kind.Right, f -> new ScryfallPrintedFace(print, right, jsonCard));

		set.printings.put(print.id(), print);
		printings.put(print.id(), print);
	}

	private static void createFlip(BiMap<String, Set> jsonSets,
								   BiMap<UUID, emi.lib.scryfall.api.Card> jsonCards,
								   emi.lib.scryfall.api.Card jsonCard,
								   BiMap<String, ScryfallSet> sets,
								   BiMap<String, ScryfallCard> cards,
								   BiMap<UUID, ScryfallPrinting> printings) {
		ScryfallCard card = cards.computeIfAbsent(jsonCard.name, ScryfallCard::new);

		jsonCards.values().remove(jsonCard);

		ScryfallSet set = sets.computeIfAbsent(jsonCard.set, setCode -> new ScryfallSet(jsonSets.get(setCode)));

		ScryfallFace front = card.faces.computeIfAbsent(Card.Face.Kind.Front, f -> new ScryfallFace(f, jsonCard, jsonCard.cardFaces.get(0)));
		ScryfallFace flip = card.faces.computeIfAbsent(Card.Face.Kind.Flipped, f -> new ScryfallFace(f, jsonCard, jsonCard.cardFaces.get(1)));

		ScryfallPrinting print = card.printings.computeIfAbsent(jsonCard.id, id -> new ScryfallPrinting(set, card, jsonCard));

		ScryfallPrintedFace frontPrint = print.faces.computeIfAbsent(Card.Face.Kind.Front, f -> new ScryfallPrintedFace(print, front, jsonCard));
		ScryfallPrintedFace flipPrint = print.faces.computeIfAbsent(Card.Face.Kind.Flipped, f -> new ScryfallPrintedFace(print, flip, jsonCard));

		set.printings.put(print.id(), print);
		printings.put(print.id(), print);
	}

	private static void createTransform(Map<String, Set> jsonSets,
										Map<UUID, emi.lib.scryfall.api.Card> jsonCards,
										emi.lib.scryfall.api.Card jsonCard,
										Map<String, ScryfallSet> sets,
										Map<String, ScryfallCard> cards,
										Map<UUID, ScryfallPrinting> printings) {
		ScryfallCard card = cards.computeIfAbsent(jsonCard.name, ScryfallCard::new);

		jsonCards.values().remove(jsonCard);

		ScryfallSet set = sets.computeIfAbsent(jsonCard.set, setCode -> new ScryfallSet(jsonSets.get(setCode)));

		ScryfallFace front = card.faces.computeIfAbsent(Card.Face.Kind.Front, f -> new ScryfallFace(f, jsonCard, jsonCard.cardFaces.get(0)));
		ScryfallFace back = card.faces.computeIfAbsent(Card.Face.Kind.Transformed, f -> new ScryfallFace(f, jsonCard, jsonCard.cardFaces.get(1)));

		ScryfallPrinting print = card.printings.computeIfAbsent(jsonCard.id, id -> new ScryfallPrinting(set, card, jsonCard));

		ScryfallPrintedFace frontPrint = print.faces.computeIfAbsent(Card.Face.Kind.Front, f -> new ScryfallPrintedFace(print, front, jsonCard));
		ScryfallPrintedFace backPrint = print.faces.computeIfAbsent(Card.Face.Kind.Transformed, f -> new ScryfallPrintedFace(print, back, jsonCard));

		set.printings.put(print.id(), print);
		printings.put(print.id(), print);
	}

	private static void createMeld(Map<String, Set> jsonSets,
								   Map<UUID, emi.lib.scryfall.api.Card> jsonCards,
								   emi.lib.scryfall.api.Card jsonCard,
								   Map<String, ScryfallSet> sets,
								   Map<String, ScryfallCard> cards,
								   Map<UUID, ScryfallPrinting> printings) {

		emi.lib.scryfall.api.Card backJson = jsonCard.allParts.stream()
				.map(part -> jsonCards.get(part.id))
				.filter(card -> card.collectorNumber.matches("^[0-9]+b$"))
				.findAny()
				.orElseThrow(AssertionError::new);

		emi.lib.scryfall.api.Card frontJson1 = jsonCard.allParts.stream()
				.map(part -> jsonCards.get(part.id))
				.filter(card -> card != backJson)
				.findAny()
				.orElseThrow(AssertionError::new);

		emi.lib.scryfall.api.Card frontJson2 = jsonCard.allParts.stream()
				.map(part -> jsonCards.get(part.id))
				.filter(card -> card != backJson && card != frontJson1)
				.findAny()
				.orElseThrow(AssertionError::new);

		jsonCards.values().remove(backJson);
		jsonCards.values().remove(frontJson1);
		jsonCards.values().remove(frontJson2);

		ScryfallCard card1 = cards.computeIfAbsent(frontJson1.name, ScryfallCard::new);
		ScryfallFace front1 = card1.faces.computeIfAbsent(Card.Face.Kind.Front, f -> new ScryfallFace(frontJson1));
		ScryfallFace back = card1.faces.computeIfAbsent(Card.Face.Kind.Transformed, f -> new ScryfallFace(Card.Face.Kind.Transformed, backJson));

		ScryfallCard card2 = cards.computeIfAbsent(frontJson2.name, ScryfallCard::new);
		ScryfallFace front2 = card2.faces.computeIfAbsent(Card.Face.Kind.Front, f -> new ScryfallFace(frontJson2));
		ScryfallFace back2 = card2.faces.computeIfAbsent(Card.Face.Kind.Transformed, f -> back);

		assert back == back2;

		ScryfallSet set = sets.computeIfAbsent(frontJson1.set, s -> new ScryfallSet(jsonSets.get(s)));

		assert sets.get(frontJson2.set) == set;
		assert sets.get(backJson.set) == set;

		ScryfallPrinting print1 = card1.printings.computeIfAbsent(frontJson1.id, id -> new ScryfallPrinting(set, card1, frontJson1));
		ScryfallPrintedFace frontPrint1 = print1.faces.computeIfAbsent(Card.Face.Kind.Front, f -> new ScryfallPrintedFace(print1, front1, frontJson1));
		ScryfallPrintedFace backPrint = print1.faces.computeIfAbsent(Card.Face.Kind.Transformed, f -> new ScryfallPrintedFace(print1, back, backJson));

		ScryfallPrinting print2 = card2.printings.computeIfAbsent(frontJson2.id, id -> new ScryfallPrinting(set, card2, frontJson2));
		ScryfallPrintedFace frontPrint2 = print2.faces.computeIfAbsent(Card.Face.Kind.Front, f -> new ScryfallPrintedFace(print2, front2, frontJson2));
		ScryfallPrintedFace backPrint2 = print2.faces.computeIfAbsent(Card.Face.Kind.Transformed, f -> new ScryfallPrintedFace(print2, back, backJson));

		set.printings.put(print1.id(), print1);
		set.printings.put(print2.id(), print2);

		printings.put(print1.id(), print1);
		printings.put(print2.id(), print2);
	}
}
