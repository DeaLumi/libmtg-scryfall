package emi.lib.mtg.scryfall;

import emi.lib.Service;
import emi.lib.mtg.card.CardFace;
import emi.lib.mtg.data.ImageSource;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

@Service.Provider(ImageSource.class)
@Service.Property.String(name="name", value="Scryfall HQ")
@Service.Property.Number(name="priority", value=0.5)
public class ScryfallImageSource implements ImageSource {
	private static final File PARENT_DIR = new File(new File("images"), "scryfall");

	static {
		if (!PARENT_DIR.exists() && !PARENT_DIR.mkdirs()) {
			throw new Error("Couldn't create parent directory for Scryfall card images...");
		}
	}

	private emi.lib.scryfall.api.Card card(CardFace face) {
		if (face instanceof ScryfallSet.ScryfallCard.ScryfallCardPartFace) {
			return ((ScryfallSet.ScryfallCard.ScryfallCardPartFace) face).card;
		} else if (face instanceof ScryfallSet.ScryfallCard.ScryfallCardFace) {
			return ((ScryfallSet.ScryfallCard) face.card()).source;
		} else {
			return null;
		}
	}

	private URL imageUrl(CardFace face) {
		emi.lib.scryfall.api.Card card = card(face);

		if (card != null) {
			if (card.imageUris.containsKey("png")) {
				return card.imageUris.get("png");
			} else if (card.imageUris.containsKey("large")) {
				return card.imageUris.get("large");
			} else {
				return card.imageUri;
			}
		}

		return null;
	}

	private File file(CardFace face) throws IOException {
		emi.lib.scryfall.api.Card card = card(face);

		if (card != null) {
			File setDir = new File(PARENT_DIR, String.format("s%s", card.set));

			if (!setDir.exists() && !setDir.mkdir()) {
				throw new IOException("Couldn't create parent directory for set " + card.setName);
			}

			return new File(setDir, card.id.toString());
		} else {
			return null;
		}
	}

	@Override
	public InputStream open(CardFace face) throws IOException {
		File f = file(face);

		if (f == null) {
			return null; // Not a ScryfallCardFace.
		}

		if (!f.exists()) {
			URL imageUrl = imageUrl(face);

			if (imageUrl == null) {
				System.err.println("Weird -- couldn't generate an image URL despite having a ScryfallCardFace?");
				return null;
			}

			HttpURLConnection connection = (HttpURLConnection) imageUrl.openConnection();

			if (connection.getResponseCode() != 200) {
				throw new IOException("Response from server was not OK.");
			}

			InputStream in = connection.getInputStream();
			FileOutputStream out = new FileOutputStream(f);
			byte[] buffer = new byte[4096];
			int read = -1;
			while ((read = in.read(buffer)) >= 0) {
				out.write(buffer, 0, read);
			}
			out.flush();
			out.close();
			in.close();
		}

		return new FileInputStream(f);
	}
}
