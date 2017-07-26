package emi.lib.mtg.scryfall.v2_1;

import com.google.common.collect.EnumHashBiMap;
import com.google.common.collect.HashBiMap;
import emi.lib.mtg.Card;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class ScryfallCard implements Card {

	static void create(Map<String, emi.lib.scryfall.api.Set> jsonSets,
					   Map<UUID, emi.lib.scryfall.api.Card> jsonCards,
					   emi.lib.scryfall.api.Card jsonCard,
					   Map<String, ScryfallSet> sets,
					   Map<String, ScryfallCard> cards,
					   Map<UUID, ScryfallPrinting> printings) {
		switch (jsonCard.layout) {
			case Normal: {
				// TODO: Check a bunch of things before calling createSimple...
				break;
			}

			case Split: {
				break;
			}

			case Flip: {
				break;
			}

			case Transform: {
				break;
			}

			case Meld: {
				createMeld(jsonSets, jsonCards, jsonCard, sets, cards, printings);
				break;
			}

			case Leveler:
			case Plane:
			case Phenomenon:
			case Scheme:
			case Vanguard: {
				createSimple(jsonSets, jsonCards, jsonCard, sets, cards, printings);
			}

			case Token:
			case Emblem: {
				assert false : "Tokens are not cards!";
				break;
			}

			default: {
				assert false;
				break;
			}
		}
	}

	private static void createSimple(Map<String, emi.lib.scryfall.api.Set> jsonSets,
									 Map<UUID, emi.lib.scryfall.api.Card> jsonCards,
									 emi.lib.scryfall.api.Card jsonCard,
									 Map<String, ScryfallSet> sets,
									 Map<String, ScryfallCard> cards,
									 Map<UUID, ScryfallPrinting> printings) {
		ScryfallCard card = cards.computeIfAbsent(jsonCard.name, ScryfallCard::new);
		ScryfallFace front = card.faces.computeIfAbsent(Face.Kind.Front, f -> new ScryfallFace(jsonCard));
		ScryfallSet set = sets.computeIfAbsent(jsonCard.set, s -> new ScryfallSet(jsonSets.get(s)));
		ScryfallPrinting print = card.printings.computeIfAbsent(jsonCard.id, f -> new ScryfallPrinting(set, card, jsonCard));
		ScryfallPrintedFace frontPrint = print.faces.computeIfAbsent(Face.Kind.Front, f -> new ScryfallPrintedFace(front, jsonCard));

		printings.put(print.id(), print);
	}

	private static void createMeld(Map<String, emi.lib.scryfall.api.Set> jsonSets,
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
		ScryfallFace front1 = card1.faces.computeIfAbsent(Face.Kind.Front, f -> new ScryfallFace(frontJson1));
		ScryfallFace back = card1.faces.computeIfAbsent(Face.Kind.Transformed, f -> new ScryfallFace(Face.Kind.Transformed, backJson));

		ScryfallCard card2 = cards.computeIfAbsent(frontJson2.name, ScryfallCard::new);
		ScryfallFace front2 = card2.faces.computeIfAbsent(Face.Kind.Front, f -> new ScryfallFace(frontJson2));
		ScryfallFace back2 = card2.faces.computeIfAbsent(Face.Kind.Transformed, f -> back);

		assert back == back2;

		ScryfallSet set = sets.computeIfAbsent(frontJson1.set, s -> new ScryfallSet(jsonSets.get(s)));

		assert sets.get(frontJson2.set) == set;
		assert sets.get(backJson.set) == set;

		ScryfallPrinting print1 = card1.printings.computeIfAbsent(frontJson1.id, id -> new ScryfallPrinting(set, card1, frontJson1));
		ScryfallPrintedFace frontPrint1 = print1.faces.computeIfAbsent(Face.Kind.Front, f -> new ScryfallPrintedFace(front1, frontJson1));
		ScryfallPrintedFace backPrint = print1.faces.computeIfAbsent(Face.Kind.Transformed, f -> new ScryfallPrintedFace(back, backJson));

		ScryfallPrinting print2 = card2.printings.computeIfAbsent(frontJson2.id, id -> new ScryfallPrinting(set, card2, frontJson2));
		ScryfallPrintedFace frontPrint2 = print2.faces.computeIfAbsent(Face.Kind.Front, f -> new ScryfallPrintedFace(front2, frontJson2));
		ScryfallPrintedFace backPrint2 = print2.faces.computeIfAbsent(Face.Kind.Transformed, f -> backPrint);

		assert backPrint == backPrint2;

		printings.put(print1.id(), print1);
		printings.put(print2.id(), print2);
	}

	private final String name;
	private final EnumHashBiMap<Face.Kind, ScryfallFace> faces;
	private final HashBiMap<UUID, ScryfallPrinting> printings;

	private ScryfallCard(String name) {
		this.name = name;
		this.faces = EnumHashBiMap.create(Face.Kind.class);
		this.printings = HashBiMap.create();
	}

	@Override
	public Set<ScryfallFace> faces() {
		return faces.values();
	}

	@Override
	public ScryfallFace face(Face.Kind kind) {
		return faces.get(kind);
	}

	private ScryfallCard face(ScryfallFace face) {
		this.faces.put(face.kind(), face);
		return this;
	}

	@Override
	public Set<ScryfallPrinting> printings() {
		return printings.values();
	}

	@Override
	public ScryfallPrinting printing(UUID id) {
		return printings.get(id);
	}

	private ScryfallCard printing(ScryfallPrinting printing) {
		this.printings.put(printing.id(), printing);
		return this;
	}

	@Override
	public String name() {
		return this.name;
	}
}
