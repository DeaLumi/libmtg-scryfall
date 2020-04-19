module emi.lib.mtg.scryfall {
	requires com.google.gson;
	requires emi.lib.mtg;
	requires emi.mtg.deckbuilder;
	requires java.desktop;

	exports emi.lib.mtg.scryfall;
	opens emi.lib.mtg.scryfall to com.google.gson;

	uses emi.lib.mtg.DataSource;
	provides emi.lib.mtg.DataSource with emi.lib.mtg.scryfall.ScryfallDataSource;

	uses emi.lib.mtg.ImageSource;
	provides emi.lib.mtg.ImageSource with emi.lib.mtg.scryfall.ScryfallImageSource;

	uses emi.mtg.deckbuilder.model.CardInstance;
	uses emi.mtg.deckbuilder.view.search.SearchProvider;
	provides emi.mtg.deckbuilder.view.search.SearchProvider with emi.lib.mtg.scryfall.ScryfallSearchProvider;
}
