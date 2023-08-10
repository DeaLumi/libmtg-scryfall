package emi.lib.mtg.scryfall.api.enums;

import emi.lib.mtg.enums.StandardFrame;

public enum CardFrame implements ApiEnum {
	Old1993 ("1993", false),
	Old1997 ("1997", false),
	Modern2001 ("2001", true),
	Modern2003 ("2003", true),
	Khans2015 ("2015", true),
	Future ("future", true),
	Unrecognized("unrecognized", true);

	private final String serialized;
	public final StandardFrame splitLeftFrame, splitRightFrame, flipTopFrame, flipBottomFrame;

	CardFrame(String serialized, boolean modern) {
		this.serialized = serialized;
		this.splitLeftFrame = modern ? StandardFrame.SplitLeftModern : StandardFrame.SplitLeftFull;
		this.splitRightFrame = modern ? StandardFrame.SplitRightModern : StandardFrame.SplitRightFull;
		this.flipTopFrame = modern ? StandardFrame.FlipTopModern : StandardFrame.FlipTopFull;
		this.flipBottomFrame = modern ? StandardFrame.FlipBottomModern : StandardFrame.FlipBottomFull;
	}

	@Override
	public String serialized() {
		return this.serialized;
	}
}
