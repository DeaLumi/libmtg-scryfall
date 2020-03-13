package emi.lib.mtg.scryfall;

import emi.lib.mtg.Card;
import emi.lib.mtg.characteristic.CardTypeLine;
import emi.lib.mtg.characteristic.Color;
import emi.lib.mtg.characteristic.ManaCost;
import emi.lib.mtg.characteristic.ManaSymbol;
import emi.lib.mtg.characteristic.impl.BasicCardTypeLine;

import java.util.*;

import static emi.lib.mtg.scryfall.Util.or;
import static emi.lib.mtg.scryfall.Util.orEmpty;

class ScryfallFace implements Card.Face {

	private final Kind kind;
	private final emi.lib.scryfall.api.Card cardJson;
	private final emi.lib.scryfall.api.Card.Face faceJson;

	private ManaCost manaCost;
	private CardTypeLine typeLine;

	private Set<Color> color, colorIndicator, colorIdentity;

	ScryfallFace(Kind kind, emi.lib.scryfall.api.Card cardJson, emi.lib.scryfall.api.Card.Face faceJson) {
		this.kind = kind;
		this.cardJson = cardJson;
		this.faceJson = faceJson;

		this.manaCost();
	}

	ScryfallFace(Kind kind, emi.lib.scryfall.api.Card cardJson) {
		this(kind, cardJson, null);
	}

	ScryfallFace(emi.lib.scryfall.api.Card cardJson) {
		this(Card.Face.Kind.Front, cardJson);
	}

	private void initColor() {
		if (color != null && colorIndicator != null && colorIdentity != null) {
			return;
		}

		switch (this.kind()) {
			case Front:
			case Flipped:
				color = Collections.unmodifiableSet(Util.mapColor(orEmpty(cardJson.colors)));
				colorIndicator = Collections.unmodifiableSet(Util.mapColor(orEmpty(cardJson.colorIndicator)));
				break;

			case Transformed:
			case Left:
			case Right:
				// Have to check for faceJson here; meld backsides are still separate Card objects.
				color = Collections.unmodifiableSet(Util.mapColor(orEmpty(faceJson != null && faceJson.colors != null ? faceJson.colors : cardJson.colors)));
				colorIndicator = Collections.unmodifiableSet(Util.mapColor(orEmpty(faceJson != null && faceJson.colorIndicator != null ? faceJson.colorIndicator : cardJson.colorIndicator)));
				break;

			case Other:
				assert false;
				color = Collections.unmodifiableSet(EnumSet.noneOf(Color.class));
				colorIndicator = Collections.unmodifiableSet(EnumSet.noneOf(Color.class));
				break;
		}

		EnumSet<Color> colorIdentity = EnumSet.noneOf(Color.class);
		colorIdentity.addAll(color);
		colorIdentity.addAll(colorIndicator);
		this.colorIdentity = colorIdentity;
	}

	@Override
	public Kind kind() {
		return kind;
	}

	@Override
	public String name() {
		return or(faceJson != null ? faceJson.name : cardJson.name, "");
	}

	@Override
	public ManaCost manaCost() {
		if (manaCost == null) {
			final String source = or(faceJson != null ? faceJson.manaCost : cardJson.manaCost, "");

			manaCost = new ManaCost() {
				private final List<ManaSymbol> symbols;
				private final double cmc;
				private final Set<Color> color;
				private final boolean varies;

				{
					symbols = Collections.unmodifiableList(ManaSymbol.symbolsIn(source));
					color = Collections.unmodifiableSet(ManaCost.super.color());
					varies = ManaCost.super.varies();
					cmc = ManaCost.super.convertedCost();
				}

				@Override
				public Collection<? extends ManaSymbol> symbols() {
					return symbols;
				}

				@Override
				public double convertedCost() {
					return cmc;
				}

				@Override
				public Set<Color> color() {
					return color;
				}

				@Override
				public boolean varies() {
					return varies;
				}
			};
		}

		return this.manaCost;
	}

	@Override
	public double convertedManaCost() {
		return cardJson.cmc != null ? cardJson.cmc : 0.0;
	}

	@Override
	public Set<Color> colorIndicator() {
		initColor();
		return colorIndicator;
	}

	@Override
	public Set<Color> color() {
		initColor();
		return color;
	}

	@Override
	public CardTypeLine type() {
		if (typeLine == null) {
			String typeLine = cardJson.typeLine;

			if (faceJson != null && faceJson.typeLine != null) {
				typeLine = faceJson.typeLine;
			}

			this.typeLine = BasicCardTypeLine.parse(typeLine);
		}

		return typeLine;
	}

	@Override
	public String rules() {
		return or(faceJson != null ? faceJson.oracleText : cardJson.oracleText, "");
	}

	@Override
	public String power() {
		return or(faceJson != null ? faceJson.power : cardJson.power, "");
	}

	@Override
	public String toughness() {
		return or(faceJson != null ? faceJson.toughness : cardJson.toughness, "");
	}

	@Override
	public String loyalty() {
		return or(cardJson.loyalty, "");
	}

	@Override
	public String handModifier() {
		return or(cardJson.handModifier, "");
	}

	@Override
	public String lifeModifier() {
		return or(cardJson.lifeModifier, "");
	}
}
