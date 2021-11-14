package emi.lib.mtg.scryfall;

import emi.lib.mtg.Card;
import emi.lib.mtg.enums.Rarity;
import emi.lib.mtg.scryfall.util.MirrorMap;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ScryfallPrinting implements Card.Printing {

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

		System.err.printf("Unrecognized collector number patterns for both %s and %s!\n", s1, s2);
		return s1.compareTo(s2);
	};

	private final ScryfallSet set;
	private final ScryfallCard card;
	final emi.lib.mtg.scryfall.api.Card cardJson;

	final MirrorMap<Card.Face.Kind, ScryfallPrintedFace> faces;

	private int variation;

	ScryfallPrinting(ScryfallSet set, ScryfallCard card, emi.lib.mtg.scryfall.api.Card cardJson) {
		this.set = set;
		this.card = card;
		this.cardJson = cardJson;

		this.faces = new MirrorMap<>(() -> new EnumMap<>(Card.Face.Kind.class));

		this.variation = -1;
	}

	@Override
	public ScryfallCard card() {
		return card;
	}

	@Override
	public Set<ScryfallPrintedFace> faces() {
		return faces.valueSet();
	}

	@Override
	public ScryfallPrintedFace face(Card.Face.Kind kind) {
		return faces.get(kind);
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
			Iterator<String> cns = set.printings().stream()
					.filter(print -> print.card().name().equals(this.card().name()))
					.map(Card.Printing::collectorNumber)
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
}
