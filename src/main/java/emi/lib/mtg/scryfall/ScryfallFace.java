package emi.lib.mtg.scryfall;

import emi.lib.mtg.Card;
import emi.lib.mtg.Mana;
import emi.lib.mtg.enums.Color;
import emi.lib.mtg.TypeLine;
import emi.lib.mtg.game.ability.Abilities;

import java.util.Set;

import static emi.lib.mtg.scryfall.Util.or;
import static emi.lib.mtg.scryfall.Util.orEmpty;

class ScryfallFace implements Card.Face {

	private final Kind kind;
	private final emi.lib.mtg.scryfall.api.Card cardJson;
	private final emi.lib.mtg.scryfall.api.Card.Face faceJson;

	private Mana.Value manaCost;
	private TypeLine typeLine;
	private Abilities abilities;

	private Color.Combination color, colorIndicator, colorIdentity;

	ScryfallFace(Kind kind, emi.lib.mtg.scryfall.api.Card cardJson, emi.lib.mtg.scryfall.api.Card.Face faceJson) {
		this.kind = kind;
		this.cardJson = cardJson;
		this.faceJson = faceJson;
	}

	ScryfallFace(Kind kind, emi.lib.mtg.scryfall.api.Card cardJson) {
		this(kind, cardJson, null);
	}

	ScryfallFace(emi.lib.mtg.scryfall.api.Card cardJson) {
		this(Card.Face.Kind.Front, cardJson);
	}

	@Override
	public String toString() {
		return name();
	}

	private void initColor() {
		if (color != null && colorIndicator != null && colorIdentity != null) {
			return;
		}

		switch (this.kind()) {
			case Flipped:
				colorIndicator = Util.mapColor(orEmpty(cardJson.colorIndicator));
				color = Util.mapColor(orEmpty(cardJson.colors))
						.plus(colorIndicator);
				colorIdentity = Mana.Symbol.symbolsIn(cardJson.oracleText)
						.map(Mana.Symbol::color)
						.collect(Color.Combination.COMBO_COLLECTOR)
						.plus(color);
				break;

			case Front:
			case Transformed:
			case Left:
			case Right:
			case Other:
				// Have to check for faceJson here; meld backsides are still separate Card objects.
				colorIndicator = Util.mapColor(orEmpty(faceJson != null && faceJson.colorIndicator != null ? faceJson.colorIndicator : cardJson.colorIndicator));
				color = Util.mapColor(orEmpty(faceJson != null && faceJson.colors != null ? faceJson.colors : cardJson.colors))
						.plus(colorIndicator);
				colorIdentity = Mana.Symbol.symbolsIn(faceJson != null ? faceJson.oracleText : cardJson.oracleText)
						.map(Mana.Symbol::color)
						.collect(Color.Combination.COMBO_COLLECTOR)
						.plus(color);
				break;
		}
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
	public Mana.Value manaCost() {
		if (manaCost == null) {
			final String source = or(faceJson != null ? faceJson.manaCost : cardJson.manaCost, "");
			try {
				manaCost = Mana.Value.parse(source);
			} catch (IllegalArgumentException iae) {
				throw new IllegalArgumentException("When parsing mana cost of " + cardJson.name + " in set " + cardJson.setName);
			}
		}

		return this.manaCost;
	}

	@Override
	public double manaValue() {
		return cardJson.cmc != null ? cardJson.cmc : 0.0;
	}

	@Override
	public Color.Combination colorIndicator() {
		initColor();
		return colorIndicator;
	}

	@Override
	public Color.Combination color() {
		initColor();
		return color;
	}

	@Override
	public Color.Combination colorIdentity() {
		initColor();
		return colorIdentity;
	}

	@Override
	public TypeLine type() {
		if (typeLine == null) {
			String typeLine = cardJson.typeLine;

			if (faceJson != null && faceJson.typeLine != null) {
				typeLine = faceJson.typeLine;
			}

			this.typeLine = TypeLine.Basic.parse(typeLine);
		}

		return typeLine;
	}

	@Override
	public String rules() {
		return or(faceJson != null ? faceJson.oracleText : cardJson.oracleText, "");
	}

	@Override
	public String printedPower() {
		return or(faceJson != null ? faceJson.power : cardJson.power, "");
	}

	@Override
	public String printedToughness() {
		return or(faceJson != null ? faceJson.toughness : cardJson.toughness, "");
	}

	@Override
	public String printedLoyalty() {
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

	@Override
	public Abilities abilities() {
		if (abilities == null) {
			abilities = new Abilities.DefaultAbilities(this);
		}

		return abilities;
	}
}
