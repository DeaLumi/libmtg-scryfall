package emi.lib.mtg.scryfall.serde;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import emi.lib.mtg.scryfall.api.ScryfallApi;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Json implements ScryfallSerde {
	private final Gson gson;
	private JsonWriter writer;
	private JsonReader reader;
	private State readState, writeState;

	public Json() {
		this(ScryfallApi.GSON);
	}

	public Json(Gson gson) {
		this.gson = gson;
		this.writer = null;
		this.reader = null;
		this.readState = State.Inactive;
		this.writeState = State.Inactive;
	}

	private void checkWriteState(State against) {
		if (writeState != against)
			throw new IllegalStateException(String.format("%s writer expected %s state, but is in %s state!", getClass().getName(), against, writeState));
	}

	private void checkReadState(State against) {
		if (readState != against)
			throw new IllegalStateException(String.format("%s writer expected %s state, but is in %s state!", getClass().getName(), against, writeState));
	}

	@Override
	public Implementation type() {
		return Implementation.Json;
	}

	@Override
	public void startWriting(Path file) throws IOException {
		if (writer != null) writer.close();
		writer = new JsonWriter(new PrintWriter(new GZIPOutputStream(Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))));
		writer.beginObject();
		writeState = State.Header;
	}

	@Override
	public void writeStartSets(int count) throws IOException {
		checkWriteState(State.Header);
		writer.name("sets");
		writer.beginObject();
		writeState = State.Sets;
	}

	@Override
	public void writeSet(emi.lib.mtg.scryfall.api.Set set) throws IOException {
		checkWriteState(State.Sets);
		writer.name(set.code);
		gson.toJson(set, emi.lib.mtg.scryfall.api.Set.class, writer);
	}

	@Override
	public void writeEndSets() throws IOException {
		checkWriteState(State.Sets);
		writer.endObject();
	}

	@Override
	public void writeStartCards(int count) throws IOException {
		checkWriteState(State.Sets);
		writer.name("cards");
		writer.beginObject();
		writer.name("count");
		writer.value(count);
		writeState = State.Cards;
	}

	@Override
	public void writeCard(emi.lib.mtg.scryfall.api.Card card) throws IOException {
		checkWriteState(State.Cards);
		writer.name(card.id.toString());
		gson.toJson(card, emi.lib.mtg.scryfall.api.Card.class, writer);
	}

	@Override
	public void writeEndCards() throws IOException {
		checkWriteState(State.Cards);
		writer.endObject();
		writeState = State.Footer;
	}

	@Override
	public void endWriting() throws IOException {
		checkWriteState(State.Footer);
		writer.endObject();
		writer.close();
		writer = null;
		writeState = State.Inactive;
	}

	@Override
	public void startReading(Path file) throws IOException {
		if (reader != null) reader.close();
		reader = gson.newJsonReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8));
		reader.beginObject();
		readState = State.Header;
	}

	@Override
	public void readStartSets() throws IOException {
		checkReadState(State.Header);
		ScryfallSerde.expect(reader.nextName(), "sets");
		reader.beginObject();
		readState = State.Sets;
	}

	@Override
	public boolean hasNextSet() throws IOException, IllegalStateException {
		checkReadState(State.Sets);
		return reader.peek() == JsonToken.NAME;
	}

	@Override
	public emi.lib.mtg.scryfall.api.Set nextSet() throws IOException, IllegalStateException {
		checkReadState(State.Sets);
		String setCode = reader.nextName();
		emi.lib.mtg.scryfall.api.Set set = gson.fromJson(reader, emi.lib.mtg.scryfall.api.Set.class);
		ScryfallSerde.expect(setCode, set.code);
		return set;
	}

	@Override
	public void readEndSets() throws IOException {
		checkReadState(State.Sets);
		reader.endObject();
	}

	@Override
	public int readStartCards() throws IOException {
		checkReadState(State.Sets);
		ScryfallSerde.expect(reader.nextName(), "cards");
		reader.beginObject();
		ScryfallSerde.expect(reader.nextName(), "count");
		readState = State.Cards;
		return reader.nextInt();
	}

	@Override
	public boolean hasNextCard() throws IOException {
		checkReadState(State.Cards);
		return reader.peek() == JsonToken.NAME;
	}

	@Override
	public emi.lib.mtg.scryfall.api.Card nextCard() throws IOException {
		checkReadState(State.Cards);
		String id = reader.nextName();
		emi.lib.mtg.scryfall.api.Card card = gson.fromJson(reader, emi.lib.mtg.scryfall.api.Card.class);
		ScryfallSerde.expect(id, card.id.toString());
		return card;
	}

	@Override
	public void readEndCards() throws IOException {
		checkReadState(State.Cards);
		reader.endObject();
		readState = State.Footer;
	}

	@Override
	public void endReading() throws IOException {
		checkReadState(State.Footer);
		reader.endObject();
		reader.close();
		reader = null;
		readState = State.Inactive;
	}

	@Override
	public void close() throws Exception {
		if (reader != null) reader.close();
		if (writer != null) writer.close();
	}
}
