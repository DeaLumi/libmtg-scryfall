package emi.lib.mtg.scryfall.serde;

import emi.lib.mtg.scryfall.ScryfallPreferences;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public interface ScryfallSerde extends AutoCloseable {
	enum Implementation {
		Json (".json.gz", Json.class),
		MessagePack (".msgpack.gz", MessagePack.class);

		public final String extension;
		final Class<? extends ScryfallSerde> implClass;

		Implementation(String extension, Class<? extends ScryfallSerde> implClass) {
			this.extension = extension;
			this.implClass = implClass;
		}
	}

	enum State {
		Inactive,
		Header,
		Sets,
		Cards,
		Footer;
	}

	static ScryfallSerde get() {
		return get(ScryfallPreferences.get().serde);
	}

	static ScryfallSerde get(Implementation impl) {
		switch (impl) {
			case Json:
				return new Json();
			case MessagePack:
				return new MessagePack();
			default:
				throw new AssertionError(impl.name());
		}
	}

	Implementation type();

	void startWriting(Path file) throws IOException;
	void writeStartSets(int count) throws IOException;
	void writeSet(emi.lib.mtg.scryfall.api.Set set) throws IOException;
	void writeEndSets() throws IOException;
	void writeStartCards(int count) throws IOException;
	void writeCard(emi.lib.mtg.scryfall.api.Card card) throws IOException;
	void writeEndCards() throws IOException;
	void endWriting() throws IOException;

	void startReading(Path file) throws IOException;
	void readStartSets() throws IOException;
	boolean hasNextSet() throws IOException;
	emi.lib.mtg.scryfall.api.Set nextSet() throws IOException;
	void readEndSets() throws IOException;
	int readStartCards() throws IOException;
	boolean hasNextCard() throws IOException;
	emi.lib.mtg.scryfall.api.Card nextCard() throws IOException;
	void readEndCards() throws IOException;
	void endReading() throws IOException;

	static void expect(Object input, Object expected) throws IOException {
		if (!Objects.equals(input, expected)) {
			throw new IOException(String.format("Expected to see \'%s\', but got \'%s\' instead!", Objects.toString(input), Objects.toString(expected)));
		}
	}

}
