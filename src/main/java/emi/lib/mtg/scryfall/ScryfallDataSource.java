package emi.lib.mtg.scryfall;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import emi.lib.mtg.Card;
import emi.lib.mtg.DataSource;
import emi.lib.mtg.scryfall.api.ScryfallApi;
import emi.lib.mtg.scryfall.api.Catalog;
import emi.lib.mtg.scryfall.api.enums.CardLayout;
import emi.lib.mtg.scryfall.api.enums.GameFormat;
import emi.lib.mtg.scryfall.api.enums.SetType;
import emi.lib.mtg.scryfall.util.CardId;
import emi.lib.mtg.scryfall.util.MirrorMap;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.DoubleConsumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class ScryfallDataSource implements DataSource {
	private static final long UPDATE_INTERVAL = 7 * 24 * 60 * 60 * 1000;

	private static final Path DATA_FILE = Paths.get("data", "scryfall", "data.json.gz");

	private static final Collection<GameFormat> DROPPED_FORMATS = Arrays.asList(
			GameFormat.Duel,
			GameFormat.OldSchool,
			GameFormat.Unrecognized);

	private static void expect(Object input, Object expected) throws IOException {
		if (!Objects.equals(input, expected)) {
			throw new IOException(String.format("Expected to see \'%s\', but got \'%s\' instead!", Objects.toString(input), Objects.toString(expected)));
		}
	}

	private final MirrorMap<UUID, ScryfallPrinting> printings = new MirrorMap<>(Hashtable::new);
	private final MirrorMap<CardId, ScryfallCard> cards = new MirrorMap<>(Hashtable::new);
	private final MirrorMap<String, ScryfallSet> sets = new MirrorMap<>(Hashtable::new);

	@Override
	public String toString() {
		return "Scryfall";
	}

	@Override
	public Set<? extends Card> cards() {
		return cards.valueSet();
	}

	@Override
	public Set<? extends Card.Printing> printings() {
		return printings.valueSet();
	}

	@Override
	public Card.Printing printing(UUID id) {
		return printings.get(id);
	}

	@Override
	public Set<? extends emi.lib.mtg.Set> sets() {
		return sets.valueSet();
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

		ScryfallApi api = new ScryfallApi();
		List<emi.lib.mtg.scryfall.api.Set> sets = api.sets();
		Set<String> droppedSets = new HashSet<>();

		Path tmp = Files.createTempFile("scryfall-data", ".json");
		JsonWriter writer = ScryfallApi.GSON.newJsonWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(tmp)), StandardCharsets.UTF_8));

		writer.beginObject();

		writer.name("sets");
		writer.beginObject();
		for (emi.lib.mtg.scryfall.api.Set set : sets) {
			if (set.setType == SetType.Token) {
				droppedSets.add(set.code);
				continue;
			}

			writer.name(set.code);
			ScryfallApi.GSON.toJson(set, emi.lib.mtg.scryfall.api.Set.class, writer);
		}
		writer.endObject();

		List<emi.lib.mtg.scryfall.api.Card> cards = api.defaultCardsBulk(null);

		writer.name("printings");
		writer.beginObject();
		writer.name("count");
		writer.value(cards.size());
		int statusCounter = 0;
		for (emi.lib.mtg.scryfall.api.Card card : cards) {
			if (card.layout == CardLayout.Token || card.layout == CardLayout.DoubleFacedToken || card.layout == CardLayout.Emblem) {
				continue;
			}

			if (card.layout == CardLayout.ArtSeries) {
				continue;
			}

			if ("Card".equals(card.typeLine)) {
				continue;
			}

			if (droppedSets.contains(card.set)) {
				continue;
			}

			// Null out some excess data here to save hard drive space.
			DROPPED_FORMATS.forEach(card.legalities::remove);
			card.purchaseUris = null;
			card.relatedUris = null;
			card.printsSearchUri = null;
			card.rulingsUri = null;
			card.setSearchUri = null;

			if (card.allParts != null) {
				card.allParts.removeIf(p -> "token".equals(p.component) || "combo_piece".equals(p.component));

				if (card.allParts.isEmpty()) {
					card.allParts = null;
				}
			}

			writer.name(card.id.toString());
			ScryfallApi.GSON.toJson(card, emi.lib.mtg.scryfall.api.Card.class, writer);

			if (progress != null) {
				++statusCounter;
				progress.accept((double) statusCounter / (double) cards.size());
			}
		}
		writer.endObject();

		writer.endObject();
		writer.close();

		Files.copy(tmp, DATA_FILE, StandardCopyOption.REPLACE_EXISTING);
		Files.delete(tmp);

		System.gc();
		System.gc();

		return true;
	}

	@Override
	public boolean needsUpdate() {
		try {
			Instant ref = Instant.now().minusMillis(UPDATE_INTERVAL);
			Instant version = Files.getLastModifiedTime(getJarPath()).toInstant();
			if (ref.isBefore(version)) {
				ref = version;
			}

			return !Files.exists(DATA_FILE) ||
					ref.isAfter(Files.getLastModifiedTime(DATA_FILE).toInstant());
		} catch (IOException ioe) {
			System.err.println(String.format("Unable to check %s for freshness -- please update Scryfall data.", DATA_FILE.toString()));
			ioe.printStackTrace();
			return true;
		}
	}

	private static Path getJarPath() {
		URL jarUrl = ScryfallDataSource.class.getProtectionDomain().getCodeSource().getLocation();
		Path jarPath;
		try {
			jarPath = Paths.get(jarUrl.toURI()).toAbsolutePath();
		} catch (URISyntaxException urise) {
			jarPath = Paths.get(jarUrl.getPath()).toAbsolutePath();
		}

		return jarPath;
	}

	private final Map<UUID, CompletableFuture<emi.lib.mtg.scryfall.api.Card>> await = new Hashtable<>();

	private CompletableFuture<emi.lib.mtg.scryfall.api.Card> await(UUID id) {
		return await.computeIfAbsent(id, x -> new CompletableFuture<>());
	}

	protected void process(emi.lib.mtg.scryfall.api.Card card) {
		CompletableFuture<emi.lib.mtg.scryfall.api.Card> pending = await.get(card.id);

		if (pending != null) {
			pending.complete(card);
			return;
		}

		if ("Who // What // When // Where // Why".equals(card.name) || "Smelt // Herd // Saw".equals(card.name)) {
			createSimple(card);
			return;
		}

		switch (card.layout) {
			case Normal:
			case Augment:
			case Host:
			case Leveler:
			case Planar:
			case Scheme:
			case Vanguard:
			case Saga:
				createSimple(card);
				return;
			case Split:
				createTwoFace(card, emi.lib.mtg.Card.Face.Kind.Left, emi.lib.mtg.Card.Face.Kind.Right);
				return;
			case Flip:
				createTwoFace(card, emi.lib.mtg.Card.Face.Kind.Front, emi.lib.mtg.Card.Face.Kind.Flipped);
				return;
			case Transform:
			case ModalDFC:
				createTwoFace(card, emi.lib.mtg.Card.Face.Kind.Front, emi.lib.mtg.Card.Face.Kind.Transformed);
				return;
			case Adventure:
				createTwoFace(card, emi.lib.mtg.Card.Face.Kind.Front, emi.lib.mtg.Card.Face.Kind.Other);
				return;
			case Meld:
				initMeld(card);
				return;
			case Token:
			case DoubleFacedToken:
			case Emblem:
				System.err.println(String.format("Unexpected token or emblem %s in set %s (%s)", card.name, card.setName, card.set));
				return;
		}
	}

	private void createSimple(emi.lib.mtg.scryfall.api.Card jsonCard) {
		ScryfallCard card = cards.computeIfAbsent(CardId.of(jsonCard), id -> new ScryfallCard(jsonCard));
		ScryfallFace front = card.faces.computeIfAbsent(emi.lib.mtg.Card.Face.Kind.Front, k -> new ScryfallFace(jsonCard));
		ScryfallSet set = sets.get(jsonCard.set);

		ScryfallPrinting print = card.printings.computeIfAbsent(jsonCard.id, id -> new ScryfallPrinting(set, card, jsonCard));
		ScryfallPrintedFace frontPrint = print.faces.computeIfAbsent(emi.lib.mtg.Card.Face.Kind.Front, k -> new ScryfallPrintedFace(print, front, jsonCard, null));

		set.printings.put(print.id(), print);
		printings.put(print.id(), print);
	}

	private void createTwoFace(emi.lib.mtg.scryfall.api.Card jsonCard, emi.lib.mtg.Card.Face.Kind firstKind, emi.lib.mtg.Card.Face.Kind secondKind) {
		ScryfallCard card = cards.computeIfAbsent(CardId.of(jsonCard.cardFaces.get(0), jsonCard.cardFaces.get(1)), id -> new ScryfallCard(jsonCard));
		ScryfallSet set = sets.get(jsonCard.set);

		ScryfallFace first = card.faces.computeIfAbsent(firstKind, k -> new ScryfallFace(firstKind, jsonCard, jsonCard.cardFaces.get(0)));
		ScryfallFace second = card.faces.computeIfAbsent(secondKind, k -> new ScryfallFace(secondKind, jsonCard, jsonCard.cardFaces.get(1)));

		ScryfallPrinting print = card.printings.computeIfAbsent(jsonCard.id, id -> new ScryfallPrinting(set, card, jsonCard));

		ScryfallPrintedFace firstPrint = print.faces.computeIfAbsent(firstKind, k -> new ScryfallPrintedFace(print, first, jsonCard, jsonCard.cardFaces.get(0)));
		ScryfallPrintedFace secondPrint = print.faces.computeIfAbsent(secondKind, k -> new ScryfallPrintedFace(print, second, jsonCard, jsonCard.cardFaces.get(1)));

		set.printings.put(print.id(), print);
		printings.put(print.id(), print);
	}

	private final BiFunction<emi.lib.mtg.scryfall.api.Card, emi.lib.mtg.scryfall.api.Card, ScryfallPrinting> meld = (jsonFront, jsonBack) -> {
		ScryfallCard card = cards.computeIfAbsent(CardId.of(jsonFront, jsonBack), id -> new ScryfallCard(jsonFront));
		ScryfallFace front = card.faces.computeIfAbsent(emi.lib.mtg.Card.Face.Kind.Front, k -> new ScryfallFace(jsonFront));
		ScryfallFace back = card.faces.computeIfAbsent(emi.lib.mtg.Card.Face.Kind.Transformed, k -> new ScryfallFace(jsonBack));

		ScryfallSet set = sets.get(jsonFront.set);
		ScryfallSet backSet = sets.get(jsonBack.set);

		assert set == backSet;

		ScryfallPrinting print = card.printings.computeIfAbsent(jsonFront.id, id -> new ScryfallPrinting(set, card, jsonFront));
		ScryfallPrintedFace frontPrint = print.faces.computeIfAbsent(emi.lib.mtg.Card.Face.Kind.Front, k -> new ScryfallPrintedFace(print, front, jsonFront, null));
		ScryfallPrintedFace backPrint = print.faces.computeIfAbsent(emi.lib.mtg.Card.Face.Kind.Transformed, k -> new ScryfallPrintedFace(print, back, jsonBack, null));

		set.printings.put(print.id(), print);
		printings.put(print.id(), print);

		return print;
	};

	private void initMeld(emi.lib.mtg.scryfall.api.Card jsonCard) {
		CompletableFuture<emi.lib.mtg.scryfall.api.Card> awaitOne = null, awaitOther = null, awaitBack = null;

		for (emi.lib.mtg.scryfall.api.Card.Part part : jsonCard.allParts) {
			CompletableFuture<emi.lib.mtg.scryfall.api.Card> await;

			if (part.id.equals(jsonCard.id)) {
				await = CompletableFuture.completedFuture(jsonCard);
			} else {
				await = await(part.id);
			}

			if ("meld_result".equals(part.component)) {
				awaitBack = await;
			} else if ("meld_part".equals(part.component)) {
				if (awaitOne != null) awaitOther = awaitOne;
				awaitOne = await;
			} else {
				System.err.println(String.format("Unexpected component %s in meld parts for %s; ignoring...", part.component, jsonCard.name));
			}
		}

		assert awaitOne != null && awaitOther != null && awaitBack != null : String.format("Impossible meld combination related to %s", jsonCard.name);

		CompletableFuture<ScryfallPrinting> one = awaitOne.thenCombine(awaitBack, meld),
				other = awaitOther.thenCombine(awaitBack, meld);

		one.thenAcceptBoth(other, (pr1, pr2) -> {
			assert pr1.card().face(emi.lib.mtg.Card.Face.Kind.Transformed) == pr2.card().face(emi.lib.mtg.Card.Face.Kind.Transformed);
			assert pr1.set() == pr2.set();
		});
	}

	@Override
	public void loadData(DoubleConsumer progress) throws IOException {
		this.cards.clear();
		this.printings.clear();
		this.sets.clear();

		JsonReader reader = ScryfallApi.GSON.newJsonReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(DATA_FILE)), StandardCharsets.UTF_8));
		reader.beginObject();

		expect(reader.nextName(), "sets");
		reader.beginObject();
		while (reader.peek() == JsonToken.NAME) {
			String code = reader.nextName();
			emi.lib.mtg.scryfall.api.Set set = ScryfallApi.GSON.fromJson(reader, emi.lib.mtg.scryfall.api.Set.class);
			expect(code, set.code);

			if (set.setType == SetType.Token) {
				continue;
			}

			sets.put(set.code, new ScryfallSet(set));
		}
		reader.endObject();

		ExecutorService processor = Executors.newCachedThreadPool();

		expect(reader.nextName(), "printings");
		reader.beginObject();
		expect(reader.nextName(), "count");
		final double printingCount = reader.nextLong();
		final AtomicInteger processedCount = new AtomicInteger();
		while (reader.peek() == JsonToken.NAME) {
			String id = reader.nextName();
			emi.lib.mtg.scryfall.api.Card card = ScryfallApi.GSON.fromJson(reader, emi.lib.mtg.scryfall.api.Card.class);
			expect(id, card.id.toString());

			if (card.layout == CardLayout.Token || card.layout == CardLayout.DoubleFacedToken) {
				continue;
			}

			processor.submit(() -> {
				process(card);

				if (progress != null) {
					int x = processedCount.incrementAndGet();
					if ((x & 0x1FF) == 0) {
						progress.accept(x / printingCount);
					}
				}
			});
		}
		reader.endObject();

		reader.endObject();
		reader.close();

		processor.shutdown();
		try {
			processor.awaitTermination(1, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			throw new IOException("Interrupted while processing cards", e);
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

		dataSource.loadData(null);

		System.out.println(String.format("New: %d sets, %d cards, %d printings", dataSource.sets.size(), dataSource.cards.size(), dataSource.printings.size()));

		System.in.read();

		ScryfallApi api = new ScryfallApi();
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
