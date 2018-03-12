package emi.lib.mtg.scryfall;

import emi.lib.mtg.Card;
import emi.lib.mtg.ImageSource;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;

public abstract class ScryfallImageSource implements ImageSource {

	private static final File PARENT = new File(new File("images"), "scryfall");

	static {
		if (!PARENT.exists() && !PARENT.mkdirs()) {
			throw new Error("Couldn't create image directory for ScryfallImageSource");
		}
	}

	private File file(Card.Printing.Face face) throws IOException {
		File subdir = new File(new File(PARENT, imageUri()), String.format("s%s", face.printing().set().code()));

		StringBuilder cname = new StringBuilder();
		cname.append(face.printing().id().toString());

		if (face.face().kind() != Card.Face.Kind.Front) {
			cname.append('-').append(face.face().kind().name());
		}

		cname.append('.').append(extension());

		File f = new File(subdir, cname.toString());

		if (!f.getParentFile().exists() && !f.getParentFile().mkdirs()) {
			throw new IOException("Couldn't make parent directory for set " + face.printing().set().code());
		}

		return f;
	}

	private URL url(Card.Printing.Face printing) {
		if (printing instanceof ScryfallPrintedFace) {
			ScryfallPrintedFace scp = (ScryfallPrintedFace) printing;

			if (scp.faceJson != null && scp.faceJson.imageUris != null) {
				return scp.faceJson.imageUris.get(imageUri());
			} else if (scp.cardJson.imageUris != null) {
				return scp.cardJson.imageUris.get(imageUri());
			} else {
				return null;
			}
		} else {
			return null; // TODO: We may be able to find an image from Scryfall anyway.
		}
	}

	private static class ImageDownloadTask {
		public final String name;
		public final Card.Printing.Face facePrint;
		public final URL url;
		public final File dest;
		public final String ext;
		public final CompletableFuture<File> future;

		public ImageDownloadTask(File dest, URL url, Card.Printing.Face face, String ext) {
			this.name = face.printing().card().name();
			this.facePrint = face;
			this.url = url;
			this.dest = dest;
			this.future = new CompletableFuture<>();
			this.ext = ext;
		}
	}

	private static final BlockingDeque<ImageDownloadTask> DOWNLOAD_QUEUE = new LinkedBlockingDeque<>();
	private static final long DOWNLOAD_DELAY = 150;

	private static final Thread DOWNLOAD_THREAD = new Thread(() -> {
		try {
			while (!Thread.currentThread().isInterrupted()) {
				ImageDownloadTask nextImage = DOWNLOAD_QUEUE.takeLast();

				try {
					HttpURLConnection connection = (HttpURLConnection) nextImage.url.openConnection();

					if (connection.getResponseCode() != 200) {
						throw new IOException("Response from server was not okay.");
					}

					InputStream in = connection.getInputStream();
					ImageIO.write(ImageIO.read(in), nextImage.ext, nextImage.dest);
					// TODO: Edit the image. Rotate for splits & round corners.
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

	protected abstract String imageUri();

	protected abstract String extension();

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

		ImageDownloadTask task = new ImageDownloadTask(file, url, face, extension());

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
