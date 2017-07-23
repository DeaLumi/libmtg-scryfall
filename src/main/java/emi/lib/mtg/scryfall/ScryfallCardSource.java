package emi.lib.mtg.scryfall;

import com.google.common.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import emi.lib.Service;
import emi.lib.mtg.card.Card;
import emi.lib.mtg.data.CardSet;
import emi.lib.mtg.data.CardSource;
import emi.lib.scryfall.Scryfall;
import emi.lib.scryfall.api.enums.SetType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service.Provider(CardSource.class)
@Service.Property.String(name="name", value="Scryfall")
public class ScryfallCardSource implements CardSource {
	private static final File PARENT_DIR = new File(new File("data"), "scryfall");

	static {
		if (!PARENT_DIR.exists() && !PARENT_DIR.mkdirs()) {
			throw new Error("Couldn't create data/scryfall directory.");
		}
	}

	private static long UPDATE_INTERVAL = 7*24*60*60*1000; // 7 days/wk * 24 hours/day * 60 minutes/hour * 60 seconds/minute * 1000 milliseconds/second * 1 wk
//	private static final long UPDATE_INTERVAL = 15*60*1000; // 5-minute interval for testing.

	private static boolean needsUpdate(File f) {
		if (!f.exists() || Instant.now().toEpochMilli() - f.lastModified() > UPDATE_INTERVAL) {
			return true;
		} else {
			return false;
		}
	}

	/*
	Scryfall is an reference website. Its goal is to supplement or replace Gatherer as a way
	for players to look up cards, and it stores its card data with that in mind.

	lib.mtg, however, is meant to be used to power things like deck builders. In function, it
	more closely matches the information model one might expect of a website that sells singles
	of cards.

	This means we have a lot to remap, which is unfortunate. With that in mind, here's the idea:
	- Download all of Scryfall's data.
	- Break the downloaded data out into a giant list of all card faces.
	- Go through each card again, this time finding its faces and linking them appropriately.
	- Write card faces and cards to JSON somewhere.
	 */

	private static class SetStub {
		public emi.lib.scryfall.api.Set set;
		public List<emi.lib.scryfall.api.Card> cards;
	}

	private Map<String, ScryfallSet> sets;

	public ScryfallCardSource() throws IOException {
		this.sets = new HashMap<>();

		Scryfall api = new Scryfall();
		File setsFile = new File(PARENT_DIR, "scryfall-sets.json");

		if (needsUpdate(setsFile)) {
			System.out.println("Sets file needs update. Please wait...");

			List<emi.lib.scryfall.api.Set> sets = api.sets();

			JsonWriter writer = Scryfall.GSON.newJsonWriter(new OutputStreamWriter(new FileOutputStream(setsFile), StandardCharsets.UTF_8));

			writer.beginObject();
			for (emi.lib.scryfall.api.Set set : sets) {
				System.out.print(" Set: " + set.code + " / " + set.name + "... ");

				if (set.setType == SetType.Token) {
					System.out.println("ignored (token set)");
					continue;
				}

				writer.name(set.code);
				writer.beginObject();

				writer.name("set");
				Scryfall.GSON.toJson(set, emi.lib.scryfall.api.Set.class, writer);

				writer.name("cards");
				writer.beginArray();
				List<emi.lib.scryfall.api.Card> cards = api.query(String.format("e:%s", set.code));
				for (emi.lib.scryfall.api.Card card : cards) {
					// Null out some excess data here to save hard drive space.
					card.eur = null;
					card.usd = null;
					card.tix = null;
					card.relatedUris = null;
					card.purchaseUris = null;
					card.legalities = null; // TODO: We might want to bring this back soon...

					Scryfall.GSON.toJson(card, emi.lib.scryfall.api.Card.class, writer);
				}
				writer.endArray();

				System.out.println("done!");

				writer.endObject();
			}
			writer.endObject();

			writer.close();

			System.out.println("Done! Cleaning up...");

			System.gc();
			System.gc();

			System.out.println("Done! Next update in one week~");
		}

		System.out.println("Loading Scryfall card data...");
		System.out.flush();

		Map<String, SetStub> setStubMap = Scryfall.GSON.fromJson(new InputStreamReader(new FileInputStream(setsFile), StandardCharsets.UTF_8), new TypeToken<Map<String, SetStub>>(){}.getType());

		for (Map.Entry<String, SetStub> stubEntry : setStubMap.entrySet()) {
			assert stubEntry.getKey().equals(stubEntry.getValue().set.code);

			this.sets.put(stubEntry.getValue().set.code, new ScryfallSet(stubEntry.getValue().set, stubEntry.getValue().cards));
		}

		System.out.println("Done!");
		System.out.flush();
	}

	@Override
	public Collection<? extends CardSet> sets() {
		return this.sets.values();
	}

	@Override
	public Card get(UUID id) {
		for (ScryfallSet set : this.sets.values()) {
			ScryfallSet.ScryfallCard card = set.get(id);

			if (card != null) {
				return card;
			}
		}

		return null;
	}

	public static void main(String[] args) throws IOException {
		ScryfallCardSource dataSource = new ScryfallCardSource();

		long sets = dataSource.sets.size();
		long printings = dataSource.sets.values().stream()
				.mapToLong(set -> set.cards().size())
				.sum();

		Map<String, Card> cards = dataSource.sets.values().stream()
				.flatMap(set -> set.cards().stream())
				.collect(Collectors.toMap(Card::name, c -> c, (c1, c2) -> c1));

		System.out.println(String.format("%d sets, %d cards, %d printings", sets, cards.size(), printings));
	}
}
