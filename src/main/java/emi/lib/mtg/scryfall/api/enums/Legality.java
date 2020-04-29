package emi.lib.mtg.scryfall.api.enums;

import emi.lib.mtg.Card;

public enum Legality implements ApiEnum {
	Legal (Card.Legality.Legal),
	NotLegal (Card.Legality.NotLegal),
	Restricted (Card.Legality.Restricted),
	Banned (Card.Legality.Banned),
	Unrecognized (null);

	public final Card.Legality libMtgLegality;

	private Legality(emi.lib.mtg.Card.Legality libMtgLegality) {
		this.libMtgLegality = libMtgLegality;
	}

	@Override
	public String serialized() {
		return this.name().toLowerCase();
	}
}
