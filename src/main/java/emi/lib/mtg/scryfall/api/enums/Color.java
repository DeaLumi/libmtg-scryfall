package emi.lib.mtg.scryfall.api.enums;

public enum Color implements ApiEnum {
	White (emi.lib.mtg.enums.Color.White),
	Blue (emi.lib.mtg.enums.Color.Blue, "U"),
	Black (emi.lib.mtg.enums.Color.Black),
	Red (emi.lib.mtg.enums.Color.Red),
	Green (emi.lib.mtg.enums.Color.Green),
	Unrecognized (null, "unrecognized");

	private final String serialized;
	public final emi.lib.mtg.enums.Color libMtgColor;

	Color(emi.lib.mtg.enums.Color libMtgColor) {
		this.libMtgColor = libMtgColor;
		this.serialized = name().substring(0, 1);
	}

	Color(emi.lib.mtg.enums.Color libMtgColor, String serialized) {
		this.libMtgColor = libMtgColor;
		this.serialized = serialized;
	}

	@Override
	public String serialized() {
		return serialized;
	}
}
