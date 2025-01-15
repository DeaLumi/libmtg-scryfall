package emi.lib.mtg.scryfall;

import emi.lib.mtg.Card;
import emi.lib.mtg.ImageSource;
import emi.lib.mtg.enums.StandardFrame;
import emi.lib.mtg.img.MtgAwtImageUtils;
import emi.lib.mtg.scryfall.api.ScryfallApi;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.Set;
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

	private URL smallCardUrl(Card.Print print) {
		if (print instanceof ScryfallPrint) {
			ScryfallPrint scp = (ScryfallPrint) print;
			Set<ScryfallPrintedFace> printedFaces = scp.card().front() != null ? scp.faces(scp.card().front()) : null;
			return url(scp.cardJson, printedFaces == null || printedFaces.isEmpty() ? null : printedFaces.iterator().next().faceJson, "normal");
		} else {
			return null; // TODO: We may be able to find an image from Scryfall anyway.
		}
	}

	private URL largeFaceUrl(Card.Print.Face printedFace) {
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
	public BufferedImage open(Card.Print print) throws IOException {
		URL url = smallCardUrl(print);

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
	public BufferedImage open(Card.Print.Face facePrint) throws IOException {
		URL url = largeFaceUrl(facePrint);

		if (url == null) {
			return null;
		}

		try {
			// TODO: This is a bit of a hack because Scryfall returns melded face images already assembled and rotated.
			if (facePrint.frame() == StandardFrame.Meld) {
				return MtgAwtImageUtils.clearCorners(openUrl(url).get());
			} else {
				return MtgAwtImageUtils.faceFromFull(facePrint, openUrl(url).get());
			}
		} catch (InterruptedException e) {
			return null;
		} catch (ExecutionException e) {
			throw new IOException(e.getCause());
		}
	}
}
