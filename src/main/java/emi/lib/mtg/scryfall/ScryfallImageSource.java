package emi.lib.mtg.scryfall;

import emi.lib.Service;
import emi.lib.mtg.card.CardFace;
import emi.lib.mtg.data.ImageSource;
import javafx.concurrent.Task;
import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Stack;
import java.util.concurrent.*;

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

	private static emi.lib.scryfall.api.Card card(CardFace face) {
		if (face instanceof ScryfallSet.ScryfallCard.ScryfallCardPartFace) {
			return ((ScryfallSet.ScryfallCard.ScryfallCardPartFace) face).card;
		} else if (face instanceof ScryfallSet.ScryfallCard.ScryfallCardFace) {
			return ((ScryfallSet.ScryfallCard) face.card()).source;
		} else {
			return null;
		}
	}

	private static URL imageUrl(CardFace face) {
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

	private static File file(CardFace face) throws IOException {
		emi.lib.scryfall.api.Card card = card(face);

		if (card != null) {
			File setDir = new File(PARENT_DIR, String.format("s%s", card.set));

			if (!setDir.exists() && !setDir.mkdir()) {
				throw new IOException("Couldn't create parent directory for set " + card.setName);
			}

			return new File(setDir, String.format("%s.png", card.id.toString()));
		} else {
			return null;
		}
	}

	private static class ImageDownloadTask {
		public final String name;
		public final URL url;
		public final File file;
		public final CompletableFuture<File> future;

		public ImageDownloadTask(String name, File file, URL url) {
			this.name = name;
			this.file = file;
			this.url = url;
			this.future = new CompletableFuture<>();
		}
	}

	private static final long DOWNLOAD_DELAY = 100;
	private static final BlockingDeque<ImageDownloadTask> DOWNLOAD_QUEUE = new LinkedBlockingDeque<>();

	private static final Thread DOWNLOAD_THREAD = new Thread(() -> {
		try {
			while (!Thread.currentThread().isInterrupted()) {
				Thread.sleep(DOWNLOAD_DELAY);

				ImageDownloadTask task = DOWNLOAD_QUEUE.takeFirst();

				try {
					System.err.println("Downloading " + task.name);
					System.err.flush();

					HttpURLConnection connection = (HttpURLConnection) task.url.openConnection();

					if (connection.getResponseCode() != 200) {
						throw new IOException("Response from server was not OK.");
					}

					InputStream in = connection.getInputStream();
					ImageIO.write(ImageIO.read(connection.getInputStream()), "png", task.file);

					task.future.complete(task.file);
				} catch (IOException ioe) {
					task.future.completeExceptionally(ioe);
				}
			}
		} catch (InterruptedException ie) {
			// meh
		}
	}, "Scryfall Image Downloading Thread");

	static {
		ScryfallImageSource.DOWNLOAD_THREAD.setDaemon(true);
		ScryfallImageSource.DOWNLOAD_THREAD.start();
	}

	private static Future<File> getImage(CardFace face) throws IOException {
		File f = file(face);

		if (f == null) {
			return null;
		}

		if (f.exists()) {
			return CompletableFuture.completedFuture(f);
		}

		URL url = imageUrl(face);

		if (url == null) {
			System.err.println("Weird -- couldn't generate an image URL despite having a ScryfallCardFace?");
		}

		try {
			ImageDownloadTask task = new ImageDownloadTask(face.name(), f, url);
			DOWNLOAD_QUEUE.putFirst(task);
			System.err.println("Stacking " + face.name());
			System.err.flush();
			return task.future;
		} catch (InterruptedException e) {
			throw new IOException(e);
		}
	}

	@Override
	public InputStream open(CardFace face) throws IOException {
		Future<File> imageDownloadTask = getImage(face);

		while (!imageDownloadTask.isDone()) {
			try {
				Thread.sleep(5);
			} catch (InterruptedException ie) {
				throw new IOException(ie);
			}
		}

		try {
			return new FileInputStream(imageDownloadTask.get());
		} catch (InterruptedException | ExecutionException e) {
			throw new IOException(e);
		}
	}
}
