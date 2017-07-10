package emi.lib.mtg.scryfall;

import com.google.gson.stream.JsonWriter;
import emi.lib.Service;
import emi.lib.mtg.card.Card;
import emi.lib.mtg.card.CardFace;
import emi.lib.mtg.card.CardFaceExtended;
import emi.lib.mtg.characteristic.*;
import emi.lib.mtg.characteristic.impl.BasicCardTypeLine;
import emi.lib.mtg.characteristic.impl.BasicManaCost;
import emi.lib.mtg.data.CardSet;
import emi.lib.mtg.data.CardSource;
import emi.lib.scryfall.PagedList;
import emi.lib.scryfall.Scryfall;
import emi.lib.scryfall.api.ApiObjectList;
import emi.lib.scryfall.api.Set;
import emi.lib.scryfall.api.enums.CardLayout;
import emi.lib.scryfall.api.enums.Rarity;
import emi.lib.scryfall.api.enums.SetType;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Executors;

@Service.Provider(CardSource.class)
@Service.Property.String(name="name", value="Scryfall")
public class ScryfallCardSource implements CardSource {
	private static final File PARENT_DIR = new File(new File("data"), "scryfall");

	static {
		if (!PARENT_DIR.exists() && !PARENT_DIR.mkdirs()) {
			throw new Error("Couldn't create data/scryfall directory.");
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

	public ScryfallCardSource() throws IOException {
		try (FileReader reader = new FileReader(new File(PARENT_DIR, "sets.json"))) {
			// do a thing
		} catch (FileNotFoundException fnfe) {
			Thread th = new Thread(() -> {
				Scryfall api = new Scryfall();

				PagedList<Set> sets = api.sets();

				try (FileWriter writer = new FileWriter(new File(PARENT_DIR, "cards.json"))) {
					JsonWriter jw = Scryfall.GSON.newJsonWriter(writer);

					jw.beginObject();

					for (Set set : sets) {
						if (set.setType == SetType.Token) {
							System.out.println("(Skipping " + set.name + ".)");
							continue;
						}

						System.out.print("Downloading " + set.name + "... ");
						System.out.flush();

						try (FileWriter setWriter = new FileWriter(new File(PARENT_DIR, String.format("%s.json", set.code)))) {
							Scryfall.GSON.toJson(set, setWriter);
						} catch (IOException ioe) {
							ioe.printStackTrace();
						}

						PagedList<emi.lib.scryfall.api.Card> cards = api.query(String.format("e:%s", set.code));

						for (emi.lib.scryfall.api.Card card : cards) {
							jw.name(card.id.toString());
							Scryfall.GSON.toJson(card, emi.lib.scryfall.api.Card.class, jw);
						}

						new ScryfallSet(set, cards);

						System.out.println("Done.");
					}

					jw.endObject();
				} catch (IOException ioe) {
					ioe.printStackTrace();
				}
			}, "Scryfall Card Downloading Thread");
			th.setDaemon(true);
			th.start();
		}
	}

	@Override
	public Collection<? extends CardSet> sets() {
		return null;
	}

	@Override
	public Card get(UUID id) {
		return null;
	}

	public static void main(String[] args) throws IOException {
		new ScryfallCardSource();

		System.in.read();
	}
}
