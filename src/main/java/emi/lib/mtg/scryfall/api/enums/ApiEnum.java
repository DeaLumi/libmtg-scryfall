package emi.lib.mtg.scryfall.api.enums;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public interface ApiEnum {
	String serialized();

	static TypeAdapterFactory typeAdapterFactory() {
		return new TypeAdapterFactory() {
			@Override
			public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
				if (!ApiEnum.class.isAssignableFrom(typeToken.getRawType())) {
					return null;
				}

				final Map<String, T> revMap = Arrays.stream(((Class<T>) typeToken.getRawType()).getEnumConstants())
						.collect(Collectors.toMap(e -> ((ApiEnum) e).serialized().toLowerCase(), e -> e));

				assert revMap.containsKey("unrecognized") : "Missing an unrecognized enum value from enum type " + typeToken.toString();

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
						switch (jsonReader.peek()) {
							case NULL:
								return null;
							case NAME:
								String nextName = jsonReader.nextName().toLowerCase().replace("_", "");
								return revMap.getOrDefault(nextName, revMap.get("unrecognized"));
							case STRING:
								String next = jsonReader.nextString().toLowerCase().replace("_", "");
								return revMap.getOrDefault(next, revMap.get("unrecognized"));
							default:
								throw new IOException("Unexpected token");
						}
					}
				};
			}
		};
	}
}
