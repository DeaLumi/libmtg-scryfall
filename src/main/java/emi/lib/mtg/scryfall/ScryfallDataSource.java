package emi.lib.mtg.scryfall;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import emi.lib.mtg.Card;
import emi.lib.mtg.DataSource;
import emi.lib.mtg.enums.StandardFrame;
import emi.lib.mtg.scryfall.api.ScryfallApi;
import emi.lib.mtg.scryfall.api.Catalog;
import emi.lib.mtg.scryfall.api.enums.CardFrame;
import emi.lib.mtg.scryfall.api.enums.CardLayout;
import emi.lib.mtg.scryfall.api.enums.GameFormat;
import emi.lib.mtg.scryfall.api.enums.SetType;
import emi.lib.mtg.scryfall.util.CardId;
import emi.lib.mtg.scryfall.util.MirrorMap;
import emi.mtg.deckbuilder.util.PluginUtils;

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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.DoubleConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class ScryfallDataSource implements DataSource {
	private static final long UPDATE_INTERVAL = 7 * 24 * 60 * 60 * 1000;

	private static final Collection<GameFormat> DROPPED_FORMATS = Arrays.asList(
			GameFormat.Duel,
			GameFormat.OldSchool,
			GameFormat.Gladiator,
			GameFormat.Premodern,
			GameFormat.Unrecognized);

	private static final Set<String> HORDE_SETS = hordeSets();

	private static Set<String> hordeSets() {
		Set<String> tmp = new HashSet<>();
		tmp.add("tbth");
		tmp.add("tfth");
		tmp.add("tdag");
		return Collections.unmodifiableSet(tmp);
	}

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
		return sets.get(code.toLowerCase());
	}

	private Path dataFile(Path dataDir) {
		return dataDir.resolve("scryfall-data.json.gz");
	}

	@Override
	public boolean update(Path dataDir, DoubleConsumer progress) throws IOException {
		ScryfallApi api = ScryfallApi.get();
		List<emi.lib.mtg.scryfall.api.Set> sets = api.sets();
		Set<String> droppedSets = new HashSet<>();

		Path tmp = Files.createTempFile("scryfall-data", ".json.gz");
		JsonWriter writer = ScryfallApi.GSON.newJsonWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(tmp)), StandardCharsets.UTF_8));

		writer.beginObject();

		writer.name("sets");
		writer.beginObject();
		for (emi.lib.mtg.scryfall.api.Set set : sets) {
			if (set.setType == SetType.Token && !HORDE_SETS.contains(set.code.toLowerCase())) {
				droppedSets.add(set.code);
				continue;
			}

			writer.name(set.code);
			ScryfallApi.GSON.toJson(set, emi.lib.mtg.scryfall.api.Set.class, writer);
		}
		writer.endObject();

		List<emi.lib.mtg.scryfall.api.Card> cards = api.defaultCardsBulk(d -> progress.accept(0.5 * d));

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
				progress.accept(0.5 + 0.5 * (double) statusCounter / (double) cards.size());
			}
		}
		writer.endObject();

		writer.endObject();
		writer.close();

		Files.copy(tmp, dataFile(dataDir), StandardCopyOption.REPLACE_EXISTING);
		Files.delete(tmp);

		System.gc();
		System.gc();

		return true;
	}

	@Override
	public boolean needsUpdate(Path dataDir) {
		try {
			Instant ref = Instant.now().minusMillis(UPDATE_INTERVAL);
			Instant version = Files.getLastModifiedTime(PluginUtils.jarPath(ScryfallDataSource.class)).toInstant();
			if (ref.isBefore(version)) {
				ref = version;
			}

			return !Files.exists(dataFile(dataDir)) ||
					ref.isAfter(Files.getLastModifiedTime(dataFile(dataDir)).toInstant());
		} catch (IOException ioe) {
			System.err.println(String.format("Unable to check %s for freshness -- please update Scryfall data.", dataFile(dataDir).toString()));
			ioe.printStackTrace();
			return true;
		}
	}

	private final Map<UUID, CompletableFuture<emi.lib.mtg.scryfall.api.Card>> await = new Hashtable<>();

	private CompletableFuture<emi.lib.mtg.scryfall.api.Card> await(UUID id) {
		return await.computeIfAbsent(id, x -> new CompletableFuture<>());
	}

	protected void process(emi.lib.mtg.scryfall.api.Card card) {
		CompletableFuture<emi.lib.mtg.scryfall.api.Card> pending = await.get(card.id);

		if (pending != null) {
			if (!pending.complete(card)) System.err.printf("!? We already completed meld for %s!?%n", card.name);
			return;
		}

		if ("Who // What // When // Where // Why".equals(card.name)) {
			createWhoWhatWhenWhereWhy(card);
			return;
		}

		if ("Smelt // Herd // Saw".equals(card.name)) {
			card.typeLine = card.typeLine.replaceAll(" [/][/] ", " ");
			card.manaCost = card.manaCost.replaceAll(" [/][/] ", "");
			createSimple(card);
			return;
		}

		switch (card.layout) {
			default:
			case Unrecognized:
				System.err.printf("Warning: Unrecognized frame type for card %s. Treating as a simple card.%n", card.name);
				// Intentional fallthrough
			case Normal:
			case Augment:
			case Host:
			case Leveler:
			case Planar:
			case Scheme:
			case Vanguard:
			case Saga:
			case Class:
			case Mutate:
			case Prototype:
			case Case:
				createSimple(card);
				return;
			case ReversibleCard:
				createReversible(card);
				return;
			case Split:
			case Flip:
			case Transform:
			case ModalDFC:
			case Adventure:
				createTwoFace(card);
				return;
			case Meld:
				initMeld(card);
				return;
			case Token:
			case DoubleFacedToken:
			case Emblem:
				System.err.printf("Unexpected token or emblem %s in set %s (%s)%n", card.name, card.setName, card.set);
				return;
		}
	}

	private static final String[] W5 = { "Who", "What", "When", "Where", "Why" };
	private static final Map<String, Integer> W5_MAP = IntStream.range(0, W5.length).collect(HashMap::new, (m, i) -> m.put(W5[i], i), HashMap::putAll);
	private static final Map<String, StandardFrame> W5_FRAMES_NEW, W5_FRAMES_OLD;

	static {
		Map<String, StandardFrame> newTmp = new HashMap<>(), oldTmp = new HashMap<>();

		newTmp.put("Who", StandardFrame.NewWho);
		newTmp.put("What", StandardFrame.NewWhat);
		newTmp.put("When", StandardFrame.NewWhen);
		newTmp.put("Where", StandardFrame.NewWhere);
		newTmp.put("Why", StandardFrame.NewWhy);

		oldTmp.put("Who", StandardFrame.OldWho);
		oldTmp.put("What", StandardFrame.OldWhat);
		oldTmp.put("When", StandardFrame.OldWhen);
		oldTmp.put("Where", StandardFrame.OldWhere);
		oldTmp.put("Why", StandardFrame.OldWhy);

		W5_FRAMES_NEW = Collections.unmodifiableMap(newTmp);
		W5_FRAMES_OLD = Collections.unmodifiableMap(oldTmp);
	}

	private void createWhoWhatWhenWhereWhy(emi.lib.mtg.scryfall.api.Card jsonCard) {
		ScryfallSet set = sets.get(jsonCard.set);

		ScryfallCard card = cards.computeIfAbsent(CardId.of(jsonCard), id -> new ScryfallCard(jsonCard));

		jsonCard.cardFaces.sort(Comparator.comparingInt(a -> W5_MAP.get(a.name)));
		List<ScryfallFace> faces = jsonCard.cardFaces.stream().map(jf -> card.addFace(jsonCard, jf, true)).collect(Collectors.toList());

		boolean old = jsonCard.frame == CardFrame.Old1993 || jsonCard.frame == CardFrame.Old1997 || jsonCard.frame == CardFrame.Modern2001 || jsonCard.frame == CardFrame.Modern2003;
		ScryfallPrinting print = card.addPrinting(set, jsonCard);
		faces.stream().forEachOrdered(f -> print.addFace(f, false, (old ? W5_FRAMES_OLD : W5_FRAMES_NEW).get(f.name()), jsonCard, f.faceJson));
		printings.put(print.id(), print);
	}

	private void createSimple(emi.lib.mtg.scryfall.api.Card jsonCard) {
		ScryfallSet set = sets.get(jsonCard.set);
		if (set == null) {
			System.err.printf("Skipping %s %s %s as the ScryfallSet is null...\n", jsonCard.name, jsonCard.set, jsonCard.collectorNumber);
			return;
		}

		ScryfallCard card = cards.computeIfAbsent(CardId.of(jsonCard), id -> new ScryfallCard(jsonCard));
		ScryfallFace front = card.addFace(jsonCard, true);

		ScryfallPrinting print = card.addPrinting(set, jsonCard);
		ScryfallPrintedFace frontPrint = print.addFace(front, false, isSideways(jsonCard.typeLine) ? StandardFrame.SidewaysFullFace : StandardFrame.FullFace, jsonCard, null);

		set.printings.put(print.id(), print);
		set.printingsByCn.put(print.collectorNumber(), print);
		printings.put(print.id(), print);
	}

	private void createReversible(emi.lib.mtg.scryfall.api.Card jsonCard) {
		ScryfallSet set = sets.get(jsonCard.set);

		ScryfallCard card = cards.computeIfAbsent(CardId.of(jsonCard.cardFaces.get(0)), id -> new ScryfallCard(jsonCard));
		ScryfallFace front = card.addFace(jsonCard, jsonCard.cardFaces.get(0), true);

		ScryfallPrinting print = card.addPrinting(set, jsonCard);
		ScryfallPrintedFace frontPrint = print.addFace(front, false, StandardFrame.FullFace, jsonCard, jsonCard.cardFaces.get(0));
		ScryfallPrintedFace backPrint = print.addFace(front, true, StandardFrame.FullFace, jsonCard, jsonCard.cardFaces.get(1));

		set.printings.put(print.id(), print);
		set.printingsByCn.put(print.collectorNumber(), print);
		printings.put(print.id(), print);
	}

	private static boolean isSideways(String typeLine) {
		return typeLine.contains("Battle") || typeLine.contains("Phenomenon") || typeLine.contains("Plane ");
	}

	private void createTwoFace(emi.lib.mtg.scryfall.api.Card jsonCard) {
		ScryfallSet set = sets.get(jsonCard.set);
		ScryfallCard card = cards.computeIfAbsent(CardId.of(jsonCard.cardFaces.get(0), jsonCard.cardFaces.get(1)), id -> new ScryfallCard(jsonCard));

		boolean back;
		StandardFrame firstFrame, secondFrame;

		ScryfallFace first, second;

		// TODO: Cases here shouldn't throw. It's just a visual bug. Print an error in the logs and choose a reasonable default.
		switch (jsonCard.layout) {
			case Transform:
				firstFrame = isSideways(jsonCard.cardFaces.get(0).typeLine) ? StandardFrame.SidewaysFullFace : StandardFrame.FullFace;
				secondFrame = isSideways(jsonCard.cardFaces.get(1).typeLine) ? StandardFrame.SidewaysFullFace : StandardFrame.FullFace;
				first = card.addFace(jsonCard, jsonCard.cardFaces.get(0), true);
				second = card.addTransformedFace(first, jsonCard, jsonCard.cardFaces.get(1));
				back = true;
				break;
			case ModalDFC:
				firstFrame = isSideways(jsonCard.cardFaces.get(0).typeLine) ? StandardFrame.SidewaysFullFace : StandardFrame.FullFace;
				secondFrame = isSideways(jsonCard.cardFaces.get(1).typeLine) ? StandardFrame.SidewaysFullFace : StandardFrame.FullFace;
				first = card.addFace(jsonCard, jsonCard.cardFaces.get(0), true);
				second = card.addFace(jsonCard, jsonCard.cardFaces.get(1), false);
				back = true;
				break;
			case Split:
				back = false;
				if (jsonCard.cardFaces.get(1).oracleText.startsWith("Aftermath")) {
					firstFrame = StandardFrame.AftermathTop;
					secondFrame = StandardFrame.AftermathBottom;
				} else {
					switch (jsonCard.frame) {
						case Khans2015:
							firstFrame = StandardFrame.SplitLeftModern;
							secondFrame = StandardFrame.SplitRightModern;
							break;
						case Modern2001:
						case Modern2003:
						case Old1997:
							firstFrame = StandardFrame.SplitLeftFull;
							secondFrame = StandardFrame.SplitRightFull;
							break;
						default:
							throw new IllegalArgumentException("Card layout is " + jsonCard.layout + ", but frame is " + jsonCard.frame);
					}
				}
				first = card.addFace(jsonCard, jsonCard.cardFaces.get(0), true);
				second = card.addFace(jsonCard, jsonCard.cardFaces.get(1), true);
				break;
			case Flip:
				back = false;
				if (jsonCard.name.contains("Curse of the Fire Penguin")) {
					firstFrame = StandardFrame.FirePenguinTop;
					secondFrame = StandardFrame.FirePenguinBottom;
				} else {
					switch (jsonCard.frame) {
						case Khans2015:
							firstFrame = StandardFrame.FlipTopModern;
							secondFrame = StandardFrame.FlipBottomModern;
							break;
						case Modern2001:
						case Modern2003:
							firstFrame = StandardFrame.FlipTopFull;
							secondFrame = StandardFrame.FlipBottomFull;
							break;
						default:
							throw new IllegalArgumentException("Card layout is " + jsonCard.layout + ", but frame is " + jsonCard.frame);
					}
				}
				first = card.addFace(jsonCard, jsonCard.cardFaces.get(0), true);
				second = card.addFlippedFace(first, jsonCard, jsonCard.cardFaces.get(1));
				break;
			case Adventure:
				back = false;
				firstFrame = StandardFrame.FullFace;
				secondFrame = StandardFrame.Adventure;
				first = card.addFace(jsonCard, jsonCard.cardFaces.get(0), true);
				second = card.addFace(jsonCard, jsonCard.cardFaces.get(1), false);
				break;
			default:
				// TODO Well, except this one. This one should probably throw.
				throw new IllegalArgumentException("createTwoFace() called with unexpected card layout " + jsonCard.layout);
		}

		ScryfallPrinting print = card.addPrinting(set, jsonCard);

		ScryfallPrintedFace firstPrint = print.addFace(first, false, firstFrame, jsonCard, jsonCard.cardFaces.get(0));
		ScryfallPrintedFace secondPrint = print.addFace(second, back, secondFrame, jsonCard, jsonCard.cardFaces.get(1));

		set.printings.put(print.id(), print);
		set.printingsByCn.put(print.collectorNumber(), print);
		printings.put(print.id(), print);
	}

	private final BiFunction<emi.lib.mtg.scryfall.api.Card, emi.lib.mtg.scryfall.api.Card, ScryfallPrinting> meld = (jsonFront, jsonBack) -> {
		ScryfallCard card = cards.computeIfAbsent(CardId.of(jsonFront, jsonBack), id -> new ScryfallCard(jsonFront));
		ScryfallFace front = card.addFace(jsonFront, true);
		ScryfallFace back = card.addTransformedFace(front, jsonBack, null);

		ScryfallSet set = sets.get(jsonFront.set);
		ScryfallSet backSet = sets.get(jsonBack.set);

		if (set != backSet) throw new IllegalStateException(String.format("Attempt to construct meld printing from two different sets %s and %s", set, backSet));

		ScryfallPrinting print = card.addPrinting(set, jsonFront);
		ScryfallPrintedFace frontPrint = print.addFace(front, false, StandardFrame.FullFace, jsonFront, null);
		ScryfallPrintedFace backPrint = print.addFace(back, true, StandardFrame.Meld, jsonBack, null);

		set.printings.put(print.id(), print);
		set.printingsByCn.put(print.collectorNumber(), print);
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
				System.err.printf("Unexpected component %s in meld parts for %s; ignoring...%n", part.component, jsonCard.name);
			}
		}

		assert awaitOne != null && awaitOther != null && awaitBack != null : String.format("Impossible meld combination related to %s", jsonCard.name);

		CompletableFuture<ScryfallPrinting> one = awaitOne.thenCombine(awaitBack, meld),
				other = awaitOther.thenCombine(awaitBack, meld);

		one.thenAcceptBoth(other, (pr1, pr2) -> {
			if (pr1.set() != pr2.set()) throw new IllegalStateException(String.format("Melded mismatched sets %s and %s!", pr1.set(), pr2.set()));

			for (ScryfallFace face : pr1.card().faces()) {
				if (pr1.card().mainFaces().contains(face)) continue;
				if (!pr2.card().faces().contains(face)) throw new IllegalStateException(String.format("Meld part %s contains face %s not in meld part %s's faces!", pr1, face, pr2));
			}
		}).handle((nothing, exc) -> {
			exc.printStackTrace();
			throw new CompletionException(exc);
		});
	}

	@Override
	public boolean loadData(Path dataDir, DoubleConsumer progress) throws IOException {
		this.cards.clear();
		this.printings.clear();
		this.sets.clear();

		JsonReader reader = ScryfallApi.GSON.newJsonReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(dataFile(dataDir))), StandardCharsets.UTF_8));
		reader.beginObject();

		expect(reader.nextName(), "sets");
		reader.beginObject();
		while (reader.peek() == JsonToken.NAME) {
			String code = reader.nextName();
			emi.lib.mtg.scryfall.api.Set set = ScryfallApi.GSON.fromJson(reader, emi.lib.mtg.scryfall.api.Set.class);
			expect(code, set.code);

			if (set.setType == SetType.Token && !HORDE_SETS.contains(set.code.toLowerCase())) {
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
				try {
					process(card);
				} catch (Exception e) {
					System.err.printf("When processing %s (%s) %s:%n", card.name, card.set, card.collectorNumber);
					e.printStackTrace();
					throw e;
				}

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
			if (!processor.awaitTermination(1, TimeUnit.MINUTES)) {
				throw new IOException("Unable to process all cards!");
			}
		} catch (InterruptedException e) {
			throw new IOException("Interrupted while processing cards", e);
		}

		return true;
	}

	public static void main(String[] args) throws IOException {
		Path wd = Paths.get(".");

		long start = System.nanoTime();
		ScryfallDataSource dataSource = new ScryfallDataSource();

		if (dataSource.needsUpdate(wd)) {
			dataSource.update(wd, x -> System.out.printf("\rUpdating data: %.2f", x * 100.0));
		}

		DoubleConsumer loadProfiler = pct -> {
			long max = Runtime.getRuntime().maxMemory();
			long curLimit = Runtime.getRuntime().totalMemory();
			long free = Runtime.getRuntime().freeMemory();
			long used = curLimit - free;

			System.out.printf("%.2f%%: max = %.3f MB, curLimit = %.3f MB, used = %.3f MB\n", pct * 100.0, max / 1024.0 / 1024.0, curLimit / 1024.0 / 1024.0, used / 1024.0 / 1024.0);
		};

		System.out.println(String.format("New: %.2f seconds", (System.nanoTime() - start) / 1e9));

		System.out.println("Begin loadData()");
		start = System.nanoTime();
		System.gc();
		System.gc();
		loadProfiler.accept(-0.01);
		dataSource.loadData(wd, loadProfiler);
		System.gc();
		System.gc();
		loadProfiler.accept(1.01);
		System.out.println(String.format("loadData() took %.2f seconds", (System.nanoTime() - start) / 1e9));

		System.out.println(String.format("New: %d sets, %d cards, %d printings", dataSource.sets.size(), dataSource.cards.size(), dataSource.printings.size()));

		System.in.read();

		System.out.println("Checking cards for bad data...");

		for (Card.Printing pr : dataSource.printings()) {
			if (pr.set() == null) {
				System.out.printf("Card %s printing {%s} has no associated set!\n", pr.card().fullName(), pr.id());
			}
			if (pr.rarity() == null) {
				System.out.printf("Card %s (%s printing) has no rarity!%n", pr.card().fullName(), pr.set().name());
			}
			if (pr.collectorNumber() == null || pr.collectorNumber().isEmpty()) {
				System.out.printf("Card %s printing %s is missing a collector number.\n", pr.card().fullName(), pr.set().code());
			}
		}

		for (emi.lib.mtg.Set set : dataSource.sets()) {
			HashMap<String, Card.Printing> cns = new HashMap<>();
			for (Card.Printing pr : set.printings()) {
				if (cns.containsKey(pr.collectorNumber())) {
					System.out.printf("! Set %s printing %s has the same collector number as %s!\n", set, pr, cns.get(pr.collectorNumber()));
				} else {
					cns.put(pr.collectorNumber(), pr);
				}
			}
		}

		for (emi.lib.mtg.Card card : dataSource.cards()) {
			HashMap<String, Card.Printing> cns = new HashMap<>();
			for (Card.Printing pr : card.printings()) {
				if (pr.set() == null) continue;

				if (cns.containsKey(pr.set().code() + " " + pr.collectorNumber())) {
					System.out.printf("! Card %s printing %s has the same collector number as %s!\n", card, pr, cns.get(pr.set().code() + " " + pr.collectorNumber()));
				} else {
					cns.put(pr.set().code() + " " + pr.collectorNumber(), pr);
				}
			}
		}

		ScryfallApi api = new ScryfallApi();
		Catalog cardNames = api.requestJson(new URL("https://api.scryfall.com/catalog/card-names"), Catalog.class);
		System.out.printf("%d cards%n", cardNames.data.size());

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
