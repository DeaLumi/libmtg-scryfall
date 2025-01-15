package emi.lib.mtg.scryfall;

import emi.lib.mtg.scryfall.serde.ScryfallSerde;
import emi.mtg.deckbuilder.model.Preferences;

public class ScryfallPreferences implements Preferences.Plugin {
	private static ScryfallPreferences DEFAULT_INSTANCE = null;

	public static ScryfallPreferences get() {
		try {
			return emi.mtg.deckbuilder.model.Preferences.get().plugin(ScryfallPreferences.class);
		} catch (IllegalStateException ise) {
			if (DEFAULT_INSTANCE == null) {
				System.err.println("!!! Scryfall plugin loaded without Deckbuilder preferences instantiated. Are you running the plugin directly? Preferences will be default!");
				DEFAULT_INSTANCE = new ScryfallPreferences();
			}

			return DEFAULT_INSTANCE; // TODO: Should I cache this so tests can change prefs?
		}
	}

	@Override
	public String name() {
		return "Scryfall";
	}

	@Preference(value="Update Interval (days)", min=1.0, max=365.0, tooltip="The number of days before data is considered old and an update is proposed.")
	public double updateInterval = 7.0;

	@Preference(value="Disk Encoding", buttonBar=true, tooltip="JSON is human-readable; MessagePack is somewhat faster.\nRequires a restart, and possibly a re-download.")
	public ScryfallSerde.Implementation serde = ScryfallSerde.Implementation.Json;

	@Preference(value="Function Tags", tooltip="Comma-separated list of function tags which should be imported from Scryfall.")
	public String functionTags = "removal, sweeper, draw, ramp, mana dork, mana rock";

	@Preference(value="Art Tags", tooltip="Comma-separated list of art tags which should be imported from Scryfall. CAUTION: Some tags match tens of thousands of total printings!")
	public String artTags = "squirrel, turtle, lantern";
}
