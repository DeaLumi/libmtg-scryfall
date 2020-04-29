package emi.lib.mtg.scryfall;

import emi.lib.mtg.Card;
import emi.lib.mtg.ImageSource;
import emi.lib.mtg.img.MtgAwtImageUtils;
import emi.lib.mtg.scryfall.api.enums.CardLayout;

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

	private static class ImageDownloadTask {
		public final URL url;
		public final CompletableFuture<BufferedImage> image;

		public ImageDownloadTask(URL url) {
			this.url = url;
			this.image = new CompletableFuture<>();
		}
	}
	private static final long DOWNLOAD_DELAY = 150;

	private static final LinkedBlockingDeque<ImageDownloadTask> downloadStack = new LinkedBlockingDeque<>();

	private static final Thread downloadThread = new Thread(() -> {
		while (!Thread.currentThread().isInterrupted()) {
			try {
				ImageDownloadTask task = downloadStack.take();
				try {
					task.image.complete(ImageIO.read(task.url));
				} catch (IOException ioe) {
					task.image.completeExceptionally(ioe);
				}
				Thread.sleep(DOWNLOAD_DELAY);
			} catch (InterruptedException ie) {
				break;
			}
		}
	}, "Scryfall Image Download Thread");

	static {
		downloadThread.setDaemon(true);
		downloadThread.start();
	}

	private Future<BufferedImage> openUrl(URL url) {
		ImageDownloadTask task = new ImageDownloadTask(url);
		downloadStack.push(task);
		return task.image;
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
