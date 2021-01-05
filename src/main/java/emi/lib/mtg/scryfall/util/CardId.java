package emi.lib.mtg.scryfall.util;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public class CardId {
	private final UUID id;

	public static CardId of(emi.lib.mtg.scryfall.api.Card card) {
		return new CardId(card);
	}

	public static CardId of(emi.lib.mtg.scryfall.api.Card front, emi.lib.mtg.scryfall.api.Card back) {
		return new CardId(front, back);
	}

	public static CardId of(emi.lib.mtg.scryfall.api.Card.Face first, emi.lib.mtg.scryfall.api.Card.Face second) {
		return new CardId(first, second);
	}

	public CardId(emi.lib.mtg.scryfall.api.Card face) {
		this(face.name + "\n" + face.oracleText);
	}

	public CardId(emi.lib.mtg.scryfall.api.Card first, emi.lib.mtg.scryfall.api.Card second) {
		this(first.name, first.oracleText, second.name, second.oracleText);
	}

	public CardId(emi.lib.mtg.scryfall.api.Card.Face face1, emi.lib.mtg.scryfall.api.Card.Face face2) {
		this(face1.name, face1.oracleText, face2.name, face2.oracleText);
	}

	private CardId(String name1, String oracle1, String name2, String oracle2) {
		this(name1 + "\n" + oracle1 + "\n//\n\n" + name2 + "\n" + oracle2);
	}

	private CardId(String total) {
		this.id = UUID.nameUUIDFromBytes(total.getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CardId cardId = (CardId) o;
		return id.equals(cardId.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}
}
