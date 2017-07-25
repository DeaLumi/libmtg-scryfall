
package emi.lib.mtg.scryfall;

import com.google.common.collect.BiMap;
import com.google.common.collect.EnumHashBiMap;
import com.google.common.collect.HashBiMap;
import emi.lib.mtg.characteristic.CardTypeLine;
import emi.lib.mtg.characteristic.Color;
import emi.lib.mtg.characteristic.ManaCost;
import emi.lib.mtg.characteristic.impl.BasicCardTypeLine;
import emi.lib.mtg.characteristic.impl.BasicManaCost;
import emi.lib.mtg.Card;

import java.util.*;

public class ScryfallCard implements Card {

	public abstract class Face implements Card.Face {
		private Face.Kind kind;
		private EnumSet<Color> colorIndicator, color, colorIdentity;

		public Face(Face.Kind kind) {
			this.kind = kind;
		}

		protected synchronized void initColors() {
			if (this.colorIndicator != null && this.color != null && this.colorIdentity != null) {
				return;
			}

			this.color = EnumSet.copyOf(internalColor());

			this.colorIndicator = EnumSet.copyOf(this.color);
			this.colorIndicator.removeAll(this.manaCost().color());

			this.colorIdentity = EnumSet.copyOf(internalColorIdentity());
		}

		protected abstract Set<Color> internalColor();

		protected abstract Set<Color> internalColorIdentity();

		@Override
		public Card card() {
			return ScryfallCard.this;
		}

		@Override
		public Kind kind() {
			return this.kind;
		}

		@Override
		public Set<Color> colorIndicator() {
			initColors();
			return this.colorIndicator;
		}

		@Override
		public Set<Color> color() {
			initColors();
			return this.color;
		}

		@Override
		public Set<Color> colorIdentity() {
			initColors();
			return this.colorIdentity;
		}
	}

	public class PartFace extends Face {

		private emi.lib.scryfall.api.Card card;
		private ManaCost manaCost;
		private CardTypeLine type;

		public PartFace(Card.Face.Kind kind, emi.lib.scryfall.api.Card card) {
			super(kind);

			this.card = card;

			this.card.name = Util.or(this.card.name, "");
			this.card.manaCost = Util.or(this.card.manaCost, "");
			this.card.typeLine = Util.or(this.card.typeLine, "");
			this.card.oracleText = Util.or(this.card.oracleText, "");
			this.card.power = Util.or(this.card.power, "");
			this.card.toughness = Util.or(this.card.toughness, "");
			this.card.loyalty = Util.or(this.card.loyalty, "");
			this.card.handModifier = Util.or(this.card.handModifier, "");
			this.card.lifeModifier = Util.or(this.card.lifeModifier, "");
		}

		@Override
		protected Set<Color> internalColor() {
			return Util.mapColor(this.card.colors);
		}

		@Override
		protected Set<Color> internalColorIdentity() {
			return Util.mapColor(this.card.colorIdentity);
		}

		@Override
		public String name() {
			return this.card.name;
		}

		@Override
		public ManaCost manaCost() {
			if (this.manaCost == null) {
				this.manaCost = BasicManaCost.parse(this.card.manaCost);
			}

			return this.manaCost;
		}

		@Override
		public CardTypeLine type() {
			if (this.type == null) {
				this.type = BasicCardTypeLine.parse(this.card.typeLine);
			}

			return this.type;
		}

		@Override
		public String rules() {
			return this.card.oracleText;
		}

		@Override
		public String power() {
			return this.card.power;
		}

		@Override
		public String toughness() {
			return this.card.toughness;
		}

		@Override
		public String loyalty() {
			return this.card.loyalty;
		}

		@Override
		public String handModifier() {
			return this.card.handModifier;
		}

		@Override
		public String lifeModifier() {
			return this.card.lifeModifier;
		}
	}

	public class FrontFace extends PartFace {
		public FrontFace(emi.lib.scryfall.api.Card card) {
			super(Kind.Front, card);
		}
	}

	public abstract class FaceFace extends Face {
		protected emi.lib.scryfall.api.Card.Face face;

		private CardTypeLine type;

		public FaceFace(Face.Kind kind, emi.lib.scryfall.api.Card.Face face) {
			super(kind);

			this.face = face;

			this.face.name = Util.or(this.face.name, "");
			// N.B. specifically ignore mana cost -- leave that to subclasses!
			this.face.typeLine = Util.or(this.face.typeLine, "");
			this.face.oracleText = Util.or(this.face.oracleText, "");
			this.face.power = Util.or(this.face.power, "");
			this.face.toughness = Util.or(this.face.toughness, "");
		}

		@Override
		public String name() {
			return face.name;
		}

		@Override
		public CardTypeLine type() {
			if (this.type == null) {
				this.type = BasicCardTypeLine.parse(this.face.typeLine);
			}

			return this.type;
		}

		@Override
		public String rules() {
			return this.face.oracleText;
		}

		@Override
		public String power() {
			return this.face.power;
		}

		@Override
		public String toughness() {
			return this.face.toughness;
		}

		@Override
		public String loyalty() {
			return "";
		}

		@Override
		public String handModifier() {
			return "";
		}

		@Override
		public String lifeModifier() {
			return "";
		}
	}

	public class SplitFace extends FaceFace {

		private ManaCost manaCost;

		public SplitFace(Face.Kind kind, emi.lib.scryfall.api.Card.Face face) {
			super(kind, face);

			face.manaCost = Util.or(face.manaCost, "");
		}

		@Override
		protected Set<Color> internalColor() {
			return manaCost().color();
		}

		@Override
		protected Set<Color> internalColorIdentity() {
			return this.internalColor();
		}

		@Override
		public ManaCost manaCost() {
			if (this.manaCost == null) {
				this.manaCost = BasicManaCost.parse(face.manaCost);
			}

			return this.manaCost;
		}
	}

	// Right now this is identical to SplitFace... I may combine them later?
	public class UnflippedFace extends FaceFace {

		private ManaCost manaCost;

		public UnflippedFace(emi.lib.scryfall.api.Card.Face face) {
			super(Face.Kind.Front, face);

			this.face.manaCost = Util.or(this.face.manaCost, "");
		}

		@Override
		protected Set<Color> internalColor() {
			return this.manaCost().color();
		}

		@Override
		protected Set<Color> internalColorIdentity() {
			return this.internalColor(); // TODO: Do any flip cards have weird color identities?
		}

		@Override
		public ManaCost manaCost() {
			if (this.manaCost == null) {
				this.manaCost = BasicManaCost.parse(this.face.manaCost);
			}

			return this.manaCost;
		}
	}

	public class FlippedFace extends FaceFace {

		private UnflippedFace upFace;

		public FlippedFace(UnflippedFace upFace, emi.lib.scryfall.api.Card.Face flipFace) {
			super(Kind.Flipped, flipFace);

			this.upFace = upFace;
		}

		@Override
		protected Set<Color> internalColor() {
			return upFace.internalColor();
		}

		@Override
		protected Set<Color> internalColorIdentity() {
			return upFace.internalColorIdentity();
		}

		@Override
		public ManaCost manaCost() {
			return upFace.manaCost();
		}
	}

	private emi.lib.scryfall.api.Card source;
	private BiMap<Face.Kind, ScryfallCard.Face> faces;
	BiMap<UUID, ScryfallPrinting> printings;

	public ScryfallCard(Map<String, emi.lib.scryfall.api.Card> bSides, emi.lib.scryfall.api.Card source) {
		this.source = source;
		this.faces = EnumHashBiMap.create(Face.Kind.class);
		this.printings = HashBiMap.create();

		switch (source.layout) {
			case Normal: {
				// TODO: Oh my god this is all so gross.
				if (source.cardFaces != null && !source.cardFaces.isEmpty()) {
					// Right now, the only card that trips this behavior is Curse of the Fire Penguin.
					if ("Curse of the Fire Penguin // ???".equals(source.name)) {
						this.faces.put(Face.Kind.Front, new SplitFace(Face.Kind.Front, source.cardFaces.get(0)));
						this.faces.put(Face.Kind.Flipped, new SplitFace(Face.Kind.Flipped, source.cardFaces.get(1)));
					} else {
						System.err.println("Add support for fancy split/flip card " + source.name);
					}
				} else if (source.allParts != null && source.allParts.size() > 1) {
					if ("Who".equals(source.name) || "What".equals(source.name) || "When".equals(source.name) || "Where".equals(source.name) || "Why".equals(source.name)) {
						// oh god
						System.err.println("I'm not supporting Who // What // When // Where // Why. I simply won't.");
						this.faces.put(Face.Kind.Front, new FrontFace(source));
					} else if ("B.F.M. (Big Furry Monster)".equals(source.name)) {
						// eh
						this.faces.put(Face.Kind.Front, new FrontFace(source));
					} else if (source.allParts.size() == 2) {
						if (source.allParts.stream().filter(p -> !source.name.equals(p.name)).findAny().get().uri.getPath().matches("/cards/t[a-z]{3}/[0-9]+")) {
							// eh
							this.faces.put(Face.Kind.Front, new FrontFace(source));
						} else {
							System.err.println("Add support for fancy multipart card " + source.name);
							this.faces.put(Face.Kind.Front, new FrontFace(source));
						}
					} else {
						System.err.println("Add support for fancy multipart card " + source.name);
						this.faces.put(Face.Kind.Front, new FrontFace(source));
					}
				} else {
					this.faces.put(Face.Kind.Front, new FrontFace(source));
				}
				break;
			}

			case Plane:
			case Token:
			case Emblem:
			case Scheme:
			case Leveler:
			case Vanguard:
			case Phenomenon:
				this.faces.put(Face.Kind.Front, new FrontFace(source));
				break;

			case Meld: {
				this.faces.put(Face.Kind.Front, new FrontFace(source));

				// TODO: There needs to be a nicer way to find the melded card (the 'b-half'). We could just try both other cards, since the 'a-halves' don't get stored in parts.
				emi.lib.scryfall.api.Card part = bSides.get(source.allParts.stream().filter(p -> p.uri.toString().endsWith("b")).findAny().get().name);
				this.faces.put(Face.Kind.Transformed, new PartFace(Face.Kind.Transformed, part));
				break;
			}

			case Transform: {
				this.faces.put(Face.Kind.Front, new FrontFace(source));

				emi.lib.scryfall.api.Card part = bSides.get(source.allParts.stream().filter(p -> !source.name.equals(p.name)).findAny().get().name);
				this.faces.put(Face.Kind.Transformed, new PartFace(Face.Kind.Transformed, part));
				break;
			}

			case Flip:
				// N.B. We assume here these are in order... At least for flips we can search the oracle text for "[Ff]lip ~"...
				UnflippedFace upright = new UnflippedFace(source.cardFaces.get(0));
				this.faces.put(Face.Kind.Front, upright);
				this.faces.put(Face.Kind.Flipped, new FlippedFace(upright, source.cardFaces.get(1)));
				break;

			case Split:
				// N.B. We assume here these are in order... and there's no way to know what the right order is.
				this.faces.put(Face.Kind.Left, new SplitFace(Face.Kind.Left, source.cardFaces.get(0)));
				this.faces.put(Face.Kind.Right, new SplitFace(Face.Kind.Right, source.cardFaces.get(1)));
				break;

			default:
				assert false : "Bwuh?";
				break;
		}
	}

	@Override
	public Set<ScryfallCard.Face> faces() {
		return faces.values();
	}

	@Override
	public Face face(Face.Kind kind) {
		return faces.get(kind);
	}

	@Override
	public Set<? extends Printing> printings() {
		return printings.values();
	}

	@Override
	public Printing printing(UUID id) {
		return printings.get(id);
	}
}