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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.function.DoubleConsumer;

public class ScryfallDataSource implements DataSource {
	private static final long UPDATE_INTERVAL = 7 * 24 * 60 * 60 * 1000;

	private static final Path DATA_FILE = Paths.get("data", "scryfall", "data.json");

	private static void expect(Object input, Object expected) throws IOException {
		if (!Objects.equals(input, expected)) {
			throw new IOException(String.format("Expected to see \'%s\', but got \'%s\' instead!", Objects.toString(input), Objects.toString(expected)));
		}
	}

	private final BiMap<UUID, ScryfallPrinting> printings;
	private final BiMap<UUID, ScryfallCard> cards;
	private final BiMap<String, ScryfallSet> sets;

	public ScryfallDataSource() throws IOException {
		this.cards = HashBiMap.create();
		this.printings = HashBiMap.create();
		this.sets = HashBiMap.create();

		try {
			loadData();
		} catch (IOException ioe) {
			System.err.println("Scryfall data source couldn't load data file.");
			ioe.printStackTrace();
			System.err.println("We'll continue with no cards...");
		}
	}

	@Override
	public Set<? extends Card> cards() {
		return cards.values();
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

	@Override
	public boolean update(DoubleConsumer progress) throws IOException {
		if (!Files.exists(DATA_FILE.getParent())) {
			Files.createDirectories(DATA_FILE.getParent());
		}

		System.out.println("Sets file needs update. Please wait...");
		Scryfall api = new Scryfall();
		List<emi.lib.scryfall.api.Set> sets = api.sets();
		Set<String> droppedSets = new HashSet<>();

		Path tmp = Files.createTempFile("scryfall-data", ".json");
		JsonWriter writer = Scryfall.GSON.newJsonWriter(new OutputStreamWriter(Files.newOutputStream(tmp), StandardCharsets.UTF_8));

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

		List<emi.lib.scryfall.api.Card> cards = api.cards();

		writer.name("printings");
		writer.beginObject();
		System.out.print("Cards:     ");
		System.out.flush();
		int statusCounter = 0;
		for (emi.lib.scryfall.api.Card card : cards) {
			if (card.layout == CardLayout.Token || card.layout == CardLayout.DoubleFacedToken) {
				continue;
			}

			if (droppedSets.contains(card.set)) {
				continue;
			}

			// Null out some excess data here to save hard drive space.
			card.legalities = null; // TODO: We might want to bring this back soon...

			writer.name(card.id.toString());
			Scryfall.GSON.toJson(card, emi.lib.scryfall.api.Card.class, writer);

			++statusCounter;
			double status = (double) statusCounter / (double) cards.size();
			System.out.print(String.format("\033[4D% 3d%%", (int) (status * 100.0)));

			if (progress != null) {
				progress.accept(status);
			}
		}
		writer.endObject();

		writer.endObject();
		writer.close();

		Files.copy(tmp, DATA_FILE, StandardCopyOption.REPLACE_EXISTING);
		Files.delete(tmp);

		System.out.println();
		System.out.println("Done! Cleaning up...");

		System.gc();
		System.gc();

		System.out.println("Done! Next update in one week~");

		loadData();

		return true;
	}

	@Override
	public boolean needsUpdate() {
		try {
			return !Files.exists(DATA_FILE) || Instant.now().minusMillis(UPDATE_INTERVAL).isAfter(Files.getLastModifiedTime(DATA_FILE).toInstant());
		} catch (IOException ioe) {
			System.err.println(String.format("Unable to check %s for freshness -- please update Scryfall data.", DATA_FILE.toString()));
			ioe.printStackTrace();
			return true;
		}
	}

	private void loadData() throws IOException {
		this.cards.clear();
		this.printings.clear();
		this.sets.clear();

		BiMap<String, emi.lib.scryfall.api.Set> jsonSets = HashBiMap.create();
		BiMap<UUID, emi.lib.scryfall.api.Card> jsonCards = HashBiMap.create();

		JsonReader reader = Scryfall.GSON.newJsonReader(new InputStreamReader(Files.newInputStream(DATA_FILE), StandardCharsets.UTF_8));

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

			if (card.layout == CardLayout.Token || card.layout == CardLayout.DoubleFacedToken) {
				continue;
			}

			jsonCards.put(card.id, card);
		}
		reader.endObject();
		reader.close();

		while (!jsonCards.isEmpty()) {
			emi.lib.scryfall.api.Card jsonCard = jsonCards.values().iterator().next();

			ScryfallCardFactory.create(jsonSets, jsonCards, jsonCard, sets, cards, printings);
		}
	}

	public static void main(String[] args) throws IOException {
		System.in.read();

		long start = System.nanoTime();
		ScryfallDataSource dataSource = new ScryfallDataSource();

		if (dataSource.needsUpdate()) {
			dataSource.update(null);
		}

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
