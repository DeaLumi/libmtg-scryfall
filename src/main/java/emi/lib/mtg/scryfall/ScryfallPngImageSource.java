package emi.lib.mtg.scryfall;

import emi.lib.Service;
import emi.lib.mtg.ImageSource;

@Service.Provider(ImageSource.class)
@Service.Property.String(name="name", value="Scryfall PNG")
@Service.Property.Number(name="priority", value=0.45)
public class ScryfallPngImageSource extends ScryfallImageSource {
	@Override
	protected String imageUri() {
		return "png";
	}

	@Override
	protected String extension() {
		return "png";
	}
}
