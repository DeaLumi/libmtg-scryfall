package emi.lib.mtg.scryfall;

import emi.lib.mtg.scryfall.api.Card;
import emi.lib.mtg.scryfall.api.PagedList;
import emi.lib.mtg.scryfall.api.ScryfallApi;
import emi.mtg.deckbuilder.model.CardInstance;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ScryfallSearchProvider implements emi.mtg.deckbuilder.view.search.SearchProvider {
	@Override
	public String name() {
		return "Scryfall";
	}

	@Override
	public String toString() {
		return name();
	}

	@Override
	public String usage() {
		return String.join("\n",
				"<p>Submits search requests to Scryfall and filters the results.",
				"See <a href=\"https://scryfall.com/docs/syntax\">https://scryfall.com/docs/syntax</a> for a complete syntax guide.</p>",
				"<p>Note that particularly large requests, such as <code>t:creature</code>, return too many results,",
				"and due to a technical limitation on Scryfall's end, would cause the deckbuilder to spam Scryfall's servers to retrieve them all.",
				"Any query returning more than 1750 results (10 pages) will produce a warning.</p>");
	}

	private static final int RESULTS_PER_PAGE = 175;
	private static final int PAGE_LIMIT = 10;

	@Override
	public Predicate<CardInstance> parse(String s) throws IllegalArgumentException {
		final ScryfallApi api = ScryfallApi.get();
		try {
			System.err.printf("Begin Scryfall search: %s", s);
			PagedList<Card> cards = api.query(s);
			System.err.printf(" ...%d results... ", cards.size());
			if (cards.size() >= RESULTS_PER_PAGE * PAGE_LIMIT) {
				System.err.println(" ...too many!");
				throw new IllegalArgumentException(String.format("The search returned %d results. Please refine your search or use another search provider.", cards.size()));
			}
			Set<UUID> ids = cards.stream().map(c -> c.id).collect(Collectors.toSet());
			System.err.printf("...fetched! Found %d IDs.\n", ids.size());
			return ci -> ids.contains(ci.id()); // TODO: Wish I matched on set/collector number instead.
		} catch (IOException ioe) {
			throw new IllegalArgumentException("A network error occurred: " + ioe.getLocalizedMessage(), ioe);
		}
	}
}
