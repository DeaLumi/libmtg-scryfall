package emi.lib.mtg.scryfall.api;

import emi.lib.mtg.scryfall.api.enums.SetType;

import java.net.URL;

public class Set extends ApiObject {
	public String code;
	public String name;
	public URL searchUri;
	public SetType setType;
}
