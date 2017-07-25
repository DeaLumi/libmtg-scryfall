package emi.lib.mtg.scryfall;

import emi.lib.Service;
import emi.lib.mtg.Card;
import emi.lib.mtg.ImageSource;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.*;

@Service.Provider(ImageSource.class)
@Service.Property.String(name="name", value="Scryfall HQ")
@Service.Property.Number(name="priority", value=0.5)
public class ScryfallImageSource implements ImageSource {

	private static final File PARENT = new File(new File("images"), "scryfall");

	static {
		if (!PARENT.exists() && !PARENT.mkdirs()) {
			throw new Error("Couldn't create image directory for ScryfallImageSourceV2");
		}
	}

	private static File file(Card.Printing printing, Card.Face face) {
		return new File(PARENT, String.format("%s.png", printing.id().toString()));
	}

	private static URL url(Card.Printing printing, Card.Face face) {
		if (printing instanceof ScryfallPrinting) {
			ScryfallPrinting scp = (ScryfallPrinting) printing;

			URL pngUrl = scp.cardJson.imageUris.get("png");

			if (pngUrl != null) {
				return pngUrl;
			}

			return scp.cardJson.imageUri;
		} else {
			return null; // TODO: We may be able to find an image from Scryfall anyway.
		}
	}

	private static class ImageDownloadTask {
		public final String name;
		public final URL url;
		public final File dest;
		public final CompletableFuture<File> future;

		public ImageDownloadTask(File dest, URL url, Card.Printing printing, Card.Face face) {
			this.name = face.name();
			this.url = url;
			this.dest = dest;
			this.future = new CompletableFuture<>();
		}
	}

	private static final BlockingDeque<ImageDownloadTask> DOWNLOAD_QUEUE = new LinkedBlockingDeque<>();
	private static final long DOWNLOAD_DELAY = 100;

	private static final Thread DOWNLOAD_THREAD = new Thread(() -> {
		try {
			while (!Thread.currentThread().isInterrupted()) {
				ImageDownloadTask nextImage = DOWNLOAD_QUEUE.take();

				try {
					HttpURLConnection connection = (HttpURLConnection) nextImage.url.openConnection();

					if (connection.getResponseCode() != 200) {
						throw new IOException("Response from server was not okay.");
					}

					InputStream in = connection.getInputStream();
					ImageIO.write(ImageIO.read(in), "png", nextImage.dest);
					nextImage.future.complete(nextImage.dest);
				} catch (IOException ie) {
					nextImage.future.completeExceptionally(ie);
				}

				Thread.sleep(DOWNLOAD_DELAY);
			}
		} catch (InterruptedException ie) {
			// meh
		}
	}, "Scryfall Image Downloading Thread");

	static {
		DOWNLOAD_THREAD.setDaemon(true);
		DOWNLOAD_THREAD.start();
	}

	@Override
	public InputStream open(Card.Printing printing, Card.Face face) throws IOException {
		File file = file(printing, face);

		if (file.exists()) {
			return new FileInputStream(file);
		}

		URL url = url(printing, face);

		if (url == null) {
			return null;
		}

		ImageDownloadTask task = new ImageDownloadTask(file, url, printing, face);

		try {
			DOWNLOAD_QUEUE.put(task);

			while (!task.future.isDone()) {
				Thread.sleep(10);
			}

			return new FileInputStream(task.future.get());
		} catch (InterruptedException ie) {
			return null;
		} catch (ExecutionException e) {
			throw new IOException(e);
		}
	}

	@Override
	public InputStream open(Card.Printing printing) throws IOException {
		if (printing.card().face(Card.Face.Kind.Front) != null) {
			return open(printing, printing.card().face(Card.Face.Kind.Front));
		} else if (printing.card().face(Card.Face.Kind.Left) != null) {
			return open(printing, printing.card().face(Card.Face.Kind.Front));
		} else {
			System.err.println("Couldn't decide on a face for " + printing.card().fullName());
			return null;
		}
	}
}
