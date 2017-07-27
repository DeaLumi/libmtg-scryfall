package emi.lib.mtg.scryfall;

import emi.lib.mtg.Card;
import emi.lib.mtg.characteristic.CardTypeLine;
import emi.lib.mtg.characteristic.Color;
import emi.lib.mtg.characteristic.ManaCost;
import emi.lib.mtg.characteristic.impl.BasicCardTypeLine;
import emi.lib.mtg.characteristic.impl.BasicManaCost;
import emi.lib.mtg.scryfall.Util;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static emi.lib.mtg.scryfall.Util.or;

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
	}

	ScryfallFace(Kind kind, emi.lib.scryfall.api.Card cardJson) {
		this(kind, cardJson, null);
	}

	ScryfallFace(emi.lib.scryfall.api.Card cardJson) {
		this(Card.Face.Kind.Front, cardJson);
	}

	private void initColor() {
		if (this.color != null && this.colorIndicator != null && this.colorIdentity != null) {
			return;
		}

		switch (this.kind()) {
			case Front:
			case Flipped:
			case Transformed:
			case Other:
				this.color = Collections.unmodifiableSet(Util.mapColor(this.cardJson.colors));
				this.colorIdentity = Collections.unmodifiableSet(Util.mapColor(this.cardJson.colorIdentity));
				break;

			case Left:
			case Right:
				this.color = Collections.unmodifiableSet(EnumSet.copyOf(this.manaCost().color()));
				this.colorIdentity = Collections.unmodifiableSet(EnumSet.copyOf(this.color()));
				break;

			default:
				assert false;
				this.color = Collections.unmodifiableSet(EnumSet.noneOf(Color.class));
				this.colorIdentity = Collections.unmodifiableSet(EnumSet.noneOf(Color.class));
		}

		Set<Color> colorIndicator = EnumSet.copyOf(this.color);
		colorIndicator.removeAll(this.manaCost().color());
		this.colorIndicator = Collections.unmodifiableSet(colorIndicator);
	}

	@Override
	public Kind kind() {
		return this.kind;
	}

	@Override
	public String name() {
		return or(this.faceJson != null ? this.faceJson.name : this.cardJson.name, "");
	}

	@Override
	public ManaCost manaCost() {
		if (this.manaCost == null) {
			String manaCost;

			switch (this.kind()) {
				case Front:
				case Flipped: // Flip cards use the upright (normal) mana cost.
				case Transformed:
				case Other:
					manaCost = this.cardJson.manaCost;
					break;

				case Left:
				case Right:
					manaCost = this.faceJson.manaCost;
					break;

				default:
					assert false;
					return null;
			}

			this.manaCost = BasicManaCost.parse(manaCost);
		}

		return this.manaCost;
	}

	@Override
	public Set<Color> colorIndicator() {
		initColor();
		return this.colorIndicator;
	}

	@Override
	public Set<Color> color() {
		initColor();
		return this.color;
	}

	@Override
	public Set<Color> colorIdentity() {
		initColor();
		return this.colorIdentity;
	}

	@Override
	public CardTypeLine type() {
		if (this.typeLine == null) {
			String typeLine = this.cardJson.typeLine;

			if (this.faceJson != null && this.faceJson.typeLine != null) {
				typeLine = this.faceJson.typeLine;
			}

			this.typeLine = BasicCardTypeLine.parse(typeLine);
		}

		return this.typeLine;
	}

	@Override
	public String rules() {
		return or(this.faceJson != null ? this.faceJson.oracleText : this.cardJson.oracleText, "");
	}

	@Override
	public String power() {
		return or(this.faceJson != null ? this.faceJson.power : this.cardJson.power, "");
	}

	@Override
	public String toughness() {
		return or(this.faceJson != null ? this.faceJson.toughness : this.cardJson.toughness, "");
	}

	@Override
	public String loyalty() {
		return or(this.cardJson.loyalty, "");
	}

	@Override
	public String handModifier() {
		return or(this.cardJson.handModifier, "");
	}

	@Override
	public String lifeModifier() {
		return or(this.cardJson.lifeModifier, "");
	}
}
