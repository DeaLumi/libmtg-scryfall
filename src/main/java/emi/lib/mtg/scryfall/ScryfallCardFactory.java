package emi.lib.mtg.scryfall;

import com.google.common.collect.BiMap;
import emi.lib.mtg.Card;
import emi.lib.mtg.scryfall.api.Set;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

class ScryfallCardFactory {
	static void create(BiMap<String, Set> jsonSets,
					   BiMap<UUID, emi.lib.mtg.scryfall.api.Card> jsonCards,
					   emi.lib.mtg.scryfall.api.Card jsonCard,
					   BiMap<String, ScryfallSet> sets,
					   BiMap<UUID, ScryfallCard> cards,
					   BiMap<UUID, ScryfallPrinting> printings) {
		switch (jsonCard.layout) {
			case Normal: {
				// TODO: Oh my god this is all so gross.
				if (jsonCard.cardFaces != null && !jsonCard.cardFaces.isEmpty()) {
					System.err.println("Add support for fancy split/flip card " + jsonCard.name);
					createSimple(jsonSets, jsonCards, jsonCard, sets, cards, printings);
				} else if (jsonCard.allParts != null && jsonCard.allParts.size() > 1) {
					if ("Who".equals(jsonCard.name) || "What".equals(jsonCard.name) || "When".equals(jsonCard.name) || "Where".equals(jsonCard.name) || "Why".equals(jsonCard.name)) {
						// oh god
						System.err.println("I'm not supporting Who // What // When // Where // Why. I simply won't.");
						createSimple(jsonSets, jsonCards, jsonCard, sets, cards, printings);
						return;
					} else if ("B.F.M. (Big Furry Monster)".equals(jsonCard.name)) {
						// eh
						System.err.println("I can't tell halves of BFM apart. You're on your own with this one, sorry...");

						// Hack...
						if (!jsonCard.typeLine.startsWith("Creature")) {
							jsonCard.typeLine = "Creature \u0097 " + jsonCard.typeLine;
						}

						createSimple(jsonSets, jsonCards, jsonCard, sets, cards, printings);
						return;
					} else if (jsonCard.allParts.size() == 2) {
						emi.lib.mtg.scryfall.api.Card.Part part = jsonCard.allParts.stream().filter(p -> !jsonCard.name.equals(p.name)).findAny().orElse(null);
						if (part == null) {
							System.err.println("Eff you Unstable! (" + jsonCard.name + ")");
							createSimple(jsonSets, jsonCards, jsonCard, sets, cards, printings);
							return;
						} else if (part.uri.getPath().matches("/cards/t[a-z0-9]{3}/[0-9]+")) {
							// This is just a Card/Token pair. Tokens aren't cards, so ignore them.
							// Oh my fucking GOD. FOOD ARE NOT PARTS OF A CARD. CARDS YOU SEARCH FOR ARE NOT PARTS OF A CARD.
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

			case Transform:
			case ModalDFC: {
				createTransform(jsonSets, jsonCards, jsonCard, sets, cards, printings);
				return;
			}

			case Meld: {
				createMeld(jsonSets, jsonCards, jsonCard, sets, cards, printings);
				return;
			}

			case Augment:
			case Host:
			case Leveler:
			case Planar:
			case Scheme:
			case Vanguard:
			case Saga: {
				createSimple(jsonSets, jsonCards, jsonCard, sets, cards, printings);
				return;
			}

			case Adventure: {
				createAdventure(jsonSets, jsonCards, jsonCard, sets, cards, printings);
				return;
			}

			case Token:
			case DoubleFacedToken:
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

	private static UUID calculateCardUUID(BiMap<UUID, emi.lib.mtg.scryfall.api.Card> jsonCards, emi.lib.mtg.scryfall.api.Card jsonCard) {
		StringBuilder sb = new StringBuilder();
		switch (jsonCard.layout) {
			case Normal:
				sb.append(jsonCard.name).append('\n').append(jsonCard.oracleText);
				break;
			case Split:
			case Flip:
			case Transform:
			case Adventure:
			case ModalDFC:
				sb.append(jsonCard.cardFaces.get(0).name).append('\n');
				sb.append(jsonCard.cardFaces.get(0).oracleText).append('\n');
				sb.append("\n//\n\n");
				sb.append(jsonCard.cardFaces.get(1).name).append('\n');
				sb.append(jsonCard.cardFaces.get(1).oracleText);
				break;
			case Meld:
				System.err.println("Warning: Unnecessary MeldParts sorting.");
				return calculateCardUUID(new MeldParts(jsonCards, jsonCard), jsonCard);
			case Augment:
			case Host:
			case Leveler:
			case Planar:
			case Scheme:
			case Vanguard:
			case Saga:
				sb.append(jsonCard.name).append('\n').append(jsonCard.oracleText);
				break;
			case Token:
			case DoubleFacedToken:
			case Emblem:
				throw new Error("Attempt to calculate UUID of a token or emblem!");
			default:
				throw new Error("Can't calculate UUID for unknown card layout " + jsonCard.layout);
		}

		return UUID.nameUUIDFromBytes(sb.toString().getBytes(StandardCharsets.UTF_8));
	}

	private static UUID calculateCardUUID(MeldParts parts, emi.lib.mtg.scryfall.api.Card front) {
		StringBuilder sb = new StringBuilder();
		sb.append(front.name).append('\n').append(front.oracleText).append('\n');
		sb.append("\n//\n\n");
		sb.append(parts.back.name).append('\n').append(parts.back.oracleText);
		return UUID.nameUUIDFromBytes(sb.toString().getBytes(StandardCharsets.UTF_8));
	}

	private static void createSimple(BiMap<String, Set> jsonSets,
									 BiMap<UUID, emi.lib.mtg.scryfall.api.Card> jsonCards,
									 emi.lib.mtg.scryfall.api.Card jsonCard,
									 BiMap<String, ScryfallSet> sets,
									 BiMap<UUID, ScryfallCard> cards,
									 BiMap<UUID, ScryfallPrinting> printings) {
		jsonCards.values().remove(jsonCard);

		if (jsonCard.typeLine.startsWith("Token")) {
			System.err.println("Attempting to createSimple " + jsonCard.typeLine + " / " + jsonCard.name + " (" + jsonCard.setName + ")");
			return;
		} else if (jsonCard.typeLine.startsWith("Card")) {
			System.err.println("Attempting to createSimple " + jsonCard.typeLine + " / " + jsonCard.name + " (" + jsonCard.setName + ")");
			return;
		}

		ScryfallCard card = cards.computeIfAbsent(calculateCardUUID(jsonCards, jsonCard), c -> new ScryfallCard(jsonCard));
		ScryfallFace front = card.faces.computeIfAbsent(Card.Face.Kind.Front, f -> new ScryfallFace(jsonCard));
		ScryfallSet set = sets.computeIfAbsent(jsonCard.set, s -> new ScryfallSet(jsonSets.get(s)));
		ScryfallPrinting print = card.printings.computeIfAbsent(jsonCard.id, f -> new ScryfallPrinting(set, card, jsonCard));
		ScryfallPrintedFace frontPrint = print.faces.computeIfAbsent(Card.Face.Kind.Front, f -> new ScryfallPrintedFace(print, front, jsonCard, null));

		set.printings.put(print.id(), print);
		printings.put(print.id(), print);
	}

	private static void createSplit(BiMap<String, Set> jsonSets,
									BiMap<UUID, emi.lib.mtg.scryfall.api.Card> jsonCards,
									emi.lib.mtg.scryfall.api.Card jsonCard,
									BiMap<String, ScryfallSet> sets,
									BiMap<UUID, ScryfallCard> cards,
									BiMap<UUID, ScryfallPrinting> printings) {
		ScryfallCard card = cards.computeIfAbsent(calculateCardUUID(jsonCards, jsonCard), c -> new ScryfallCard(jsonCard));

		jsonCards.values().remove(jsonCard);

		ScryfallSet set = sets.computeIfAbsent(jsonCard.set, setCode -> new ScryfallSet(jsonSets.get(setCode)));

		ScryfallFace left = card.faces.computeIfAbsent(Card.Face.Kind.Left, f -> new ScryfallFace(f, jsonCard, jsonCard.cardFaces.get(0)));
		ScryfallFace right = card.faces.computeIfAbsent(Card.Face.Kind.Right, f -> new ScryfallFace(f, jsonCard, jsonCard.cardFaces.get(1)));

		ScryfallPrinting print = card.printings.computeIfAbsent(jsonCard.id, id -> new ScryfallPrinting(set, card, jsonCard));

		ScryfallPrintedFace leftPrint = print.faces.computeIfAbsent(Card.Face.Kind.Left, f -> new ScryfallPrintedFace(print, left, jsonCard, jsonCard.cardFaces.get(0)));
		ScryfallPrintedFace rightPrint = print.faces.computeIfAbsent(Card.Face.Kind.Right, f -> new ScryfallPrintedFace(print, right, jsonCard, jsonCard.cardFaces.get(1)));

		set.printings.put(print.id(), print);
		printings.put(print.id(), print);
	}

	private static void createFlip(BiMap<String, Set> jsonSets,
								   BiMap<UUID, emi.lib.mtg.scryfall.api.Card> jsonCards,
								   emi.lib.mtg.scryfall.api.Card jsonCard,
								   BiMap<String, ScryfallSet> sets,
								   BiMap<UUID, ScryfallCard> cards,
								   BiMap<UUID, ScryfallPrinting> printings) {
		ScryfallCard card = cards.computeIfAbsent(calculateCardUUID(jsonCards, jsonCard), c -> new ScryfallCard(jsonCard));

		jsonCards.values().remove(jsonCard);

		ScryfallSet set = sets.computeIfAbsent(jsonCard.set, setCode -> new ScryfallSet(jsonSets.get(setCode)));

		ScryfallFace front = card.faces.computeIfAbsent(Card.Face.Kind.Front, f -> new ScryfallFace(f, jsonCard, jsonCard.cardFaces.get(0)));
		ScryfallFace flip = card.faces.computeIfAbsent(Card.Face.Kind.Flipped, f -> new ScryfallFace(f, jsonCard, jsonCard.cardFaces.get(1)));

		ScryfallPrinting print = card.printings.computeIfAbsent(jsonCard.id, id -> new ScryfallPrinting(set, card, jsonCard));

		ScryfallPrintedFace frontPrint = print.faces.computeIfAbsent(Card.Face.Kind.Front, f -> new ScryfallPrintedFace(print, front, jsonCard, jsonCard.cardFaces.get(0)));
		ScryfallPrintedFace flipPrint = print.faces.computeIfAbsent(Card.Face.Kind.Flipped, f -> new ScryfallPrintedFace(print, flip, jsonCard, jsonCard.cardFaces.get(1)));

		set.printings.put(print.id(), print);
		printings.put(print.id(), print);
	}

	private static void createTransform(Map<String, Set> jsonSets,
										BiMap<UUID, emi.lib.mtg.scryfall.api.Card> jsonCards,
										emi.lib.mtg.scryfall.api.Card jsonCard,
										Map<String, ScryfallSet> sets,
										Map<UUID, ScryfallCard> cards,
										Map<UUID, ScryfallPrinting> printings) {
		ScryfallCard card = cards.computeIfAbsent(calculateCardUUID(jsonCards, jsonCard), c -> new ScryfallCard(jsonCard));

		jsonCards.values().remove(jsonCard);

		ScryfallSet set = sets.computeIfAbsent(jsonCard.set, setCode -> new ScryfallSet(jsonSets.get(setCode)));

		ScryfallFace front = card.faces.computeIfAbsent(Card.Face.Kind.Front, f -> new ScryfallFace(f, jsonCard, jsonCard.cardFaces.get(0)));
		ScryfallFace back = card.faces.computeIfAbsent(Card.Face.Kind.Transformed, f -> new ScryfallFace(f, jsonCard, jsonCard.cardFaces.get(1)));

		ScryfallPrinting print = card.printings.computeIfAbsent(jsonCard.id, id -> new ScryfallPrinting(set, card, jsonCard));

		ScryfallPrintedFace frontPrint = print.faces.computeIfAbsent(Card.Face.Kind.Front, f -> new ScryfallPrintedFace(print, front, jsonCard, jsonCard.cardFaces.get(0)));
		ScryfallPrintedFace backPrint = print.faces.computeIfAbsent(Card.Face.Kind.Transformed, f -> new ScryfallPrintedFace(print, back, jsonCard, jsonCard.cardFaces.get(1)));

		set.printings.put(print.id(), print);
		printings.put(print.id(), print);
	}

	private static class MeldParts {
		public final emi.lib.mtg.scryfall.api.Card back;
		public final emi.lib.mtg.scryfall.api.Card active;
		public final emi.lib.mtg.scryfall.api.Card passive;

		public MeldParts(Map<UUID, emi.lib.mtg.scryfall.api.Card> jsonCards, emi.lib.mtg.scryfall.api.Card jsonCard) {
			emi.lib.mtg.scryfall.api.Card backJson = jsonCard.allParts.stream()
					.map(part -> jsonCards.get(part.id))
					.filter(card -> card != null && card.collectorNumber != null && card.collectorNumber.matches("^[0-9]+bs?$"))
					.findAny()
					.orElseThrow(() -> new AssertionError("Can't find back face for " + jsonCard.printedName + "/" + jsonCard.scryfallUri));

			emi.lib.mtg.scryfall.api.Card frontJson1 = jsonCard.allParts.stream()
					.map(part -> jsonCards.get(part.id))
					.filter(card -> card != backJson)
					.findAny()
					.orElseThrow(AssertionError::new);

			emi.lib.mtg.scryfall.api.Card frontJson2 = jsonCard.allParts.stream()
					.map(part -> jsonCards.get(part.id))
					.filter(card -> card != backJson && card != frontJson1)
					.findAny()
					.orElseThrow(AssertionError::new);

			if (frontJson1.oracleText.contains("then meld them into")) {
				this.active = frontJson1;
				this.passive = frontJson2;
			} else if (frontJson2.oracleText.contains("then meld them into")) {
				this.active = frontJson2;
				this.passive = frontJson1;
			} else {
				throw new Error("Couldn't determine active vs. passive meld pair!");
			}

			this.back = backJson;
		}
	}

	private static void createMeld(Map<String, Set> jsonSets,
								   BiMap<UUID, emi.lib.mtg.scryfall.api.Card> jsonCards,
								   emi.lib.mtg.scryfall.api.Card jsonCard,
								   Map<String, ScryfallSet> sets,
								   Map<UUID, ScryfallCard> cards,
								   Map<UUID, ScryfallPrinting> printings) {
		MeldParts parts = new MeldParts(jsonCards, jsonCard);

		jsonCards.values().remove(parts.back);
		jsonCards.values().remove(parts.active);
		jsonCards.values().remove(parts.passive);

		ScryfallCard card1 = cards.computeIfAbsent(calculateCardUUID(parts, parts.active), c -> new ScryfallCard(parts.active));
		ScryfallFace front1 = card1.faces.computeIfAbsent(Card.Face.Kind.Front, f -> new ScryfallFace(parts.active));
		ScryfallFace back = card1.faces.computeIfAbsent(Card.Face.Kind.Transformed, f -> new ScryfallFace(Card.Face.Kind.Transformed, parts.back));

		ScryfallCard card2 = cards.computeIfAbsent(calculateCardUUID(parts, parts.passive), c -> new ScryfallCard(parts.passive));
		ScryfallFace front2 = card2.faces.computeIfAbsent(Card.Face.Kind.Front, f -> new ScryfallFace(parts.passive));
		ScryfallFace back2 = card2.faces.computeIfAbsent(Card.Face.Kind.Transformed, f -> back);

		assert back == back2;

		ScryfallSet set = sets.computeIfAbsent(parts.active.set, s -> new ScryfallSet(jsonSets.get(s)));

		assert sets.get(parts.passive.set) == set;
		assert sets.get(parts.back.set) == set;

		ScryfallPrinting print1 = card1.printings.computeIfAbsent(parts.active.id, id -> new ScryfallPrinting(set, card1, parts.active));
		ScryfallPrintedFace frontPrint1 = print1.faces.computeIfAbsent(Card.Face.Kind.Front, f -> new ScryfallPrintedFace(print1, front1, parts.active, null));
		ScryfallPrintedFace backPrint = print1.faces.computeIfAbsent(Card.Face.Kind.Transformed, f -> new ScryfallPrintedFace(print1, back, parts.back, null));

		ScryfallPrinting print2 = card2.printings.computeIfAbsent(parts.passive.id, id -> new ScryfallPrinting(set, card2, parts.passive));
		ScryfallPrintedFace frontPrint2 = print2.faces.computeIfAbsent(Card.Face.Kind.Front, f -> new ScryfallPrintedFace(print2, front2, parts.passive, null));
		ScryfallPrintedFace backPrint2 = print2.faces.computeIfAbsent(Card.Face.Kind.Transformed, f -> new ScryfallPrintedFace(print2, back, parts.back, null));

		set.printings.put(print1.id(), print1);
		set.printings.put(print2.id(), print2);

		printings.put(print1.id(), print1);
		printings.put(print2.id(), print2);
	}

	private static void createAdventure(Map<String, Set> jsonSets,
										BiMap<UUID, emi.lib.mtg.scryfall.api.Card> jsonCards,
										emi.lib.mtg.scryfall.api.Card jsonCard,
										Map<String, ScryfallSet> sets,
										Map<UUID, ScryfallCard> cards,
										Map<UUID, ScryfallPrinting> printings) {
		ScryfallCard card = cards.computeIfAbsent(calculateCardUUID(jsonCards, jsonCard), c -> new ScryfallCard(jsonCard));

		jsonCards.values().remove(jsonCard);

		ScryfallSet set = sets.computeIfAbsent(jsonCard.set, setCode -> new ScryfallSet(jsonSets.get(setCode)));

		emi.lib.mtg.scryfall.api.Card.Face jsonFront = jsonCard.cardFaces.stream().filter(f -> f.typeLine.startsWith("Creature")).findAny().orElseThrow(() -> new AssertionError("Couldn't find main part of adventure!"));
		emi.lib.mtg.scryfall.api.Card.Face jsonAdventure = jsonCard.cardFaces.stream().filter(f -> f.typeLine.endsWith("Adventure")).findAny().orElseThrow(() -> new AssertionError("Couldn't find adventure part of adventure!"));

		ScryfallFace front = card.faces.computeIfAbsent(Card.Face.Kind.Front, f -> new ScryfallFace(f, jsonCard, jsonFront));
		ScryfallFace adventure = card.faces.computeIfAbsent(Card.Face.Kind.Other, f -> new ScryfallFace(f, jsonCard, jsonAdventure));

		ScryfallPrinting print = card.printings.computeIfAbsent(jsonCard.id, id -> new ScryfallPrinting(set, card, jsonCard));

		ScryfallPrintedFace leftPrint = print.faces.computeIfAbsent(Card.Face.Kind.Front, f -> new ScryfallPrintedFace(print, front, jsonCard, jsonFront));
		ScryfallPrintedFace rightPrint = print.faces.computeIfAbsent(Card.Face.Kind.Other, f -> new ScryfallPrintedFace(print, adventure, jsonCard, jsonAdventure));

		set.printings.put(print.id(), print);
		printings.put(print.id(), print);
	}
}
