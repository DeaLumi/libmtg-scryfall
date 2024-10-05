package emi.lib.mtg.scryfall.api.enums;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public interface ApiEnum {
	String serialized();

	class Serialization {
		private static final Map<Class<? extends ApiEnum>, Map<String, ? extends ApiEnum>> REV_MAPS = new HashMap<>();

		public static <T extends ApiEnum> Map<String, T> revMap(Class<T> type) {
			return (Map<String, T>) revMapRaw(type);
		}

		public static Map<String, ? extends ApiEnum> revMapRaw(Class<? extends ApiEnum> type) {
			Map<String, ? extends ApiEnum> val = REV_MAPS.get(type);

			if (val == null) {
				val = Arrays.stream(type.getEnumConstants())
						.collect(Collectors.toMap(e -> e.serialized().toLowerCase(), e -> e));

				assert val.containsKey("unrecognized") : "Missing an unrecognized enum value from enum type " + type.getCanonicalName();

				REV_MAPS.put(type, val);
			}

			return val;
		}

		public static <T extends ApiEnum> T orUnrecognized(Class<T> type, String name) {
			Map<String, T> map = revMap(type);
			return map.getOrDefault(name.toLowerCase(), map.get("unrecognized"));
		}
	}

	static TypeAdapterFactory typeAdapterFactory() {
		return new TypeAdapterFactory() {
			@Override
			public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
				if (!ApiEnum.class.isAssignableFrom(typeToken.getRawType())) {
					return null;
				}

				final Map<String, T> revMap = (Map<String, T>) Serialization.revMapRaw((Class<? extends ApiEnum>) typeToken.getRawType());
				T unrecognized = revMap.get("unrecognized");

				return new TypeAdapter<T>() {
					@Override
					public void write(JsonWriter jsonWriter, T e) throws IOException {
						if (e == null) {
							jsonWriter.nullValue();
						} else {
							jsonWriter.value(((ApiEnum) e).serialized());
						}
					}

					@Override
					public T read(JsonReader jsonReader) throws IOException {
						String name;
						switch (jsonReader.peek()) {
							case NULL:
								return null;
							case NAME:
								name = jsonReader.nextName().toLowerCase().replace("_", "");
								break;
							case STRING:
								name = jsonReader.nextString().toLowerCase().replace("_", "");
								break;
							default:
								throw new IOException("Unexpected token");
						}

						if (!revMap.containsKey(name)) {
							System.err.println("WARNING: Unrecognized " + typeToken.getRawType().getSimpleName() + " \"" + name + "\"! Errors may ensue!");
							return unrecognized;
						}

						return revMap.get(name);
					}
				};
			}
		};
	}
}
