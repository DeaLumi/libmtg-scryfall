package emi.lib.mtg.scryfall.api.enums;

public enum Color implements ApiEnum {
	White (emi.lib.mtg.characteristic.Color.White),
	Blue (emi.lib.mtg.characteristic.Color.Blue, "U"),
	Black (emi.lib.mtg.characteristic.Color.Black),
	Red (emi.lib.mtg.characteristic.Color.Red),
	Green (emi.lib.mtg.characteristic.Color.Green),
	Unrecognized (null, "unrecognized");

	private final String serialized;
	public final emi.lib.mtg.characteristic.Color libMtgColor;

	Color(emi.lib.mtg.characteristic.Color libMtgColor) {
		this.libMtgColor = libMtgColor;
		this.serialized = name().substring(0, 1);
	}

	Color(emi.lib.mtg.characteristic.Color libMtgColor, String serialized) {
		this.libMtgColor = libMtgColor;
		this.serialized = serialized;
	}

	@Override
	public String serialized() {
		return serialized;
	}
}
