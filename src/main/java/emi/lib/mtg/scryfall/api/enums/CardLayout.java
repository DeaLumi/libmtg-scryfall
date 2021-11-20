package emi.lib.mtg.scryfall.api.enums;

public enum CardLayout implements ApiEnum {
	Normal,
	Split,
	Flip,
	Transform,
	Meld,
	Leveler,
	Planar,
	Scheme,
	Vanguard,
	Token,
	Emblem,
	DoubleFacedToken,
	Host,
	Augment,
	Saga,
	Adventure,
	ArtSeries,
	ModalDFC,
	Class,
	ReversibleCard,
	Unrecognized;

	@Override
	public String serialized() {
		return this.name().toLowerCase();
	}
}
