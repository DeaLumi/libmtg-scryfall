package emi.lib.mtg.scryfall;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import emi.lib.mtg.Card;
import emi.lib.mtg.DataSource;
import emi.lib.scryfall.Scryfall;
import emi.lib.scryfall.api.Catalog;
import emi.lib.scryfall.api.enums.CardLayout;
import emi.lib.scryfall.api.enums.SetType;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

public class ScryfallDataSource implements DataSource {
	private static final long UPDATE_INTERVAL = 7 * 24 * 60 * 60 * 1000;

	private static void expect(Object input, Object expected) throws IOException {
		if (!Objects.equals(input, expected)) {
			throw new IOException(String.format("Expected to see \'%s\', but got \'%s\' instead!", Objects.toString(input), Objects.toString(expected)));
		}
	}

	private static int status = 0;

	private static void advanceStatus() {
		System.out.print("\033[1D");
		switch (status % 4) {
			case 0:
				System.out.print("/");
				break;
			case 1:
				System.out.print("-");
				break;
			case 2:
				System.out.print("\\");
				break;
			case 3:
				System.out.print("|");
				break;
			default:
				System.out.print("?");
				break;
		}
		System.out.flush();

		++status;
	}

	private final BiMap<UUID, ScryfallPrinting> printings;
	private final BiMap<String, ScryfallCard> cards;
	private final BiMap<String, ScryfallSet> sets;

	public ScryfallDataSource() throws IOException {
		File dataFile = new File(new File(new File("data"), "scryfall"), "data.json");

		if (!dataFile.getParentFile().exists() && !dataFile.getParentFile().mkdirs()) {
			throw new IOException("Couldn't create parent directories for data file.");
		}

		if (!dataFile.exists() || Instant.now().minusMillis(UPDATE_INTERVAL).isAfter(Instant.ofEpochMilli(dataFile.lastModified()))) {
			System.out.println("Sets file needs update. Please wait...");
			Scryfall api = new Scryfall();
			List<emi.lib.scryfall.api.Set> sets = api.sets();
			List<emi.lib.scryfall.api.Card> cards = api.cards();
			Set<String> droppedSets = new HashSet<>();

			JsonWriter writer = Scryfall.GSON.newJsonWriter(new OutputStreamWriter(new FileOutputStream(dataFile), StandardCharsets.UTF_8));

			writer.beginObject();

			writer.name("sets");
			writer.beginObject();
			for (emi.lib.scryfall.api.Set set : sets) {
				System.out.print(" Set: " + set.code + " / " + set.name + "... ");

				if (set.setType == SetType.Token) {
					System.out.println("ignored (token set)");
					droppedSets.add(set.code);
					continue;
				}

				writer.name(set.code);
				Scryfall.GSON.toJson(set, emi.lib.scryfall.api.Set.class, writer);
				System.out.println("done!");
			}
			writer.endObject();

			writer.name("printings");
			writer.beginObject();
			System.out.print("Cards  ");
			System.out.flush();
			int statusCounter = 0;
			for (emi.lib.scryfall.api.Card card : cards) {
				if (card.layout == CardLayout.Token) {
					continue;
				}

				if (droppedSets.contains(card.set)) {
					continue;
				}

				// Null out some excess data here to save hard drive space.
				card.eur = null;
				card.usd = null;
				card.tix = null;
				card.relatedUris = null;
				card.purchaseUris = null;
				card.legalities = null; // TODO: We might want to bring this back soon...

				writer.name(card.id.toString());
				Scryfall.GSON.toJson(card, emi.lib.scryfall.api.Card.class, writer);

				++statusCounter;
				if (statusCounter == 25) {
					advanceStatus();
					statusCounter = 0;
				}
			}
			writer.endObject();

			writer.endObject();
			writer.close();

			System.out.println();
			System.out.println("Done! Cleaning up...");

			System.gc();
			System.gc();

			System.out.println("Done! Next update in one week~");
		}

		BiMap<String, emi.lib.scryfall.api.Set> jsonSets = HashBiMap.create();
		BiMap<UUID, emi.lib.scryfall.api.Card> jsonCards = HashBiMap.create();

		JsonReader reader = Scryfall.GSON.newJsonReader(new InputStreamReader(new FileInputStream(dataFile), StandardCharsets.UTF_8));

		reader.beginObject();

		expect(reader.nextName(), "sets");
		reader.beginObject();
		while (reader.peek() == JsonToken.NAME) {
			String code = reader.nextName();
			emi.lib.scryfall.api.Set set = Scryfall.GSON.fromJson(reader, emi.lib.scryfall.api.Set.class);
			expect(code, set.code);

			if (set.setType == SetType.Token) {
				continue;
			}

			jsonSets.put(set.code, set);
		}
		reader.endObject();

		expect(reader.nextName(), "printings");
		reader.beginObject();
		while (reader.peek() == JsonToken.NAME) {
			String id = reader.nextName();
			emi.lib.scryfall.api.Card card = Scryfall.GSON.fromJson(reader, emi.lib.scryfall.api.Card.class);
			expect(id, card.id.toString());

			if (card.layout == CardLayout.Token) {
				continue;
			}

			jsonCards.put(card.id, card);
		}
		reader.endObject();

		this.cards = HashBiMap.create();
		this.printings = HashBiMap.create();
		this.sets = HashBiMap.create();

		while (!jsonCards.isEmpty()) {
			emi.lib.scryfall.api.Card jsonCard = jsonCards.values().iterator().next();

			ScryfallCardFactory.create(jsonSets, jsonCards, jsonCard, sets, cards, printings);
		}
	}

	@Override
	public Set<? extends Card> cards() {
		return cards.values();
	}

	@Override
	public Card card(String name) {
		return cards.get(name);
	}

	@Override
	public Set<? extends Card.Printing> printings() {
		return printings.values();
	}

	@Override
	public Card.Printing printing(UUID id) {
		return printings.get(id);
	}

	@Override
	public Set<? extends emi.lib.mtg.Set> sets() {
		return sets.values();
	}

	@Override
	public emi.lib.mtg.Set set(String code) {
		return sets.get(code);
	}

	public static void main(String[] args) throws IOException {
		System.in.read();

		long start = System.nanoTime();
		ScryfallDataSource dataSource = new ScryfallDataSource();
		System.out.println(String.format("New: %.2f seconds", (System.nanoTime() - start) / 1e9));

		System.out.println(String.format("New: %d sets, %d cards, %d printings", dataSource.sets.size(), dataSource.cards.size(), dataSource.printings.size()));

		System.in.read();

		Scryfall api = new Scryfall();
		Catalog cardNames = api.requestJson(new URL("https://api.scryfall.com/catalog/card-names"), Catalog.class);
		System.out.println(String.format("%d cards", cardNames.data.size()));

		Set<String> newCardNames = new HashSet<>();

		dataSource.cards()
				.forEach(c -> {
					newCardNames.add(c.fullName());
					newCardNames.add(c.name());

					c.faces().stream()
							.map(Card.Face::name)
							.forEach(newCardNames::add);
				});

		for (String name : cardNames.data) {
			if (!newCardNames.contains(name)) {
				System.out.println("New data source is missing card name " + name);
			}
		}

	}
}
