package emi.lib.mtg.scryfall.api;

import emi.lib.mtg.scryfall.api.enums.SetType;

import java.net.URL;
import java.time.LocalDate;

public class Set extends ApiObject {
	public String code;
	public String name;
	public URL searchUri;
	public SetType setType;
	public LocalDate releasedAt;
	public boolean digital;
}
