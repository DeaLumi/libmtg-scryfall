package emi.lib.mtg.scryfall;

import emi.lib.mtg.card.Card;
import emi.lib.mtg.card.CardFace;
import emi.lib.mtg.card.CardFaceExtended;
import emi.lib.mtg.characteristic.CardRarity;
import emi.lib.mtg.characteristic.CardTypeLine;
import emi.lib.mtg.characteristic.Color;
import emi.lib.mtg.characteristic.ManaCost;
import emi.lib.mtg.characteristic.impl.BasicCardTypeLine;
import emi.lib.mtg.characteristic.impl.BasicManaCost;
import emi.lib.mtg.data.CardSet;
import emi.lib.scryfall.PagedList;
import emi.lib.scryfall.Scryfall;
import emi.lib.scryfall.api.enums.CardLayout;

import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ScryfallSet implements CardSet {
	private static int or(Integer in, int def) {
		return in != null ? in : def;
	}

	private static String or(String in, String def) {
		return in != null ? in : def;
	}

	private static <T> Set<T> orEmpty(Set<T> in) {
		return in != null ? in : Collections.emptySet();
	}

	private static Set<Color> mapColor(Set<emi.lib.scryfall.api.enums.Color> apiColors) {
		Set<Color> out = EnumSet.noneOf(Color.class);

		for (emi.lib.scryfall.api.enums.Color color : apiColors) {
			switch (color) {
				case White:
					out.add(Color.WHITE);
					break;
				case Blue:
					out.add(Color.BLUE);
					break;
				case Black:
					out.add(Color.BLACK);
					break;
				case Red:
					out.add(Color.RED);
					break;
				case Green:
					out.add(Color.GREEN);
					break;
				default:
					assert false : "Bwuh?";
					break;
			}
		}

		return out;
	}

	private static final Pattern SYMBOL_PATTERN = Pattern.compile("\\{[WUBRG0-9/TQP]+}"); // This regex is far from perfect.

	private static Set<emi.lib.scryfall.api.enums.Color> colorsIn(String... strings) {
		Set<emi.lib.scryfall.api.enums.Color> colors = EnumSet.noneOf(emi.lib.scryfall.api.enums.Color.class);

		if (strings == null || strings.length == 0) {
			return colors;
		}

		for (String str : strings) {
			if (str == null || str.isEmpty()) {
				continue;
			}

			Matcher m = SYMBOL_PATTERN.matcher(str);

			while (m.find()) {
				for (emi.lib.scryfall.api.enums.Color color : emi.lib.scryfall.api.enums.Color.values()) {
					if (m.group().contains(color.serialized())) {
						colors.add(color);
					}
				}
			}
		}

		return colors;
	}

	public class ScryfallCard implements Card {
		public abstract class ScryfallCardFace implements CardFaceExtended {
			private final ManaCost manaCost;
			private final Set<Color> color, colorIdentity;
			private final CardTypeLine typeLine;

			public ScryfallCardFace(String manaCost, Set<emi.lib.scryfall.api.enums.Color> color, Set<emi.lib.scryfall.api.enums.Color> colorIdentity, String typeLine) {
				this.manaCost = new BasicManaCost(or(manaCost, ""));
				this.color = mapColor(orEmpty(color));
				this.colorIdentity = mapColor(orEmpty(colorIdentity));
				this.typeLine = BasicCardTypeLine.parse(or(typeLine, ""));
			}

			@Override
			public int multiverseId() {
				return or(ScryfallCard.this.source.multiverseId, -1);
			}

			@Override
			public String collectorNumber() {
				return or(ScryfallCard.this.source.collectorNumber, "");
			}

			@Override
			public Card card() {
				return ScryfallCard.this;
			}

			@Override
			public ManaCost manaCost() {
				return manaCost;
			}

			@Override
			public Set<Color> color() {
				return color;
			}

			@Override
			public Set<Color> colorIdentity() {
				return colorIdentity;
			}

			@Override
			public CardTypeLine type() {
				return typeLine;
			}
		}

		public class ScryfallCardSingleFace extends ScryfallCardFace {
			ScryfallCardSingleFace() {
				super(ScryfallCard.this.source.manaCost, ScryfallCard.this.source.colors, ScryfallCard.this.source.colorIdentity, ScryfallCard.this.source.typeLine);
			}

			@Override
			public Kind kind() {
				return CardFace.Kind.Front;
			}

			@Override
			public String name() {
				return ScryfallCard.this.source.name;
			}

			@Override
			public String text() {
				return or(ScryfallCard.this.source.oracleText, "");
			}

			@Override
			public String flavor() {
				return or(ScryfallCard.this.source.flavorText, "");
			}

			@Override
			public String power() {
				return or(ScryfallCard.this.source.power, "");
			}

			@Override
			public String toughness() {
				return or(ScryfallCard.this.source.toughness, "");
			}

			@Override
			public String loyalty() {
				return or(ScryfallCard.this.source.loyalty, "");
			}
		}

		public class ScryfallCardSplitFace extends ScryfallCardFace {
			private final CardFace.Kind kind;
			private final emi.lib.scryfall.api.Card.Face face;

			public ScryfallCardSplitFace(CardFace.Kind kind, emi.lib.scryfall.api.Card.Face face) {
				super(face.manaCost, colorsIn(face.manaCost), colorsIn(face.manaCost, face.oracleText), face.typeLine);

				this.kind = kind;
				this.face = face;
			}

			@Override
			public Kind kind() {
				return this.kind;
			}

			@Override
			public String name() {
				return this.face.name;
			}

			@Override
			public String text() {
				return or(this.face.oracleText, "");
			}

			@Override
			public String flavor() {
				return or(ScryfallCard.this.source.flavorText, "");
			}

			@Override
			public String power() {
				return or(this.face.power, "");
			}

			@Override
			public String toughness() {
				return or(this.face.toughness, "");
			}

			@Override
			public String loyalty() {
				return or(ScryfallCard.this.source.loyalty, "");
			}
		}

		public class ScryfallCardFlipFace extends ScryfallCardFace {
			private final CardFace.Kind kind;
			private final emi.lib.scryfall.api.Card.Face face;

			public ScryfallCardFlipFace(CardFace.Kind kind, emi.lib.scryfall.api.Card.Face face) {
				super(face.manaCost, ScryfallCard.this.source.colors, ScryfallCard.this.source.colorIdentity, face.typeLine);

				this.kind = kind;
				this.face = face;
			}

			@Override
			public Kind kind() {
				return this.kind;
			}

			@Override
			public String name() {
				return this.face.name;
			}

			@Override
			public String text() {
				return or(this.face.oracleText, "");
			}

			@Override
			public String flavor() {
				return or(ScryfallCard.this.source.flavorText, "");
			}

			@Override
			public String power() {
				return or(this.face.power, "");
			}

			@Override
			public String toughness() {
				return or(this.face.toughness, "");
			}

			@Override
			public String loyalty() {
				return or(ScryfallCard.this.source.loyalty, "");
			}
		}

		public class ScryfallCardPartFace extends ScryfallCardFace {
			private final CardFace.Kind kind;
			private final emi.lib.scryfall.api.Card card;

			public ScryfallCardPartFace(CardFace.Kind kind, emi.lib.scryfall.api.Card card) {
				super(card.manaCost, card.colors, card.colorIdentity, card.typeLine);

				this.kind = kind;
				this.card = card;
			}

			@Override
			public Kind kind() {
				return this.kind;
			}

			@Override
			public String name() {
				return this.card.name;
			}

			@Override
			public String text() {
				return or(this.card.oracleText, "");
			}

			@Override
			public String flavor() {
				return or(this.card.flavorText, "");
			}

			@Override
			public String power() {
				return or(this.card.power, "");
			}

			@Override
			public String toughness() {
				return or(this.card.toughness, "");
			}

			@Override
			public String loyalty() {
				return or(this.card.loyalty, "");
			}
		}

		private final emi.lib.scryfall.api.Card source;
		private final Map<CardFace.Kind, ScryfallCardFace> faces;

		public ScryfallCard(Map<URL, emi.lib.scryfall.api.Card> parts, emi.lib.scryfall.api.Card source) {
			this.source = source;

			this.faces = new EnumMap<>(CardFace.Kind.class);

			switch (source.layout) {
				case Plane:
				case Token:
				case Emblem:
				case Normal:
				case Scheme:
				case Leveler:
				case Vanguard:
				case Phenomenon:
					this.faces.put(CardFace.Kind.Front, new ScryfallCardSingleFace());
					break;

					// TODO: CLEAN THESE UP!
				case Meld: {
					this.faces.put(CardFace.Kind.Front, new ScryfallCardSingleFace());

					emi.lib.scryfall.api.Card part = parts.get(source.allParts.stream().map(p -> p.uri).filter(url -> url.toString().endsWith("b")).findAny().get());
					this.faces.put(CardFace.Kind.Transformed, new ScryfallCardPartFace(CardFace.Kind.Transformed, part));
					break;
				}

				case Transform: {
					this.faces.put(CardFace.Kind.Front, new ScryfallCardSingleFace());

					emi.lib.scryfall.api.Card part = parts.get(source.allParts.stream().map(p -> p.uri).filter(url -> url.toString().endsWith("b")).findAny().get());
					this.faces.put(CardFace.Kind.Transformed, new ScryfallCardPartFace(CardFace.Kind.Transformed, part));
					break;
				}

				case Flip:
					// N.B. We assume here these are in order... At least for flips we can search the oracle text for "[Ff]lip ~"...
					this.faces.put(CardFace.Kind.Front, new ScryfallCardFlipFace(CardFace.Kind.Front, source.cardFaces.get(0)));
					this.faces.put(CardFace.Kind.Flipped, new ScryfallCardFlipFace(CardFace.Kind.Flipped, source.cardFaces.get(1)));
					break;

				case Split:
					// N.B. We assume here these are in order... and there's no way to know what the right order is.
					this.faces.put(CardFace.Kind.Left, new ScryfallCardSplitFace(CardFace.Kind.Front, source.cardFaces.get(0)));
					this.faces.put(CardFace.Kind.Right, new ScryfallCardSplitFace(CardFace.Kind.Flipped, source.cardFaces.get(1)));
					break;

				default:
					assert false : "Bwuh?";
					break;
			}
		}

		@Override
		public CardSet set() {
			return ScryfallSet.this;
		}

		@Override
		public String name() {
			return this.source.name;
		}

		@Override
		public Set<Color> color() {
			return mapColor(this.source.colors); // TODO cache this?
		}

		@Override
		public Set<Color> colorIdentity() {
			return mapColor(this.source.colorIdentity); // TODO cache this?
		}

		@Override
		public CardRarity rarity() {
			switch (this.source.rarity) { // TODO: Cache this?
				case Common:
					if (this.source.typeLine.contains("Basic Land")) {
						return CardRarity.BasicLand;
					}

					return CardRarity.Common;
				case Uncommon:
					return CardRarity.Uncommon;
				case Rare:
					return CardRarity.Rare;
				case Mythic:
					return CardRarity.MythicRare;
				default: // TODO: Do some 'special' rarity calculation?
					assert false : "Bwuh?";
					return null;
			}
		}

		@Override
		public CardFace face(CardFace.Kind kind) {
			return faces.get(kind);
		}

		@Override
		public int variation() {
			return -1; // TODO
		}

		@Override
		public UUID id() {
			return this.source.id;
		}
	}

	private final emi.lib.scryfall.api.Set set;
	private final List<ScryfallCard> cards;

	public ScryfallSet(Scryfall api, emi.lib.scryfall.api.Set set, List<emi.lib.scryfall.api.Card> cards) {
		this.set = set;

		// Extract all transform/meld "B-sides" and store them.
		Map<URL, emi.lib.scryfall.api.Card> parts = cards.parallelStream()
				.filter(c -> c.layout == CardLayout.Transform || c.layout == CardLayout.Meld)
				.filter(c -> c.collectorNumber.endsWith("b"))
				.collect(Collectors.toMap(c -> c.scryfallUri, c -> c));

		this.cards = cards.parallelStream()
				.filter(c -> c.layout != CardLayout.Transform && c.layout != CardLayout.Meld || !c.collectorNumber.endsWith("b"))
				.map(c -> new ScryfallCard(parts, c))
				.collect(Collectors.toList());
	}

	@Override
	public String name() {
		return set.name;
	}

	@Override
	public String code() {
		return set.code;
	}

	@Override
	public Collection<? extends Card> cards() {
		return cards;
	}
}
