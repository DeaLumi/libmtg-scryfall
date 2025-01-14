package emi.lib.mtg.scryfall.api;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import emi.lib.mtg.scryfall.api.enums.ApiEnum;
import emi.lib.mtg.scryfall.api.enums.BulkDataType;
import emi.lib.mtg.scryfall.util.ReportingWrapper;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

	public static class HttpException extends IOException {
		public static class ErrorObject {
			public int status;
			public String code;
			public String type;
			public String details;
			public String[] warnings;
		}

		public final URL url;
		public final int errorCode;
		public final String errorMessage;
		public final String responseBody;
		public final ErrorObject object;

		public HttpException(URL url, int errorCode, String errorMessage, String responseBody) {
			super(String.format("GET %s returned HTTP %d %s:\n\n%s", url, errorCode, errorMessage, responseBody));

			this.url = url;
			this.errorCode = errorCode;
			this.errorMessage = errorMessage;
			this.responseBody = responseBody;

			ErrorObject obj;
			try {
				obj = GSON.fromJson(responseBody, ErrorObject.class);
			} catch (JsonSyntaxException jse) {
				obj = null;
			}
			this.object = obj;
		}
	}

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
		builder.setPrettyPrinting();

		GSON = builder.create();
	}

	private static ScryfallApi INSTANCE;

	public static ScryfallApi get() {
		synchronized (ScryfallApi.class) {
			if (INSTANCE == null) {
				INSTANCE = new ScryfallApi();
			}
		}

		return INSTANCE;
	}

	private static class Request extends CompletableFuture<InputStream> {
		public final URL url;
		public final String contentType;
		public final LongConsumer reporter;

		public Request(URL url, String contentType, LongConsumer reporter) {
			this.url = url;
			this.contentType = contentType;
			this.reporter = reporter;
		}
	}

	private final BlockingDeque<Request> requestQueue;
	private final Thread thread;
	private volatile long nextRequest;

	public ScryfallApi() {
		this.requestQueue = new LinkedBlockingDeque<>();

		this.thread = new Thread("Scryfall API Thread") {
			@Override
			public void run() {
				while (!Thread.interrupted()) {
					try {
						Thread.sleep(Math.max(1, nextRequest - System.currentTimeMillis()));
						Request request = requestQueue.take();

						try {
							HttpsURLConnection connection = (HttpsURLConnection) request.url.openConnection();
							connection.setRequestProperty("Accept", String.format("%s;q=0.9,*/*;q=0.8", request.contentType));
							connection.setRequestProperty("Accept-Encoding", "gzip");
							connection.setRequestProperty("user-agent", "emi.lib.mtg.scryfall via java.net");

							if (connection.getResponseCode() != 200) {
								InputStream err = connection.getErrorStream();
								if ("gzip".equals(connection.getContentEncoding())) err = new GZIPInputStream(err);

								StringBuilder errStr = new StringBuilder(connection.getContentLength());
								byte[] buffer = new byte[4096];
								int read;
								while ((read = err.read(buffer)) >= 0) errStr.append(new String(buffer, 0, read));
								err.close();

								throw new HttpException(request.url, connection.getResponseCode(), connection.getResponseMessage(), errStr.toString());
							}

							InputStream input = connection.getInputStream();
							if ("gzip".equals(connection.getContentEncoding())) input = new GZIPInputStream(input);
							if (request.reporter != null) input = new ReportingWrapper(input, request.reporter);

							request.complete(input);
						} catch (IOException ioe) {
							request.completeExceptionally(ioe);
						}

						nextRequest = System.currentTimeMillis() + REQUEST_PAUSE;
					} catch (InterruptedException e) {
						break;
					}
				}
			}
		};

		this.thread.setDaemon(true);
		this.thread.start();

		this.nextRequest = System.currentTimeMillis();
	}

	public CompletableFuture<InputStream> getURL(URL url, String contentType, LongConsumer reporter, boolean preempt) {
		Request request = new Request(url, contentType, reporter);

		if (preempt) {
			requestQueue.addFirst(request);
		} else {
			requestQueue.addLast(request);
		}

		return request;
	}

	private <T> CompletableFuture<T> requestJsonAsync(URL url, Type type, LongConsumer reporter) {
		return getURL(url, "application/json", reporter, false)
				.thenApply(is -> GSON.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), type));
	}

	public <T> T requestJson(URL url, Type type, LongConsumer reporter) throws IOException {
		try {
			return this.<T>requestJsonAsync(url, type, reporter).get();
		} catch (InterruptedException e) {
			return null;
		} catch (ExecutionException e) {
			if (e.getCause() instanceof HttpException) {
				throw (HttpException) e.getCause();
			} else if (e.getCause() instanceof IOException) {
				throw (IOException) e.getCause();
			} else {
				throw new IOException(e.getCause());
			}
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

		return requestJson(defaultCards.downloadUri.toURL(), new TypeToken<List<Card>>(){}.getType(), l -> progress.accept((double) l / defaultCards.size));
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
