package emi.lib.mtg.scryfall.api;

import emi.lib.mtg.scryfall.api.enums.BulkDataType;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public class BulkDataList extends ApiObjectList<BulkDataList.Entry> {
	public static class Entry extends ApiObject {
		public UUID id;
		public URI uri;
		public BulkDataType type;
		public String name;
		public String description;
		public URI downloadUri;
		public Instant updatedAt;
		public long compressedSize;
		public String contentType;
		public String contentEncoding;
	}
}
