package emi.lib.mtg.scryfall;

import emi.lib.mtg.Card;
import emi.lib.mtg.ImageSource;
import emi.lib.mtg.img.MtgAwtImageUtils;
import emi.lib.mtg.scryfall.api.ScryfallApi;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.*;

public class ScryfallImageSource implements ImageSource {

	@Override
	public int priority() {
		return 50;
	}

	private URL url(emi.lib.mtg.scryfall.api.Card cardJson, emi.lib.mtg.scryfall.api.Card.Face faceJson, String imageUri) {
		switch (cardJson.layout) {
			case Transform:
			case ModalDFC:
			case ReversibleCard:
				assert faceJson != null && faceJson.imageUris != null;
				return faceJson.imageUris.get(imageUri);
			default:
				assert cardJson.imageUris != null;
				return cardJson.imageUris.get(imageUri);
		}
	}

	private URL smallCardUrl(Card.Printing printing) {
		if (printing instanceof ScryfallPrinting) {
			ScryfallPrinting scp = (ScryfallPrinting) printing;
			ScryfallPrintedFace front = scp.face(Card.Face.Kind.Front);
			return url(scp.cardJson, front != null ? front.faceJson : null, "normal");
		} else {
			return null; // TODO: We may be able to find an image from Scryfall anyway.
		}
	}

	private URL largeFaceUrl(Card.Printing.Face printedFace) {
		if (printedFace instanceof ScryfallPrintedFace) {
			ScryfallPrintedFace spf = (ScryfallPrintedFace) printedFace;
			return url(spf.cardJson, spf.faceJson, "large");
		} else {
			return null; // TODO: We may be able to find an image from Scryfall anyway.
		}
	}

	private Future<BufferedImage> openUrl(URL url) {
		return ScryfallApi.get().getURL(url, "image/jpeg", null, true)
				.thenApply(is -> {
					try {
						return ImageIO.read(is);
					} catch (IOException ioe) {
						throw new CompletionException(ioe);
					}
				});
	}

	@Override
	public BufferedImage open(Card.Printing printing) throws IOException {
		URL url = smallCardUrl(printing);

		if (url == null) {
			return null;
		}

		try {
			return openUrl(url).get();
		} catch (InterruptedException e) {
			return null;
		} catch (ExecutionException e) {
			throw new IOException(e.getCause());
		}
	}

	@Override
	public BufferedImage open(Card.Printing.Face facePrint) throws IOException {
		URL url = largeFaceUrl(facePrint);

		if (url == null) {
			return null;
		}

		try {
			return MtgAwtImageUtils.faceFromFull(facePrint, openUrl(url).get());
		} catch (InterruptedException e) {
			return null;
		} catch (ExecutionException e) {
			throw new IOException(e.getCause());
		}
	}
}
