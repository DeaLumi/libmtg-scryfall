
package emi.lib.mtg.scryfall.v2;

import emi.lib.mtg.characteristic.CardTypeLine;
import emi.lib.mtg.characteristic.Color;
import emi.lib.mtg.characteristic.ManaCost;
import emi.lib.mtg.characteristic.impl.BasicManaCost;
import emi.lib.mtg.v2.Card;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

public class ScryfallCard implements Card {
	private static int or(Integer in, int def) {
		return in != null ? in : def;
	}

	private static String or(String in, String def) {
		return in != null ? in : def;
	}

	private static <T> Set<T> orEmpty(Set<T> in) {
		return in != null ? in : Collections.emptySet();
	}

	private static EnumSet<Color> mapColor(Set<emi.lib.scryfall.api.enums.Color> apiColors) {
		EnumSet<Color> out = EnumSet.noneOf(Color.class);

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

	public abstract class Face implements Card.Face {

	}

	public class FrontFace extends Face {

		private ManaCost manaCost;
		private EnumSet<Color> colorIndicator, color, colorIdentity;
		private CardTypeLine type;

		private synchronized void initColors() {
			if (this.colorIndicator != null && this.color != null && this.colorIdentity != null) {
				return;
			}

			this.color = mapColor(ScryfallCard.this.source.colors);

			this.colorIndicator = EnumSet.copyOf(this.color);
			this.colorIndicator.removeAll(this.manaCost().color());

			this.colorIdentity = mapColor(ScryfallCard.this.source.colors);
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
			return ScryfallCard.this.source.name;
		}

		@Override
		public ManaCost manaCost() {
			if (this.manaCost == null) {
				this.manaCost = BasicManaCost.parse(ScryfallCard.this.source.manaCost);
			}

			return this.manaCost;
		}

		@Override
		public Set<Color> colorIndicator() {
			initColors();
			return this.colorIndicator;
		}

		@Override
		public CardTypeLine type() {
			if (this.type == null) {
				this.type = CardTypeLine.parse()
			}

			return null;
		}

		@Override
		public String rules() {
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

		@Override
		public String handModifier() {
			return null;
		}

		@Override
		public String lifeModifier() {
			return null;
		}

		@Override
		public Set<Color> color() {
			return null;
		}

		@Override
		public Set<Color> colorIdentity() {
			return null;
		}
	}

	private emi.lib.scryfall.api.Card source;

	@Override
	public Set<ScryfallCard.Face> faces() {
		return null;
	}

	@Override
	public Face face(Face.Kind kind) {
		return null;
	}

	@Override
	public Set<? extends Printing> printings() {
		return null;
	}

	@Override
	public Printing printing(UUID id) {
		return null;
	}
}