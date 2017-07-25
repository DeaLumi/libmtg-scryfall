package emi.lib.mtg.scryfall.v2;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import emi.lib.Service;
import emi.lib.mtg.card.Card;
import emi.lib.mtg.data.CardSet;
import emi.lib.mtg.scryfall.ScryfallCardSource;
import emi.lib.mtg.v2.DataSource;
import emi.lib.scryfall.Scryfall;
import emi.lib.scryfall.api.Catalog;
import emi.lib.scryfall.api.enums.CardLayout;
import emi.lib.scryfall.api.enums.SetType;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service.Provider(DataSource.class)
@Service.Property.String(name="name", value="Scryfall")
public class ScryfallDataSource implements DataSource {
	private static final long UPDATE_INTERVAL = 7 * 24 * 60 * 60 * 1000;

	private BiMap<String, ScryfallCard> cards;
	private BiMap<UUID, ScryfallPrinting> printings;
	private BiMap<String, ScryfallSet> sets;

	private static void expect(Object input, Object expected) throws IOException {
		if (!Objects.equals(input, expected)) {
			throw new IOException(String.format("Expected to see \'%s\', but got \'%s\' instead!", Objects.toString(input), Objects.toString(expected)));
		}
	}

	private static void requireSamePlayable(emi.lib.scryfall.api.Card oracle, emi.lib.scryfall.api.Card test) throws IOException {
		expect(test.name, oracle.name);
		expect(test.manaCost, oracle.manaCost);
		expect(test.convertedManaCost, oracle.convertedManaCost);
		expect(test.typeLine, oracle.typeLine);
		expect(test.oracleText, oracle.oracleText);
		expect(test.power, oracle.power);
		expect(test.toughness, oracle.toughness);
		expect(test.loyalty, oracle.loyalty);
		expect(test.handModifier, oracle.handModifier);
		expect(test.colors, oracle.colors);
		expect(test.layout, oracle.layout);
		expect(test.cardFaces, oracle.cardFaces);
	}

	private static void requireSamePlayable(ScryfallCard oracle, emi.lib.scryfall.api.Card test) throws IOException {
		// TODO: The above one was the easy one...
//		System.err.println(String.format("Ignoring requireSamePlayable; write this code already! (%s)", test.name));
	}

	public ScryfallDataSource() throws IOException {
		this.cards = HashBiMap.create();
		this.printings = HashBiMap.create();
		this.sets = HashBiMap.create();

		File dataFile = new File(new File(new File("data"), "scryfall"), "data.json");

		if (!dataFile.getParentFile().exists() && !dataFile.getParentFile().mkdirs()) {
			throw new IOException("Couldn't create parent directories for data file.");
		}

		if (!dataFile.exists() || Instant.now().minusMillis(UPDATE_INTERVAL).isAfter(Instant.ofEpochMilli(dataFile.lastModified()))) {
			System.out.println("Sets file needs update. Please wait...");
			Scryfall api = new Scryfall();
			List<emi.lib.scryfall.api.Set> sets = api.sets();
			List<List<emi.lib.scryfall.api.Card>> cardss = new ArrayList<>();

			JsonWriter writer = Scryfall.GSON.newJsonWriter(new OutputStreamWriter(new FileOutputStream(dataFile), StandardCharsets.UTF_8));

			writer.beginObject();

			writer.name("sets");
			writer.beginObject();
			for (emi.lib.scryfall.api.Set set : sets) {
				System.out.print(" Set: " + set.code + " / " + set.name + "... ");

				if (set.setType == SetType.Token) {
					System.out.println("ignored (token set)");
					continue;
				}

				writer.name(set.code);
				Scryfall.GSON.toJson(set, emi.lib.scryfall.api.Set.class, writer);
				cardss.add(api.query(String.format("e:%s", set.code)));
				System.out.println("done!");
			}
			writer.endObject();

			writer.name("printings");
			writer.beginObject();
			System.out.print("Cards");
			System.out.flush();
			for (List<emi.lib.scryfall.api.Card> cards : cardss) {
				for (emi.lib.scryfall.api.Card card : cards) {
					// Null out some excess data here to save hard drive space.
					card.eur = null;
					card.usd = null;
					card.tix = null;
					card.relatedUris = null;
					card.purchaseUris = null;
					card.legalities = null; // TODO: We might want to bring this back soon...

					writer.name(card.id.toString());
					Scryfall.GSON.toJson(card, emi.lib.scryfall.api.Card.class, writer);
				}
				System.out.print(".");
				System.out.flush();
			}
			writer.endObject();

			writer.endObject();
			writer.close();

			System.out.println("Done! Cleaning up...");

			System.gc();
			System.gc();

			System.out.println("Done! Next update in one week~");
		}

		JsonReader reader = Scryfall.GSON.newJsonReader(new InputStreamReader(new FileInputStream(dataFile), StandardCharsets.UTF_8));

		reader.beginObject();

		expect(reader.nextName(), "sets");
		reader.beginObject();
		while (reader.peek() == JsonToken.NAME) {
			String code = reader.nextName();
			emi.lib.scryfall.api.Set set = Scryfall.GSON.fromJson(reader, emi.lib.scryfall.api.Set.class);
			expect(code, set.code);
			this.sets.put(code, new ScryfallSet(set));
		}
		reader.endObject();

		Map<String, emi.lib.scryfall.api.Card> bSides = new HashMap<>();
		List<emi.lib.scryfall.api.Card> deferredCards = new ArrayList<>();

		expect(reader.nextName(), "printings");
		reader.beginObject();
		while (reader.peek() == JsonToken.NAME) {
			String id = reader.nextName();
			emi.lib.scryfall.api.Card card = Scryfall.GSON.fromJson(reader, emi.lib.scryfall.api.Card.class);
			expect(id, card.id.toString());

			if ((card.layout == CardLayout.Transform || card.layout == CardLayout.Meld) && card.collectorNumber != null && card.collectorNumber.matches("^[0-9]+[b-z]$")) {
				emi.lib.scryfall.api.Card oracle = bSides.get(card.name);
				if (oracle == null) {
					bSides.put(card.name, card);
				} else {
					requireSamePlayable(oracle, card);
				}
			} else {
				deferredCards.add(card);
			}
		}
		reader.endObject();

		for (emi.lib.scryfall.api.Card card : deferredCards) {
			ScryfallCard oracle = this.cards.get(card.name);
			if (oracle == null) {
				oracle = new ScryfallCard(bSides, card);
				this.cards.put(oracle.name(), oracle);
				this.printings.put(card.id, new ScryfallPrinting(oracle, sets.get(card.set), card));
			} else {
				requireSamePlayable(oracle, card);
				this.printings.put(card.id, new ScryfallPrinting(oracle, sets.get(card.set), card));
			}
		}
	}

	@Override
	public Set<ScryfallCard> cards() {
		return cards.values();
	}

	@Override
	public ScryfallCard card(String name) {
		return cards.get(name);
	}

	@Override
	public Set<ScryfallPrinting> printings() {
		return printings.values();
	}

	@Override
	public ScryfallPrinting printing(UUID id) {
		return printings.get(id);
	}

	@Override
	public Set<ScryfallSet> sets() {
		return sets.values();
	}

	@Override
	public ScryfallSet set(String code) {
		return sets.get(code);
	}

	public static void main(String[] args) throws IOException {
		long start = System.nanoTime();
		ScryfallDataSource dataSource = new ScryfallDataSource();
		System.out.println(String.format("New: %.2f seconds", (System.nanoTime() - start) / 1e9));

		System.out.println(String.format("New: %d sets, %d cards, %d printings", dataSource.sets.size(), dataSource.cards.size(), dataSource.printings.size()));

		start = System.nanoTime();
		ScryfallCardSource cardSource = new ScryfallCardSource();
		System.out.println(String.format("Old: %.2f seconds", (System.nanoTime() - start) / 1e9));

		long sets = cardSource.sets().size();
		long printings = cardSource.sets().stream()
				.mapToLong(set -> set.cards().size())
				.sum();

		Map<String, Card> cards = cardSource.sets().stream()
				.flatMap(set -> set.cards().stream())
				.collect(Collectors.toMap(Card::name, c -> c, (c1, c2) -> c1));

		System.out.println(String.format("Old: %d sets, %d cards, %d printings", sets, cards.size(), printings));

		Scryfall api = new Scryfall();
		Catalog cardNames = api.requestJson(new URL("https://api.scryfall.com/catalog/card-names"), Catalog.class);
		System.out.println(String.format("%d cards", cardNames.data.size()));

		for (CardSet oldSet : cardSource.sets()) {
			if (dataSource.set(oldSet.code()) == null) {
				System.out.println(String.format("New data source is missing set %s / %s", oldSet.code(), oldSet.name()));
			} else {
				for (emi.lib.mtg.card.Card oldCard : oldSet.cards()) {
					if (dataSource.card(oldCard.name()) == null) {
						System.out.println(String.format("New data source is missing card %s", oldCard.name()));
					} else {
						emi.lib.mtg.v2.Card newCard = dataSource.card(oldCard.name());

						if (newCard.printings().stream().noneMatch(pr -> pr.set().code().equals(oldSet.code()))) {
							System.out.println(String.format("New data source is missing %s from %s / %s", oldCard.name(), oldSet.code(), oldSet.name()));
						}
					}
				}
			}
		}

		Set<String> newCardNames = new HashSet<>();

		dataSource.cards()
				.forEach(c -> {
					newCardNames.add(c.fullName());
					newCardNames.add(c.name());

					c.faces().stream()
							.map(emi.lib.mtg.v2.Card.Face::name)
							.forEach(newCardNames::add);
				});

		for (String name : cardNames.data) {
			if (!newCardNames.contains(name)) {
				System.out.println("New data source is missing card name " + name);
			}
		}
	}
}
