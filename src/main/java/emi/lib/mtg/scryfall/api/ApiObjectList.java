package emi.lib.mtg.scryfall.api;

import java.net.URL;
import java.util.List;

public class ApiObjectList<T extends ApiObject> extends ApiObject {
	public static class SetList extends ApiObjectList<Set> {

	}

	public static class CardList extends ApiObjectList<Card> {

	}

	public List<T> data;
	public boolean hasMore;
	public URL nextPage;
	public Long totalCards;
	public java.util.Set<String> warnings;
}
