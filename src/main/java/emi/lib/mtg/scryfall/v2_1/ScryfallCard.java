package emi.lib.mtg.scryfall.v2_1;

import emi.lib.mtg.Card;

import java.util.Set;
import java.util.UUID;

public class ScryfallCard implements Card {
	@Override
	public Set<? extends Face> faces() {
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
