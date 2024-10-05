package emi.lib.mtg.scryfall.serde;

import emi.lib.mtg.scryfall.api.ApiObject;
import emi.lib.mtg.scryfall.api.enums.ApiEnum;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class MessagePack implements ScryfallSerde {
	private static final Map<Class<?>, Map<String, Field>> REFLECTION_MAP = new HashMap<>();

	private static Map<String, Field> reflectionMap(Class<?> type) {
		assert Object.class.isAssignableFrom(type) : "Attempt to build reflection map for non-Object type " + type;
		assert type.getPackage().getName().startsWith("emi.lib.mtg.scryfall.api") : "Attempt to break containment with " + type;

		Map<String, Field> fieldsMap = REFLECTION_MAP.get(type);
		if (fieldsMap != null) return fieldsMap;

		fieldsMap = new HashMap<>();
		for (Field f : type.getFields()) {
			if ((f.getModifiers() & Modifier.TRANSIENT) != 0) continue;

			fieldsMap.put(f.getName(), f);
		}

		REFLECTION_MAP.put(type, fieldsMap);
		return fieldsMap;
	}

	public static void packApiObject(MessagePacker packer, ApiObject object) throws IOException {
		packObject(packer, object);
	}

	public static <T extends ApiObject> T unpackApiObject(MessageUnpacker unpacker, Class<T> type) throws IOException {
		return unpackObject(unpacker, type);
	}

	public static <T> void packObject(MessagePacker packer, T object) throws IOException {
		try {
			Map<String, Field> fields = reflectionMap(object.getClass());
			int fieldCount = fields.size();
			packer.packMapHeader(fieldCount);
			for (Map.Entry<String, Field> e : fields.entrySet()) {
				packer.packString(e.getKey());
				packField(packer, object, e.getValue());
			}
		} catch (IllegalAccessException iae) {
			throw new IOException("Unable to pack object " + object + " via reflection", iae);
		}
	}

	public static <T> T unpackObject(MessageUnpacker unpacker, Class<T> type) throws IOException {
		try {
			T object = type.newInstance();

			Map<String, Field> fields = reflectionMap(type);
			int fieldCount = unpacker.unpackMapHeader();
			for (int i = 0; i < fieldCount; ++i) {
				String fieldName = unpacker.unpackString();
				Field f = fields.get(fieldName);
				if (f == null) throw new IOException("Unrecognized field name " + fieldName + " in type " + type.getCanonicalName()); // TODO Be more lenient?
				unpackField(unpacker, object, f);
			}

			return object;
		} catch (InstantiationException | IllegalAccessException iae) {
			throw new IOException("Unable to unpack object of type " + type.getCanonicalName() + " via reflection", iae);
		}
	}

	private static void packReference(MessagePacker packer, Class<?> type, Object value) throws IOException {
		if (value == null) {
			packer.packNil();
		} else if (type == String.class) {
			packer.packString((String) value);
		} else if (type == Integer.class) {
			packer.packInt((Integer) value);
		} else if (type == Double.class) {
			packer.packDouble((Double) value);
		} else if (type == LocalDate.class) {
			packer.packLong(((LocalDate) value).toEpochDay());
		} else if (type == URL.class) {
			packer.packString(((URL) value).toExternalForm());
		} else if (type == UUID.class) {
			packer.packLong(((UUID) value).getMostSignificantBits());
			packer.packLong(((UUID) value).getLeastSignificantBits());
		} else if (ApiEnum.class.isAssignableFrom(type)) {
			packer.packString(((ApiEnum) value).serialized());
		} else if (type.getPackage().getName().startsWith("emi.lib.mtg.scryfall.api")) {
			packObject(packer, value);
		} else {
			throw new IOException("Unhandled reference type " + type.getCanonicalName());
		}
	}

	private static Object unpackReference(MessageUnpacker unpacker, Class<?> type) throws IOException {
		if (unpacker.tryUnpackNil()) return null;

		if (type == String.class) {
			return unpacker.unpackString();
		} else if (type == Integer.class) {
			return unpacker.unpackInt();
		} else if (type == Double.class) {
			return unpacker.unpackDouble();
		} else if (type == LocalDate.class) {
			return LocalDate.ofEpochDay(unpacker.unpackLong());
		} else if (type == URL.class) {
			return new URL(unpacker.unpackString());
		} else if (type == UUID.class) {
			return new UUID(unpacker.unpackLong(), unpacker.unpackLong());
		} else if (ApiEnum.class.isAssignableFrom(type)) {
			return ApiEnum.Serialization.orUnrecognized((Class<? extends ApiEnum>) type, unpacker.unpackString());
		} else if (type.getPackage().getName().startsWith("emi.lib.mtg.scryfall.api")) {
			return unpackObject(unpacker, type);
		} else {
			throw new IOException("Unhandled reference type " + type.getCanonicalName());
		}
	}

	private static void packCollection(MessagePacker packer, Type fullType, Collection<?> collection) throws IOException {
		if (collection == null) {
			packer.packNil();
			return;
		}

		if (!(fullType instanceof ParameterizedType)) throw new IOException("Unable to pack collection with type " + fullType + ": not a ParameterizedType");
		Type param0 = ((ParameterizedType) fullType).getActualTypeArguments()[0];
		if (!(param0 instanceof Class)) throw new IOException("Unable to pack collection with type " + fullType + ": param0 not a Class");
		Class<?> type = (Class<?>) param0;

		packer.packArrayHeader(collection.size());
		for (Object val : collection) packReference(packer, type, val);
	}

	private static Collection<?> unpackCollection(MessageUnpacker unpacker, Type fullType) throws IOException {
		if (unpacker.tryUnpackNil()) return null;

		if (!(fullType instanceof ParameterizedType)) throw new IOException("Unable to unpack collection with type " + fullType + ": not a ParameterizedType");
		Type rawType = ((ParameterizedType) fullType).getRawType();
		Type param0 = ((ParameterizedType) fullType).getActualTypeArguments()[0];
		if (!(rawType instanceof Class)) throw new IOException("Unable to unpack collection with type " + fullType + ": raw type not a Class");
		if (!(param0 instanceof Class)) throw new IOException("Unable to unpack collection with type " + fullType + ": param0 not a Class");
		Class<?> raw = (Class<?>) rawType;
		Class<?> type = (Class<?>) param0;
		if (!Collection.class.isAssignableFrom(raw)) throw new IOException("Unable to unpack collection with type " + fullType + ": Raw type is somehow not collection: " + type);
		if (!Object.class.isAssignableFrom(type)) throw new IOException("Unable to unpack collection with type " + fullType + ": Component type is somehow fundamental: " + type);

		Collection collection;
		if (!raw.isInterface() && (raw.getModifiers() & Modifier.ABSTRACT) == 0) {
			try {
				collection = (Collection<?>) raw.newInstance();
			} catch (InstantiationException | IllegalAccessException iae) {
				throw new IOException(iae);
			}
		} else if (raw.isAssignableFrom(ArrayList.class)) {
			collection = new ArrayList<>();
		} else if (raw.isAssignableFrom(HashSet.class)) {
			collection = new HashSet<>();
		} else {
			throw new IOException("Can't figure out what type to make for " + fullType);
		}

		int size = unpacker.unpackArrayHeader();
		for (int i = 0; i < size; ++i) collection.add(unpackReference(unpacker, type));
		return collection;
	}

	private static void packMap(MessagePacker packer, Type fullType, Map<?, ?> map) throws IOException {
		if (map == null) {
			packer.packNil();
			return;
		}

		if (!(fullType instanceof ParameterizedType)) throw new IOException("Unable to pack map with type " + fullType + ": not a ParameterizedType");
		Type param0 = ((ParameterizedType) fullType).getActualTypeArguments()[0];
		Type param1 = ((ParameterizedType) fullType).getActualTypeArguments()[1];
		if (!(param0 instanceof Class)) throw new IOException("Unable to pack map with type " + fullType + ": param0 not a Class");
		if (!(param1 instanceof Class)) throw new IOException("Unable to pack map with type " + fullType + ": param1 not a Class");
		Class<?> keyType = (Class<?>) param0;
		Class<?> valueType = (Class<?>) param1;

		packer.packMapHeader(map.size());
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			packReference(packer, keyType, entry.getKey());
			packReference(packer, valueType, entry.getValue());
		}
	}

	private static Map<?, ?> unpackMap(MessageUnpacker unpacker, Type fullType) throws IOException {
		if (unpacker.tryUnpackNil()) return null;

		if (!(fullType instanceof ParameterizedType)) throw new IOException("Unable to unpack map with type " + fullType + ": not a ParameterizedType");
		Type rawType = ((ParameterizedType) fullType).getRawType();
		Type param0 = ((ParameterizedType) fullType).getActualTypeArguments()[0];
		Type param1 = ((ParameterizedType) fullType).getActualTypeArguments()[1];
		if (!(rawType instanceof Class)) throw new IOException("Unable to unpack map with type " + fullType + ": raw type not a Class");
		if (!(param0 instanceof Class)) throw new IOException("Unable to unpack map with type " + fullType + ": param0 not a Class");
		if (!(param1 instanceof Class)) throw new IOException("Unable to unpack map with type " + fullType + ": param1 not a Class");
		Class<?> raw = (Class<?>) rawType;
		Class<?> keyType = (Class<?>) param0;
		Class<?> valueType = (Class<?>) param1;
		if (!Map.class.isAssignableFrom(raw)) throw new IOException("Unable to unpack map with type " + fullType + ": Somehow, raw type is not map: " + raw);
		if (!Object.class.isAssignableFrom(keyType)) throw new IOException("Unable to unpack map with type " + fullType + ": Somehow, key type is fundamental " + keyType);
		if (!Object.class.isAssignableFrom(valueType)) throw new IOException("Unable to unpack map with type " + fullType + ": Somehow, value type is fundamental " + valueType);

		Map map;
		if (!raw.isInterface() && (raw.getModifiers() & Modifier.ABSTRACT) == 0) {
			try {
				map = (Map<?, ?>) raw.newInstance();
			} catch (InstantiationException | IllegalAccessException iae) {
				throw new IOException(iae);
			}
		} else if (raw.isAssignableFrom(HashMap.class)) {
			map = new HashMap<>();
		} else if (raw.isAssignableFrom(EnumMap.class)) {
			map = new EnumMap(keyType);
		} else if (raw.isAssignableFrom(SortedMap.class)) {
			map = new LinkedHashMap<>();
		} else {
			throw new IOException("Can't figure out what type to make for " + fullType);
		}

		int pairs = unpacker.unpackMapHeader();
		for (int i = 0; i < pairs; ++i) map.put(unpackReference(unpacker, keyType), unpackReference(unpacker, valueType));
		return map;
	}

	private static void packField(MessagePacker packer, Object object, Field f) throws IOException, IllegalAccessException {
		if (f.getType() == int.class) {
			packer.packInt(f.getInt(object));
		} else if (f.getType() == boolean.class) {
			packer.packBoolean(f.getBoolean(object));
		} else if (Collection.class.isAssignableFrom(f.getType())) {
			try {
				packCollection(packer, f.getGenericType(), (Collection<?>) f.get(object));
			} catch (IOException ioe) {
				throw new IOException("On " + object.getClass().getCanonicalName() + " field " + f.getName(), ioe);
			}
		} else if (Map.class.isAssignableFrom(f.getType())) {
			try {
				packMap(packer, f.getGenericType(), (Map<?, ?>) f.get(object));
			} catch (IOException ioe) {
				throw new IOException("On " + object.getClass().getCanonicalName() + " field " + f.getName(), ioe);
			}
		} else if (Object.class.isAssignableFrom(f.getType())) {
			try {
				packReference(packer, f.getType(), f.get(object));
			} catch (IOException ioe) {
				throw new IOException("On " + object.getClass().getCanonicalName() + " field " + f.getName(), ioe);
			}
		} else {
			throw new IOException("Non-packable fundamental type " + f.getType().getCanonicalName() + " on " + object.getClass().getCanonicalName() + " field " + f.getName());
		}
	}

	private static void unpackField(MessageUnpacker unpacker, Object object, Field f) throws IOException, IllegalAccessException {
		if (f.getType() == int.class) {
			f.setInt(object, unpacker.unpackInt());
		} else if (f.getType() == boolean.class) {
			f.setBoolean(object, unpacker.unpackBoolean());
		} else if (Collection.class.isAssignableFrom(f.getType())) {
			try {
				f.set(object, unpackCollection(unpacker, f.getGenericType()));
			} catch (IOException ioe) {
				throw new IOException("On " + object.getClass().getCanonicalName() + " field " + f.getName(), ioe);
			}
		} else if (Map.class.isAssignableFrom(f.getType())) {
			try {
				f.set(object, unpackMap(unpacker, f.getGenericType()));
			} catch (IOException ioe) {
				throw new IOException("On " + object.getClass().getCanonicalName() + " field " + f.getName(), ioe);
			}
		} else if (Object.class.isAssignableFrom(f.getType())) {
			try {
				f.set(object, unpackReference(unpacker, f.getType()));
			} catch (IOException ioe) {
				throw new IOException("On " + object.getClass().getCanonicalName() + " field " + f.getName(), ioe);
			}
		} else {
			throw new IOException("Non-UNpackable fundamental type " + f.getType().getCanonicalName() + " on " + object.getClass().getCanonicalName() + " field " + f.getName());
		}
	}

	private MessagePacker writer;
	private MessageUnpacker reader;
	private State readState, writeState;
	private int remainingSets, remainingCards;

	public MessagePack() {
		this.writer = null;
		this.reader = null;
		this.readState = State.Inactive;
		this.writeState = State.Inactive;
		this.remainingSets = -1;
		this.remainingCards = -1;
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
		return Implementation.MessagePack;
	}

	@Override
	public void startWriting(Path file) throws IOException {
		if (writer != null) writer.close();
		writer = org.msgpack.core.MessagePack.newDefaultPacker(new GZIPOutputStream(Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)));
		writer.packMapHeader(2);
		writeState = State.Header;
	}

	@Override
	public void writeStartSets(int count) throws IOException {
		checkWriteState(State.Header);
		writer.packString("sets");
		writer.packMapHeader(count);
		writeState = State.Sets;
	}

	@Override
	public void writeSet(emi.lib.mtg.scryfall.api.Set set) throws IOException {
		checkWriteState(State.Sets);
		writer.packString(set.code);
		packApiObject(writer, set);
	}

	@Override
	public void writeEndSets() throws IOException {
		checkWriteState(State.Sets);
	}

	@Override
	public void writeStartCards(int count) throws IOException {
		checkWriteState(State.Sets);
		writer.packString("cards");
		writer.packMapHeader(count);
		writeState = State.Cards;
	}

	@Override
	public void writeCard(emi.lib.mtg.scryfall.api.Card card) throws IOException {
		checkWriteState(State.Cards);
		writer.packLong(card.id.getMostSignificantBits());
		writer.packLong(card.id.getLeastSignificantBits());
		packApiObject(writer, card);
	}

	@Override
	public void writeEndCards() throws IOException {
		checkWriteState(State.Cards);
		writeState = State.Footer;
	}

	@Override
	public void endWriting() throws IOException {
		checkWriteState(State.Footer);
		writer.close();
		writer = null;
		writeState = State.Inactive;
	}

	@Override
	public void startReading(Path file) throws IOException {
		if (reader != null) reader.close();
		reader = org.msgpack.core.MessagePack.newDefaultUnpacker(new GZIPInputStream(Files.newInputStream(file)));
		int len = reader.unpackMapHeader();
		ScryfallSerde.expect(len, 2);
		readState = State.Header;
	}

	@Override
	public void readStartSets() throws IOException {
		checkReadState(State.Header);
		ScryfallSerde.expect(reader.unpackString(), "sets");
		remainingSets = reader.unpackMapHeader();
		readState = State.Sets;
	}

	@Override
	public boolean hasNextSet() throws IOException, IllegalStateException {
		checkReadState(State.Sets);
		return remainingSets > 0;
	}

	@Override
	public emi.lib.mtg.scryfall.api.Set nextSet() throws IOException, IllegalStateException {
		checkReadState(State.Sets);
		String code = reader.unpackString();
		emi.lib.mtg.scryfall.api.Set set = unpackApiObject(reader, emi.lib.mtg.scryfall.api.Set.class);
		ScryfallSerde.expect(code, set.code);
		--remainingSets;
		return set;
	}

	@Override
	public void readEndSets() throws IOException {
		checkReadState(State.Sets);
		ScryfallSerde.expect(remainingSets, 0);
		remainingSets = -1;
	}

	@Override
	public int readStartCards() throws IOException {
		checkReadState(State.Sets);
		ScryfallSerde.expect(reader.unpackString(), "cards");
		remainingCards = reader.unpackMapHeader();
		readState = State.Cards;
		return remainingCards;
	}

	@Override
	public boolean hasNextCard() throws IOException {
		checkReadState(State.Cards);
		return remainingCards > 0;
	}

	@Override
	public emi.lib.mtg.scryfall.api.Card nextCard() throws IOException {
		checkReadState(State.Cards);
		UUID id = new UUID(reader.unpackLong(), reader.unpackLong());
		emi.lib.mtg.scryfall.api.Card card = unpackApiObject(reader, emi.lib.mtg.scryfall.api.Card.class);
		ScryfallSerde.expect(id, card.id);
		--remainingCards;
		return card;
	}

	@Override
	public void readEndCards() throws IOException {
		checkReadState(State.Cards);
		ScryfallSerde.expect(remainingCards, 0);
		remainingCards = -1;
		readState = State.Footer;
	}

	@Override
	public void endReading() throws IOException {
		checkReadState(State.Footer);
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
