package emi.lib.mtg.scryfall.api.enums;

public enum Color implements ApiEnum {
	White (emi.lib.mtg.characteristic.Color.WHITE),
	Blue (emi.lib.mtg.characteristic.Color.BLUE, "U"),
	Black (emi.lib.mtg.characteristic.Color.BLACK),
	Red (emi.lib.mtg.characteristic.Color.RED),
	Green (emi.lib.mtg.characteristic.Color.GREEN),
	Unrecognized (null,"?");

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
