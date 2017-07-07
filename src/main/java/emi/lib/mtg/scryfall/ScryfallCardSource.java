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
import emi.lib.scryfall.api.enums.Rarity;

import java.io.*;
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

	private static int or(Integer i) {
		return i != null ? i : -1;
	}

	private static String or(String s) {
		return s != null ? s : "";
	}

	private class ScryfallSet implements CardSet {
		private class ScryfallCard implements Card {
			private class ScryfallFrontFace implements CardFaceExtended {
				private ManaCost manaCost;
				private java.util.Set<Color> color, colorIdentity;
				private CardTypeLine typeLine;

				@Override
				public int multiverseId() {
					return or(backingCard.multiverseId);
				}

				@Override
				public String collectorNumber() {
					return or(backingCard.collectorNumber);
				}

				@Override
				public Card card() {
					return ScryfallCard.this;
				}

				@Override
				public Kind kind() {
					return Kind.Front;
				}

				@Override
				public String name() {
					return or(backingCard.name);
				}

				@Override
				public ManaCost manaCost() {
					if (this.manaCost == null) {
						this.manaCost = new BasicManaCost(backingCard.manaCost);
					}

					return this.manaCost;
				}

				@Override
				public java.util.Set<Color> color() {
					return null;
				}

				@Override
				public java.util.Set<Color> colorIdentity() {
					return null;
				}

				@Override
				public CardTypeLine type() {
					return null;
				}

				@Override
				public String text() {
					return null;
				}

				@Override
				public String flavor() {
					return null;
				}

				@Override
				public String power() {
					return null;
				}

				@Override
				public String toughness() {
					return null;
				}

				@Override
				public String loyalty() {
					return null;
				}
			}

			private class ScryfallCardFace implements CardFaceExtended {
				private final Kind kind;
				private final int index;

				private ManaCost manaCost;
				private java.util.Set<Color> colors, colorIdentity;
				private CardTypeLine type;

				public ScryfallCardFace(Kind kind, int index) {
					this.kind = kind;
					this.index = index;
				}

				@Override
				public int multiverseId() {
					return backingCard.multiverseId != null ? backingCard.multiverseId : -1;
				}

				@Override
				public String collectorNumber() {
					return backingCard.collectorNumber != null ? backingCard.collectorNumber : "";
				}

				@Override
				public Card card() {
					return ScryfallCard.this;
				}

				@Override
				public Kind kind() {
					return kind;
				}

				@Override
				public String name() {
					return backingCard.cardFaces.get(index).name;
				}

				@Override
				public ManaCost manaCost() {
					if (manaCost == null) {
						if (backingCard.cardFaces.get(index).manaCost == null) {
							manaCost = new BasicManaCost("");
						} else {
							manaCost = new BasicManaCost(backingCard.cardFaces.get(index).manaCost);
						}
					}

					return manaCost;
				}

				@Override
				public java.util.Set<Color> color() {
					if (this.colors == null) {
						this.colors = new HashSet<>(manaCost().colors());
					}

					return colors;
				}

				@Override
				public java.util.Set<Color> colorIdentity() {
					if (colorIdentity == null) {
						colorIdentity = color(); // TODO this is not guaranteed
					}

					return colorIdentity;
				}

				@Override
				public CardTypeLine type() {
					if (type == null) {
						type = BasicCardTypeLine.parse(backingCard.cardFaces.get(index).type);
					}

					return type;
				}

				@Override
				public String text() {
					return backingCard.cardFaces.get(index).oracleText != null ? backingCard.cardFaces.get(index).oracleText : "";
				}

				@Override
				public String flavor() {
					return kind == Kind.Front && backingCard.flavorText != null ? backingCard.flavorText : "";
				}

				@Override
				public String power() {
					return backingCard.cardFaces.get(index).power != null ? backingCard.cardFaces.get(index).power : "";
				}

				@Override
				public String toughness() {
					return backingCard.cardFaces.get(index).toughness != null ? backingCard.cardFaces.get(index).toughness : "";
				}

				@Override
				public String loyalty() {
					return kind == Kind.Front ? backingCard.loyalty : "";
				}
			}

			private emi.lib.scryfall.api.Card backingCard;
			private Map<CardFace.Kind, CardFaceExtended> faces;

			public ScryfallCard(emi.lib.scryfall.api.Card backingCard) {
				this.backingCard = backingCard;

				this.faces = new HashMap<>();

				switch (backingCard.layout) {
					case Split:
					case Flip:
						assert backingCard.cardFaces != null && backingCard.cardFaces.size() == 2;

						break;
					case Transform:
					case Meld:
						break;
					case Phenomenon:
					case Leveler:
					case Normal:
					case Plane:
					case Scheme:
					case Vanguard:
						assert backingCard.cardFaces == null;
						break;
				}
			}

			@Override
			public CardSet set() {
				return ScryfallSet.this;
			}

			@Override
			public CardRarity rarity() {
				switch (backingCard.rarity) {
					case Common:
						if (front().type().supertypes().contains(Supertype.Basic)) {
							return CardRarity.BasicLand;
						} else {
							return CardRarity.Common;
						}
					case Uncommon:
						return CardRarity.Uncommon;
					case Rare:
						return CardRarity.Rare;
					case Mythic:
						return CardRarity.MythicRare;
					default:
						throw new Error("Unknown Scryfall card rarity " + backingCard.rarity);
				}
			}

			@Override
			public CardFace face(CardFace.Kind kind) {
				return null;
			}

			@Override
			public String name() {
				return backingCard.name;
			}

			@Override
			public java.util.Set<Color> color() {
				return null;
			}

			@Override
			public java.util.Set<Color> colorIdentity() {
				return null;
			}

			@Override
			public CardFace front() {
				return null;
			}

			@Override
			public UUID id() {
				return null;
			}

			@Override
			public int variation() {
				return 0;
			}
		}

		private Set backingSet;

		public ScryfallSet(Set backingSet) {
			this.backingSet = backingSet;
		}

		@Override
		public String name() {
			return backingSet.name;
		}

		@Override
		public String code() {
			return backingSet.code;
		}

		@Override
		public Collection<? extends Card> cards() {
			return null; // TODO implement me!
		}
	}

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
