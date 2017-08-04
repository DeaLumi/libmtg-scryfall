package emi.lib.mtg.scryfall;

import emi.lib.Service;
import emi.lib.mtg.ImageSource;

@Service.Provider(ImageSource.class)
@Service.Property.String(name="name", value="Scryfall Large")
@Service.Property.Number(name="priority", value=0.5)
public class ScryfallLargeImageSource extends ScryfallImageSource {
	protected String imageUri() {
		return "large";
	}

	protected String extension() {
		return "jpg";
	}
}
