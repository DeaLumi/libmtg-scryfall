package emi.lib.mtg.scryfall.api;

import emi.lib.mtg.scryfall.api.enums.*;

import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class Card extends ApiObject {
	public static class Face extends ApiObject {

		/*
		 * Gameplay Information
		 */

		public String name;
		public String manaCost;
		public String typeLine;
		public String oracleText;
		public String power, toughness, loyalty, defense;
		public Set<Color> colors, colorIndicator;

		/*
		 * Print-Specific
		 */

		public String flavorText;
		public String artist;
		public UUID artistId;
		public UUID illustrationId;
		public UUID oracleId;
		public Map<String, URL> imageUris;
	}

	public static class Part {
		public UUID id;
		public String name;
		public String component;
		public URL uri;
	}

	/*
	 * Core fields
	 */

	public UUID id;
	public UUID oracleId;
	public List<Integer> multiverseIds;
	public Integer mtgoId;
	public URL uri;
	public URL scryfallUri;
	public URL printsSearchUri;

	/*
	 * Gameplay Information
	 */

	public String name;
	public CardLayout layout;
	public Double cmc;
	public String typeLine;
	public String oracleText;
	public String manaCost;
	public String power, toughness;
	public String loyalty, defense;
	public String lifeModifier, handModifier;
	public Set<Color> colors, colorIndicator, colorIdentity;
	public List<Part> allParts;
	public List<Face> cardFaces;
	public Map<String, Legality> legalities;
	public boolean reserved;
	public Integer edhrecRank;
	public URL rulingsUri;

	/*
	 * Print-Specific
	 */

	public String lang;
	public String printedName;
	public String printedText;
	public String printedTypeLine;
	public String set;
	public SetType setType;
	public String setName;
	public String collectorNumber;
	public URL setSearchUri;
	public URL setUri;
	public URL scryfallSetUrl;
	public Map<String, URL> imageUris;
	public boolean highresImage;
	public List<Finish> finishes;
	public boolean oversized;
	public boolean reprint;
	public boolean digital;
	public boolean promo;
	public UUID illustrationId;
	public Rarity rarity;
	public String flavorText;
	public String artist;
	public CardFrame frame;
	public boolean fullArt;
	public String watermark;
	public BorderColor borderColor;
	public Integer storySpotlightNumber;
	public String storySpotlightUri; // This should be a URL, but Scryfall can't get shit consistent.
	public boolean timeshifted, colorshifted, futureshifted;
	public String usd, eur;
	public Map<String, URL> purchaseUris;
	public Map<String, URL> relatedUris;
	public LocalDate releasedAt;

	public UUID oracleId() {
		if (oracleId != null) return oracleId;
		if (!cardFaces.isEmpty()) {
			UUID id = null;
			for (Face face : cardFaces) {
				if (id == null) id = face.oracleId;
				if (id == null) throw new IllegalStateException(String.format("%s (%s) %s has no oracle ID!", name, set, collectorNumber));
				if (!id.equals(face.oracleId)) throw new IllegalStateException(String.format("%s (%s) %s has multiple faces with different oracle IDs: %s", name, set, collectorNumber, cardFaces.stream().map(f -> String.format("%s (%s)", f.name, f.oracleId)).collect(Collectors.joining())));
			}
			this.oracleId = id;
			return id;
		}

		throw new IllegalStateException(String.format("%s (%s) %s has no oracle ID!", name, set, collectorNumber));
	}
}
