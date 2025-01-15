package emi.lib.mtg.scryfall;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import emi.lib.mtg.Card;
import emi.lib.mtg.DataSource;
import emi.lib.mtg.scryfall.api.PagedList;
import emi.lib.mtg.scryfall.api.ScryfallApi;
import emi.lib.mtg.scryfall.api.enums.CardLayout;
import emi.lib.mtg.scryfall.serde.ScryfallSerde;
import emi.mtg.deckbuilder.controller.Tags;
import emi.mtg.deckbuilder.controller.Updateable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.DoubleConsumer;
import java.util.stream.Collectors;

// TODO: All this stuff is doubled up, and I wish I had an easy fix for it. Generics technically work, but...
public class ScryfallTagSource implements Tags.Provider, Updateable {
	public static final String TAGS_FILE_NAME = "scryfall-tags.json";

	private transient Map<String, Collection<Card.Printing.Reference>> updatedCardTags;
	private transient Map<String, Collection<Card.Printing.Reference>> updatedArtTags;

	private final Tags.TagGraph<Card> cardTags;
	private final Tags.TagGraph<Card.Printing> printingTags;

	public ScryfallTagSource() {
		this.updatedCardTags = null;
		this.updatedArtTags = null;
		this.cardTags = new Tags.TagGraph<>();
		this.printingTags = new Tags.TagGraph<>();
	}

	@Override
	public String toString() {
		return "Scryfall Card Tags";
	}

	@Override
	public String description() {
		return "Downloads tagged cards according to Scryfall. See preferences to choose which tags are downloaded. May take several minutes!";
	}

	private static Collection<Card.Printing.Reference> cardReferences(ScryfallApi api, emi.lib.mtg.scryfall.api.Card card) {
		if (card.layout == CardLayout.Meld) {
			// Meld sucks
			return card.allParts.stream()
					.filter(p -> "meld_part".equals(p.component))
					.map(p -> {
						try {
							return api.requestJson(p.uri, emi.lib.mtg.scryfall.api.Card.class);
						} catch (IOException ioe) {
							new ExecutionException("While fetching meld part " + p.name, ioe).printStackTrace(); // TODO
							return null;
						}
					})
					.filter(Objects::nonNull)
					.filter(c -> !ScryfallDataSource.excludeCard(c))
					.map(c -> Card.Printing.Reference.to(c.name, c.set, c.collectorNumber))
					.collect(Collectors.toList());
		} else if (card.cardFaces != null && card.cardFaces.size() >= 2 && card.layout != CardLayout.Split) {
			return Collections.singleton(Card.Printing.Reference.to(card.cardFaces.get(0).name, card.set, card.collectorNumber));
		} else {
			return Collections.singleton(Card.Printing.Reference.to(card.name, card.set, card.collectorNumber));
		}
	}

	@Override
	public void update(Path dataDir, Progress progress) throws IOException {
		HashMap<String, List<emi.lib.mtg.scryfall.api.Card>> cardTaggings = new HashMap<>();
		HashMap<String, List<emi.lib.mtg.scryfall.api.Card>> artTaggings = new HashMap<>();

		updatedCardTags = new HashMap<>();
		updatedArtTags = new HashMap<>();

		ScryfallPreferences prefs = ScryfallPreferences.get();
		ScryfallApi api = ScryfallApi.get();
		String[] cardTags = prefs.functionTags.trim().isEmpty() ? new String[0] : prefs.functionTags.trim().split(" *, *");
		String[] artTags = prefs.artTags.trim().isEmpty() ? new String[0] : prefs.artTags.trim().split(" *, *");

		progress.accept(0.0, "Fetching initial results...");

		long target = 0;
		for (int i = 0; i < cardTags.length; ++i) {
			String tag = cardTags[i];
			PagedList<emi.lib.mtg.scryfall.api.Card> cards = api.query(String.format("function:\"%s\"", tag), "cards", false, false);
			cardTaggings.put(tag, cards);
			target += cards.size();
		}

		for (int i = 0; i < artTags.length; ++i) {
			String tag = artTags[i];
			PagedList<emi.lib.mtg.scryfall.api.Card> cards = api.query(String.format("art:\"%s\"", tag), "prints", false, false);
			artTaggings.put(tag, cards);
			target += cards.size();
		}

		long processed = 0;
		try (JsonWriter writer = ScryfallApi.GSON.newJsonWriter(Files.newBufferedWriter(dataDir.resolve(TAGS_FILE_NAME)))) {
			writer.beginObject();
			writer.name("count");
			writer.value(target); // TODO Target can be off by a few due to STUPID MELD CARDS >:(

			writer.name("cards");
			writer.beginObject();
			for (int i = 0; i < cardTags.length; ++i) {
				String tag = cardTags[i];
				List<emi.lib.mtg.scryfall.api.Card> matchingCards = cardTaggings.get(tag);
				List<Card.Printing.Reference> references = new ArrayList<>();
				progress.accept(processed / (double) target, "Fetching \"" + tag + "\"...");

				writer.name(tag);
				writer.beginArray();

				for (int j = 0; j < matchingCards.size(); ++j) {
					emi.lib.mtg.scryfall.api.Card card = matchingCards.get(j);
					if (ScryfallDataSource.excludeCard(card)) continue; // TODO token
					// TODO: Printing reference instead of card name (thanks UST)
					for (Card.Printing.Reference ref : cardReferences(api, card)) {
						writer.value(ref.toString());
						references.add(ref);
					}
					++processed;
					progress.accept(processed / (double) target, String.format("Fetching \"%s\" (%d / %d)...", tag, j, matchingCards.size()));
				}

				writer.endArray();
				updatedCardTags.put(tag, references);
			}
			writer.endObject();

			writer.name("prints");
			writer.beginObject();
			for (int i = 0; i < artTags.length; ++i) {
				String tag = artTags[i];
				List<emi.lib.mtg.scryfall.api.Card> matchingCards = artTaggings.get(tag);
				List<Card.Printing.Reference> references = new ArrayList<>();
				progress.accept(processed / (double) target, "Fetching \"" + tag + "\"...");

				writer.name(tag);
				writer.beginArray();

				for (int j = 0; j < matchingCards.size(); ++j) {
					emi.lib.mtg.scryfall.api.Card card = matchingCards.get(j);
					if (ScryfallDataSource.excludeCard(card)) continue; // TODO token
					for (Card.Printing.Reference ref : cardReferences(api, card)) {
						writer.value(ref.toString());
						references.add(ref);
					}
					++processed;
					progress.accept(processed / (double) target, String.format("Fetching \"%s\" (%d / %d)...", tag, j, matchingCards.size()));
				}

				writer.endArray();
				updatedArtTags.put(tag, references);
			}
			writer.endObject();

			writer.endObject();
		}

		progress.accept(1.0, "Done!");
	}

	@Override
	public boolean updateAvailable(Path dataDir) {
		return Util.needsUpdate(dataDir.resolve(TAGS_FILE_NAME), (long) ScryfallPreferences.get().updateInterval * 24 * 60 * 60);
	}

	@Override
	public boolean load(DataSource data, Path path, DoubleConsumer progress) throws IOException {
		if (progress == null) progress = d -> {};

		if (updatedCardTags != null && updatedArtTags != null) {
			long target = updatedCardTags.values().stream().mapToInt(Collection::size).sum() + updatedArtTags.values().stream().mapToInt(Collection::size).sum();
			long processed = 0;

			progress.accept(0.0);

			for (String tag : updatedCardTags.keySet()) {
				for (Card.Printing.Reference ref : updatedCardTags.get(tag)) {
					Card.Printing pr = data.printing(ref);

					if (pr == null) {
						System.err.printf("Couldn't find printing %s to tag with %s! Continuing...%n", ref, tag);
						continue;
					}

					cardTags.tag(pr.card(), tag);

					++processed;
					progress.accept(processed / (double) target);
				}
			}
			updatedCardTags = null;

			for (String tag : updatedArtTags.keySet()) {
				for (Card.Printing.Reference ref : updatedArtTags.get(tag)) {
					Card.Printing pr = data.printing(ref);

					if (pr == null) {
						System.err.printf("Couldn't find printing %s to tag with (art tag) %s! Continuing...%n", ref, tag);
						continue;
					}

					printingTags.tag(pr, tag);

					++processed;
					progress.accept(processed / (double) target);
				}
			}
			updatedArtTags = null;

			progress.accept(1.0);

			return true;
		}

		Path file = path.resolve(TAGS_FILE_NAME);
		if (!Files.exists(file)) return true;

		try (JsonReader reader = ScryfallApi.GSON.newJsonReader(Files.newBufferedReader(file))) {
			long target = -1;
			long processed = 0;

			reader.beginObject();

			progress.accept(0.0);

			while (reader.hasNext() && reader.peek() == JsonToken.NAME) {
				String section = reader.nextName();
				switch (section) {
					case "count": {
						target = reader.nextLong();
						break;
					}
					case "cards": {
						reader.beginObject();
						while (reader.hasNext()) {
							String tag = reader.nextName();
							reader.beginArray();
							while (reader.hasNext()) {
								String val = reader.nextString();
								try {
									// TODO: Printing reference instead of card name
									Card.Printing.Reference ref = Card.Printing.Reference.valueOf(val);
									Card.Printing pr = data.printing(ref);
									if (pr == null) {
										System.err.printf("Couldn't find printing %s to tag with %s! Continuing...%n", ref, tag);
									} else {
										cardTags.tag(pr.card(), tag);
									}
								} catch (IllegalArgumentException iae) {
									new IOException(String.format("While trying to tag %s with %s. Ignoring.", val, tag), iae).printStackTrace();
								}
								++processed;
								if (target > 0) progress.accept(processed / (double) target);
							}
							reader.endArray();
						}
						reader.endObject();
						break;
					}
					case "prints": {
						reader.beginObject();
						while (reader.hasNext()) {
							String tag = reader.nextName();
							reader.beginArray();
							while (reader.hasNext()) {
								String val = reader.nextString();
								try {
									Card.Printing.Reference ref = Card.Printing.Reference.valueOf(val);
									Card.Printing pr = data.printing(ref);
									if (pr == null) {
										System.err.printf("Couldn't find printing %s to tag with (art tag) %s! Continuing...%n", ref, tag);
									} else {
										printingTags.tag(pr, tag);
									}
								} catch (IllegalArgumentException iae) {
									new IOException(String.format("While trying to tag %s with %s. Ignoring.", val, tag), iae).printStackTrace();
								}
								++processed;
								if (target > 0) progress.accept(processed / (double) target);
							}
							reader.endArray();
						}
						reader.endObject();
						break;
					}
					default: {
						new IOException("Unexpected section " + section + " while parsing " + file.toAbsolutePath() + "; ignoring...").printStackTrace();
						break;
					}
				}
			}

			reader.endObject();
		}

		return true;
	}

	@Override
	public Set<String> tags(Card card) {
		return cardTags.tags(card);
	}

	@Override
	public Set<Card> cards(String s) {
		return cardTags.objects(s);
	}

	@Override
	public Set<String> tags(Card.Printing printing) {
		return printingTags.tags(printing);
	}

	@Override
	public Set<Card.Printing> printings(String s) {
		return printingTags.objects(s);
	}

	public static void main(String[] args) throws IOException {
		Path wd = Paths.get(".");
		ScryfallPreferences.get().serde = ScryfallSerde.Implementation.MessagePack;
		ScryfallDataSource data = new ScryfallDataSource();
		if (data.updateAvailable(wd)) data.update(wd, Progress.cmdLine("Updating Data", 100));
		data.loadData(wd, Progress.cmdLine("Loading Data", 100));

		ScryfallTagSource tags = new ScryfallTagSource();
		if (tags.updateAvailable(wd)) tags.update(wd, Progress.cmdLine("Updating Tags", 100));
		tags.load(data, wd, Progress.cmdLine("Loading Tags", 100));
	}
}
