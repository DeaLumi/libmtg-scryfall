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

	private static File file(Card.Printing.Face face) throws IOException {
		File f = new File(new File(PARENT, String.format("s%s", face.printing().set().code())), String.format("%s.png", face.printing().id().toString()));

		if (!f.getParentFile().exists() && !f.getParentFile().mkdirs()) {
			throw new IOException("Couldn't make parent directory for set " + face.printing().set().code());
		}

		return f;
	}

	private static URL url(Card.Printing.Face printing) {
		if (printing instanceof ScryfallPrintedFace) {
			ScryfallPrintedFace scp = (ScryfallPrintedFace) printing;

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

		public ImageDownloadTask(File dest, URL url, Card.Printing.Face face) {
			this.name = face.printing().card().name();
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
	public InputStream open(Card.Printing.Face face) throws IOException {
		File file = file(face);

		if (file.exists()) {
			return new FileInputStream(file);
		}

		URL url = url(face);

		if (url == null) {
			return null;
		}

		ImageDownloadTask task = new ImageDownloadTask(file, url, face);

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
}
