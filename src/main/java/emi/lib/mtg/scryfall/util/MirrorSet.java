package emi.lib.mtg.scryfall.util;

import java.util.*;
import java.util.function.Consumer;

public class MirrorSet<K, T> implements Set<T> {
	public static <K, T> MirrorSet<K, T> of(Map<K, T> map) {
		return new MirrorSet<>(map);
	}

	private static class MirrorIterator<K, T> implements Iterator<T> {
		private final Iterator<Map.Entry<K, T>> backing;

		public MirrorIterator(Set<Map.Entry<K, T>> entrySet) {
			this.backing = entrySet.iterator();
		}

		@Override
		public boolean hasNext() {
			return backing.hasNext();
		}

		@Override
		public T next() {
			return backing.next().getValue();
		}

		@Override
		public void remove() {
			backing.remove();
		}

		@Override
		public void forEachRemaining(Consumer<? super T> action) {
			backing.forEachRemaining(e -> action.accept(e.getValue()));
		}
	}

	private final Map<K, T> backing;

	public MirrorSet(Map<K, T> backing) {
		this.backing = backing;
	}

	@Override
	public int size() {
		return backing.size();
	}

	@Override
	public boolean isEmpty() {
		return backing.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return backing.containsValue(o);
	}

	@Override
	public Iterator<T> iterator() {
		return new MirrorIterator<>(backing.entrySet());
	}

	@Override
	public Object[] toArray() {
		return backing.values().toArray();
	}

	@Override
	public <T1> T1[] toArray(T1[] a) {
		return backing.values().toArray(a);
	}

	@Override
	public boolean add(T t) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean remove(Object o) {
		return backing.entrySet().removeIf(e -> e.getValue() == o || e.getValue().hashCode() == o.hashCode() || Objects.equals(e.getValue(), o));
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return backing.values().containsAll(c);
	}

	@Override
	public boolean addAll(Collection<? extends T> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		return backing.entrySet().removeIf(e -> !c.contains(e.getValue()));
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		return backing.entrySet().removeIf(e -> c.contains(e.getValue()));
	}

	@Override
	public void clear() {
		backing.clear();
	}
}
