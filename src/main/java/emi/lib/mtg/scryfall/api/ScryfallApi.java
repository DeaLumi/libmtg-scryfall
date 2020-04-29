package emi.lib.mtg.scryfall.api;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import emi.lib.mtg.scryfall.api.enums.ApiEnum;

import javax.net.ssl.HttpsURLConnection;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;

public class ScryfallApi {
	private static final int REQUEST_PAUSE = 250;
	private static final URL BASE;
	public static final Gson GSON;

	static {
		try {
			BASE = new URL("https://api.scryfall.com/");
		} catch (MalformedURLException mue) {
			throw new Error(mue);
		}

		GsonBuilder builder = new GsonBuilder();

		// Register enum type adapters
		builder.registerTypeAdapterFactory(ApiEnum.typeAdapterFactory());

		builder.enableComplexMapKeySerialization();
		builder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);

		builder.setPrettyPrinting();

		GSON = builder.create();
	}

	private final ScheduledExecutorService executor;
	private long nextRequest;

	public ScryfallApi() {
		this.executor = new ScheduledThreadPoolExecutor(1, r -> {
			Thread th = Executors.defaultThreadFactory().newThread(r);
			th.setDaemon(true);
			return th;
		});

		this.nextRequest = System.currentTimeMillis();
	}

	private <T> ScheduledFuture<T> requestJsonAsync(URL url, Type type) {
		long delay;
		synchronized(this) {
			long now = System.currentTimeMillis();
			nextRequest = Math.max(nextRequest + REQUEST_PAUSE, now);
			delay = now - nextRequest;
		}

		return executor.schedule(() -> {
			try {
				HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

				if (connection.getResponseCode() != 200) {
					// TODO: Handle errors...
					System.err.println("HTTP request for " + url.toString() + " failed: " + connection.getResponseMessage());
					return null;
				}

				return GSON.fromJson(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8), type);
			} catch (IOException e) {
				return null;
			}
		}, delay, TimeUnit.MILLISECONDS);
	}

	public <T> T requestJson(URL url, Type type) {
		ScheduledFuture<T> downloadTask = requestJsonAsync(url, type);

		while (!downloadTask.isDone()) {
			try {
				Thread.sleep(5);
			} catch (InterruptedException ie) {
				return null;
			}
		}

		try {
			return downloadTask.get();
		} catch (InterruptedException e) {
			return null;
		} catch (ExecutionException e) {
			e.printStackTrace();
			return null;
		}
	}

	public <T> T requestJson(URL url, Class<T> cls) {
		return requestJson(url, (Type) cls);
	}

	public PagedList<Set> sets() {
		try {
			return new PagedList<>(this, requestJson(new URL(BASE, "/sets"), ApiObjectList.SetList.class));
		} catch (MalformedURLException e) {
			throw new AssertionError(e);
		}
	}

	public PagedList<Card> cards() {
		try {
			return new PagedList<>(this, requestJson(new URL(BASE, "/cards"), ApiObjectList.CardList.class));
		} catch (MalformedURLException e) {
			throw new AssertionError(e);
		}
	}

	public List<Card> cardsBulk() {
		try {
			return requestJson(new URL("https://archive.scryfall.com/json/scryfall-default-cards.json"), new TypeToken<List<Card>>(){}.getType());
		} catch (MalformedURLException e) {
			throw new AssertionError(e);
		}
	}

	public PagedList<Card> query(String syntax) {
		return query(syntax, "prints", false, false);
	}

	public PagedList<Card> query(String syntax, String unique, boolean include_extras, boolean include_multilingual) {
		try {
			String query = URLEncoder.encode(syntax, "UTF-8");
			return new PagedList<>(this, requestJson(new URL(BASE, String.format("/cards/search?q=%s&unique=%s&include_extras=%s&include_multilingual=%s", query, unique, Boolean.toString(include_extras), Boolean.toString(include_multilingual))), ApiObjectList.CardList.class));
		} catch (MalformedURLException | UnsupportedEncodingException e) {
			throw new AssertionError(e);
		}
	}

	public static void main(String[] args) throws IOException {
		ScryfallApi api = new ScryfallApi();

		long start = System.nanoTime();
		List<Card> results = api.cardsBulk();
		System.out.println(String.format("Took %.2f seconds to download %d cards.", (System.nanoTime() - start) / 1e9, results.size()));

		try (FileWriter writer = new FileWriter("standard.json")) {
			GSON.toJson(results, new TypeToken<List<Card>>(){}.getType(), writer);
		}
	}
}
