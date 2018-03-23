package emi.lib.mtg.scryfall;

import emi.lib.Service;
import emi.lib.mtg.Card;
import emi.lib.mtg.ImageSource;
import emi.lib.mtg.img.MtgAwtImageUtils;
import emi.lib.scryfall.api.enums.CardLayout;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service.Provider(ImageSource.class)
@Service.Property.String(name="name", value="Scryfall")
@Service.Property.Number(name="priority", value=0.5)
public class ScryfallImageSource implements ImageSource {

	private URL url(emi.lib.scryfall.api.Card cardJson, emi.lib.scryfall.api.Card.Face faceJson, String imageUri) {
		if (cardJson.layout == CardLayout.Transform) {
			assert faceJson != null && faceJson.imageUris != null;
			return faceJson.imageUris.get(imageUri);
		} else {
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

	private static ExecutorService downloader = Executors.newSingleThreadExecutor(r -> {
		Thread th = Executors.defaultThreadFactory().newThread(r);
		th.setDaemon(true);
		return th;
	});

	private static final long DOWNLOAD_DELAY = 150;
	private Future<BufferedImage> openUrl(URL url) {
		return downloader.submit(() -> {
			try {
				Thread.sleep(DOWNLOAD_DELAY);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}

			return ImageIO.read(url);
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
