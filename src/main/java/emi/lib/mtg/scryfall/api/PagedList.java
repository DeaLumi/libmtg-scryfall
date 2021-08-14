package emi.lib.mtg.scryfall.api;

import java.io.IOException;
import java.util.*;

/**
 * Created by Emi on 7/5/2017.
 */
public class PagedList<T extends ApiObject> extends AbstractList<T> {
	private final ScryfallApi api;
	private final NavigableMap<Integer, ApiObjectList<T>> pages;

	public PagedList(ScryfallApi api, ApiObjectList<T> pageOne) {
		this.api = api;
		this.pages = new TreeMap<>();

		if (pageOne != null && !pageOne.data.isEmpty()) {
			this.pages.put(0, pageOne);
		}
	}

	private boolean fetchNextPage() throws IOException {
		if (!pages.lastEntry().getValue().hasMore) {
			return false;
		}

		ApiObjectList<T> nextPage = api.requestJson(pages.lastEntry().getValue().nextPage, pages.lastEntry().getValue().getClass());
		pages.put(pages.lastKey() + pages.lastEntry().getValue().data.size(), nextPage);

		return true;
	}

	@Override
	public T get(int index) {
		if (pages.isEmpty()) {
			throw new NoSuchElementException();
		}

		while (pages.lastKey() + pages.lastEntry().getValue().data.size() <= index) {
			try {
				if (!fetchNextPage()) {
					throw new NoSuchElementException();
				}
			} catch (IOException ioe) {
				throw new RuntimeException(ioe);
			}
		}

		Map.Entry<Integer, ApiObjectList<T>> page = pages.floorEntry(index);
		return page.getValue().data.get(index - page.getKey());
	}

	@Override
	public int size() {
		if (pages.isEmpty()) {
			return 0;
		} else if (pages.firstEntry().getValue().totalCards != null) {
			return pages.firstEntry().getValue().totalCards.intValue();
		}

		return pages.lastKey() + pages.lastEntry().getValue().data.size();
	}

	@Override
	public boolean isEmpty() {
		return pages.isEmpty() || pages.firstEntry().getValue().data.isEmpty();
	}
/*
	@Override
	public boolean contains(Object o) {
		return pages.values().stream().map(page -> page.data).anyMatch(pageData -> pageData.contains(o));
	}

	@Override
	public Iterator<T> iterator() {
		return listIterator();
	}

	@Override
	public Object[] toArray() {
		return pages.values().stream().map(page -> page.data).toArray();
	}

	@Override
	public <T1> T1[] toArray(T1[] a) {
		return pages.values().stream().map(page -> page.data).toArray(i -> a);
	}
*/
}
