package emi.lib.mtg.scryfall;

import com.google.common.reflect.TypeToken;
import emi.lib.Service;
import emi.lib.mtg.card.Card;
import emi.lib.mtg.data.CardSet;
import emi.lib.mtg.data.CardSource;
import emi.lib.scryfall.Scryfall;
import emi.lib.scryfall.api.enums.SetType;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

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

	private Map<String, ScryfallSet> sets;

	public ScryfallCardSource() throws IOException {
		this.sets = new HashMap<>();

		Scryfall api = new Scryfall();
		File setsFile = new File(PARENT_DIR, "sets.json");

		List<emi.lib.scryfall.api.Set> sets;
		if (needsUpdate(setsFile)) {
			// download sets
			sets = api.sets();
		} else {
			System.out.println("Sets file is up-to-date.");
			FileReader reader = new FileReader(setsFile);
			sets = Scryfall.GSON.fromJson(reader, new TypeToken<List<emi.lib.scryfall.api.Set>>(){}.getType());
			reader.close();
		}

		// iterate over sets and get cards
		for (emi.lib.scryfall.api.Set set : sets) {
			if (set.setType == SetType.Token) {
				continue;
			}

			File cardsFile = new File(PARENT_DIR, String.format("%s-cards.json", set.code));

			List<emi.lib.scryfall.api.Card> cards;
			if (needsUpdate(cardsFile)) {
				cards = api.query(String.format("e:%s", set.code));
			} else {
				System.out.println("Cards file for " + set.name + " is up-to-date.");
				FileReader reader = new FileReader(cardsFile);
				cards = Scryfall.GSON.fromJson(reader, new TypeToken<List<emi.lib.scryfall.api.Card>>(){}.getType());
				reader.close();
			}

			// create the card set
			this.sets.put(set.code, new ScryfallSet(set, cards));

			if (needsUpdate(cardsFile)) {
				System.out.println("Updating cards file for " + set.name);
				FileWriter writer = new FileWriter(cardsFile);
				Scryfall.GSON.toJson(cards, new TypeToken<List<emi.lib.scryfall.api.Card>>(){}.getType(), writer);
				writer.close();
			}
		}

		if (needsUpdate(setsFile)) {
			System.out.println("Updating sets file.");
			FileWriter writer = new FileWriter(setsFile);
			Scryfall.GSON.toJson(sets, new TypeToken<List<emi.lib.scryfall.api.Set>>(){}.getType(), writer);
			writer.close();
		}
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
		new ScryfallCardSource();
	}
}
