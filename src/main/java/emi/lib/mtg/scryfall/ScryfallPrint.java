package emi.lib.mtg.scryfall;

import emi.lib.mtg.Card;
import emi.lib.mtg.enums.Rarity;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ScryfallPrint implements Card.Print {

	private static class OrdinaryCollectorNumber implements Comparable<OrdinaryCollectorNumber> {
		public static final Pattern PATTERN = Pattern.compile("(?<prefix>[0-9]?[A-Za-z*]+)?(?<number>[0-9]+)(?<suffix>[A-Za-z*\u2605\u2020\u2021]+)?");

		public static OrdinaryCollectorNumber of(String cn) {
			Matcher m = PATTERN.matcher(cn);
			if (!m.matches()) return null;

			String prefix = m.group("prefix");
			int n = Integer.parseInt(m.group("number"));
			String suffix = m.group("suffix");

			if (prefix == null) prefix = "";
			if (suffix == null) suffix = "";
			if ("*".equals(prefix)) {
				prefix = "";
				suffix += "*";
			}

			return new OrdinaryCollectorNumber(prefix, n, suffix);
		}

		public final String prefix;
		public final int number;
		public final String suffix;

		protected OrdinaryCollectorNumber(String prefix, int number, String suffix) {
			this.prefix = prefix;
			this.number = number;
			this.suffix = suffix;
		}

		@Override
		public int compareTo(OrdinaryCollectorNumber other) {
			if (!prefix.equals(other.prefix)) return prefix.compareTo(other.prefix);
			if (number != other.number) return Integer.compare(number, other.number);
			return suffix.compareTo(other.suffix);
		}
	}

	private final static Pattern PT_CN_PATTERN = Pattern.compile("(?<p>p)?(?<year>[0-9]{4})-(?<number>[0-9]{1,2})");
	private final static Pattern ARENA_CN_PATTERN = Pattern.compile("(?<number>[0-9]{3})-(?<name>[A-Z]+)");
	private final static Pattern LIST_PATTERN = Pattern.compile("(?<osc>[A-Za-z0-9]{3,4})-(?<ocn>" + OrdinaryCollectorNumber.PATTERN + ")");

	final static Comparator<String> COLLECTOR_NUMBER_COMPARATOR = (s1, s2) -> {
		OrdinaryCollectorNumber ocn1 = OrdinaryCollectorNumber.of(s1), ocn2 = OrdinaryCollectorNumber.of(s2);

		if (ocn1 != null && ocn2 != null) {
			return ocn1.compareTo(ocn2);
		} else if (ocn1 != null || ocn2 != null) {
			return ocn1 != null ? -1 : 1;
		}

		Matcher matcher1 = PT_CN_PATTERN.matcher(s1), matcher2 = PT_CN_PATTERN.matcher(s2);
		boolean m1 = matcher1.matches(), m2 = matcher2.matches();
		if (m1 && m2) {
			String y1 = matcher1.group("year"), y2 = matcher2.group("year");
			if (!y1.equals(y2)) return y1.compareTo(y2);
			int n1 = Integer.parseInt(matcher1.group("number")), n2 = Integer.parseInt(matcher2.group("number"));
			if (n1 != n2) return n1 - n2;

			String p1 = matcher1.group("p"), p2 = matcher2.group("p");
			if (p1 != null && p2 == null) return 1;
			if (p1 == null && p2 != null) return -1;
			return 0;
		} else if (m1 || m2) {
			return m1 ? -1 : 1;
		}

		matcher1 = ARENA_CN_PATTERN.matcher(s1);
		matcher2 = ARENA_CN_PATTERN.matcher(s2);
		m1 = matcher1.matches();
		m2 = matcher2.matches();

		if (m1 && m2) {
			int n1 = Integer.parseInt(matcher1.group("number")), n2 = Integer.parseInt(matcher2.group("number"));
			if (n1 != n2) return n1 - n2;
			return matcher1.group("name").compareTo(matcher2.group("name"));
		} else if (m1 || m2) {
			return m1 ? -1 : 1;
		}

		matcher1 = LIST_PATTERN.matcher(s1);
		matcher2 = LIST_PATTERN.matcher(s2);
		m1 = matcher1.matches();
		m2 = matcher2.matches();

		if (m1 && m2) {
			String osc1 = matcher1.group("osc"), osc2 = matcher2.group("osc");
			if (!osc1.equals(osc2)) return osc1.compareTo(osc2);

			ocn1 = OrdinaryCollectorNumber.of(matcher1.group("ocn"));
			ocn2 = OrdinaryCollectorNumber.of(matcher2.group("ocn"));
			if (ocn1 != null && ocn2 != null) {
				return ocn1.compareTo(ocn2);
			} else if (ocn1 != null || ocn2 != null) {
				return ocn1 != null ? -1 : 1;
			}
		} else if (m1 || m2) {
			return m1 ? -1 : 1;
		}

		System.err.printf("Unrecognized collector number patterns for both %s and %s!\n", s1, s2);
		return s1.compareTo(s2);
	};

	private final ScryfallSet set;
	private final ScryfallCard card;
	final emi.lib.mtg.scryfall.api.Card cardJson;

	private Set<ScryfallPrintedFace> faces, mainFaces;
	private final Map<ScryfallFace, Map<ScryfallPrintedFace, ScryfallPrintedFace>> facesDict;

	private int variation;

	ScryfallPrint(ScryfallSet set, ScryfallCard card, emi.lib.mtg.scryfall.api.Card cardJson) {
		this.set = set;
		this.card = card;
		this.cardJson = cardJson;

		this.faces = Collections.emptySet();
		this.mainFaces = Collections.emptySet();
		this.facesDict = new HashMap<>();

		this.variation = -1;
	}

	ScryfallPrintedFace addFace(ScryfallFace face, boolean back, emi.lib.mtg.enums.StandardFrame frame, emi.lib.mtg.scryfall.api.Card jsonCard, emi.lib.mtg.scryfall.api.Card.Face faceJson) {
		ScryfallPrintedFace printedFace = new ScryfallPrintedFace(this, face, back, frame, jsonCard, faceJson);
		if (facesDict.containsKey(face) && facesDict.get(face).containsKey(printedFace)) return facesDict.get(face).get(printedFace);

		faces = Util.addElem(faces, printedFace, LinkedHashSet::new);

		if (card.mainFaces().contains(face)) {
			mainFaces = Util.addElem(mainFaces, printedFace, LinkedHashSet::new);
		}

		facesDict.computeIfAbsent(face, f -> new LinkedHashMap<>()).put(printedFace, printedFace);
		return printedFace;
	}

	@Override
	public ScryfallCard card() {
		return card;
	}

	@Override
	public Set<ScryfallPrintedFace> faces() {
		return faces;
	}

	@Override
	public Set<ScryfallPrintedFace> mainFaces() {
		return mainFaces;
	}

	@Override
	public Set<ScryfallPrintedFace> faces(Card.Face face) {
		if (!(face instanceof ScryfallFace)) throw new IllegalArgumentException(String.format("%s is not a face of %s!", face, card));
		return facesDict.get(face).keySet();
	}

	@Override
	public emi.lib.mtg.Set set() {
		return set;
	}

	@Override
	public Rarity rarity() {
		return Util.mapRarity(cardJson);
	}

	@Override
	public Integer multiverseId() {
		return cardJson.multiverseIds == null || cardJson.multiverseIds.isEmpty() ? null : cardJson.multiverseIds.iterator().next();
	}

	@Override
	public int variation() {
		if (variation < 0) {
			Iterator<String> cns = card().prints().stream()
					.filter(p -> p.set == set)
					.map(Card.Print::collectorNumber)
					.sorted(COLLECTOR_NUMBER_COMPARATOR)
					.iterator();

			for (int i = 1; cns.hasNext(); ++i) {
				if (Objects.equals(cns.next(), collectorNumber())) {
					variation = i;
					break;
				}
			}

			if (variation < 0) {
				variation = 1;
			}
		}

		return variation;
	}

	@Override
	public String collectorNumber() {
		return cardJson.collectorNumber;
	}

	@Override
	public Integer mtgoCatalogId() {
		return cardJson.mtgoId;
	}

	@Override
	public boolean promo() {
		return cardJson.promo;
	}

	@Override
	public LocalDate releaseDate() {
		return cardJson.releasedAt;
	}

	@Override
	public String toString() {
		return String.format("%s (%s) %s", card.name(), set.code().toUpperCase(), collectorNumber());
	}

	@Override
	public UUID id() {
		return cardJson.id;
	}

	@Override
	public int hashCode() {
		return cardJson.id.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof ScryfallPrint && (cardJson.id.equals(((ScryfallPrint) obj).cardJson.id));
	}

	@Override
	public Treatment treatment() {
		return Treatment.None; // TODO Scryfall doesn't separate foil and nonfoil prints. For now, claim none of them are foil.
	}
}
