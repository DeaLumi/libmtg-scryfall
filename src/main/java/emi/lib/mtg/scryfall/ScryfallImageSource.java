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

			return new File(setDir, String.format("%s.png", card.id.toString()));
		} else {
			return null;
		}
	}

	private static final long DOWNLOAD_DELAY = 500;

	private static final ScheduledExecutorService DOWNLOAD_POOL = new ScheduledThreadPoolExecutor(1, r -> {
		Thread th = Executors.defaultThreadFactory().newThread(r);
		th.setName("Scryfall Image Downloader");
		th.setDaemon(true);
		return th;
	});

	private long nextDownload = System.currentTimeMillis();

	@Override
	public InputStream open(CardFace face) throws IOException {
		File f = file(face);

		if (f == null) {
			return null; // Not a ScryfallCardFace.
		}

		if (!f.exists()) {
			long delay;
			synchronized (this) {
				long now = System.currentTimeMillis();
				nextDownload = Math.max(nextDownload + DOWNLOAD_DELAY, now);
				delay = now - nextDownload;
			}

			Future<Void> imageDownloadTask = DOWNLOAD_POOL.schedule(() -> {
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
				ImageIO.write(ImageIO.read(connection.getInputStream()), "png", f);

				return null;
			}, delay, TimeUnit.MILLISECONDS);

			while (!imageDownloadTask.isDone()) {
				try {
					Thread.sleep(5);
				} catch (InterruptedException ie) {
					throw new IOException(ie);
				}
			}

			try {
				imageDownloadTask.get();
			} catch (InterruptedException | ExecutionException e) {
				throw new IOException(e);
			}
		}

		return new FileInputStream(f);
	}
}
