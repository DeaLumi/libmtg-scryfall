package emi.lib.mtg.scryfall.api.enums;

public enum BulkDataType implements ApiEnum {
	OracleCards,
	UniqueArtwork,
	DefaultCards,
	AllCards,
	Rulings,

	Unrecognized;

	@Override
	public String serialized() {
		return name().toLowerCase();
	}
}
