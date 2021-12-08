package emi.lib.mtg.scryfall.api;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import emi.lib.mtg.scryfall.api.enums.ApiEnum;
import emi.lib.mtg.scryfall.api.enums.BulkDataType;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.DoubleConsumer;
import java.util.function.LongConsumer;
import java.util.zip.GZIPInputStream;

public class ScryfallApi {
	private static final int REQUEST_PAUSE = 250;
	private static final URL URL_BASE;
	public static final Gson GSON;

	private static final URL URL_SETS;
	private static final URL URL_BULK;

	private static TypeAdapter<Instant> instantAdapter() {
		return new TypeAdapter<Instant>() {
			@Override
			public void write(JsonWriter out, Instant value) throws IOException {
				out.value(value.atOffset(ZoneOffset.UTC).toString());
			}

			@Override
			public Instant read(JsonReader in) throws IOException {
				String sval = "";
				switch (in.peek()) {
					case NAME:
						sval = in.nextName();
						break;
					case STRING:
						sval = in.nextString();
						break;
					default:
						assert false;
						return Instant.MIN;
				}
				return Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(sval));
			}
		};
	}

	private static TypeAdapter<LocalDate> localDateAdapter() {
		return new TypeAdapter<LocalDate>() {
			@Override
			public void write(JsonWriter out, LocalDate value) throws IOException {
				out.value(value.toString());
			}

			@Override
			public LocalDate read(JsonReader in) throws IOException {
				String sval = "";
				switch (in.peek()) {
					case NAME:
						sval = in.nextName();
						break;
					case STRING:
						sval = in.nextString();
						break;
					default:
						assert false;
						return LocalDate.MIN;
				}
				return LocalDate.from(DateTimeFormatter.ISO_DATE.parse(sval));
			}
		};
	}

	static {
		try {
			URL_BASE = new URL("https://api.scryfall.com/");
			URL_SETS = new URL(URL_BASE, "/sets");
			URL_BULK = new URL(URL_BASE, "/bulk-data");
		} catch (MalformedURLException mue) {
			throw new Error(mue);
		}

		GsonBuilder builder = new GsonBuilder();

		// Register enum type adapters
		builder.registerTypeAdapterFactory(ApiEnum.typeAdapterFactory());
		builder.registerTypeAdapter(Instant.class, instantAdapter());
		builder.registerTypeAdapter(LocalDate.class, localDateAdapter());

		builder.enableComplexMapKeySerialization();
		builder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);


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

	private <T> ScheduledFuture<T> requestJsonAsync(URL url, Type type, LongConsumer reporter) {
		long delay;
		synchronized(this) {
			long now = System.currentTimeMillis();
			nextRequest = Math.max(nextRequest + REQUEST_PAUSE, now);
			delay = now - nextRequest;
		}

		return executor.schedule(() -> {
			HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
			connection.setRequestProperty("Accept-Encoding", "gzip");

			if (connection.getResponseCode() != 200) {
				// TODO: Handle errors...
				System.err.println("HTTP request for " + url.toString() + " failed: " + connection.getResponseMessage());
				return null;
			}

			InputStream input = connection.getInputStream();
			if (reporter != null) {
				input = new ReportingWrapper(input, reporter);
			}

			if ("gzip".equals(connection.getContentEncoding())) {
				input = new GZIPInputStream(input);
			}

			return GSON.fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), type);
		}, delay, TimeUnit.MILLISECONDS);
	}

	public <T> T requestJson(URL url, Type type, LongConsumer reporter) throws IOException {
		ScheduledFuture<T> downloadTask = requestJsonAsync(url, type, reporter);

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
			throw new IOException(e.getCause());
		}
	}

	public <T> T requestJson(URL url, Type type) throws IOException {
		return requestJson(url, type, null);
	}

	public <T> T requestJson(URL url, Class<T> cls, LongConsumer reporter) throws IOException {
		return requestJson(url, (Type) cls, reporter);
	}

	public <T> T requestJson(URL url, Class<T> cls) throws IOException {
		return requestJson(url, cls, null);
	}

	public PagedList<Set> sets() throws IOException {
		return new PagedList<>(this, requestJson(URL_SETS, ApiObjectList.SetList.class, null));
	}

	public BulkDataList bulkData() throws IOException {
		return requestJson(URL_BULK, BulkDataList.class, null);
	}

	public List<Card> defaultCardsBulk(DoubleConsumer progress) throws IOException {
		BulkDataList bulk = bulkData();
		BulkDataList.Entry defaultCards = bulk.data.stream().filter(x -> x.type == BulkDataType.DefaultCards).findAny().orElse(null);

		if (defaultCards == null) throw new AssertionError(new IOException("Couldn't find scryfall bulk default card data URI!"));

		return requestJson(defaultCards.downloadUri.toURL(), new TypeToken<List<Card>>(){}.getType(), l -> progress.accept((double) l / defaultCards.compressedSize));
	}

	public PagedList<Card> query(String syntax) throws IOException {
		return query(syntax, "prints", false, false);
	}

	public PagedList<Card> query(String syntax, String unique, boolean include_extras, boolean include_multilingual) throws IOException {
		String query = URLEncoder.encode(syntax, "UTF-8");
		return new PagedList<>(this, requestJson(new URL(URL_BASE, String.format("/cards/search?q=%s&unique=%s&include_extras=%s&include_multilingual=%s", query, unique, Boolean.toString(include_extras), Boolean.toString(include_multilingual))), ApiObjectList.CardList.class));
	}

	public static void main(String[] args) throws IOException {
		ScryfallApi api = new ScryfallApi();

		long start = System.nanoTime();
		System.out.print("Cards:     ");
		List<Card> results = api.defaultCardsBulk(x -> System.out.print(String.format("\033[4D% 3d%%", (int) (x * 100.0))));
		System.out.println();
		System.out.println(String.format("Took %.2f seconds to download %d cards.", (System.nanoTime() - start) / 1e9, results.size()));

		try (Writer writer = Files.newBufferedWriter(new File("standard.json").toPath())) {
			GSON.toJson(results, new TypeToken<List<Card>>(){}.getType(), writer);
		}
	}
}
