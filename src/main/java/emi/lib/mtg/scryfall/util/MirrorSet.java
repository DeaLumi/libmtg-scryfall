package emi.lib.mtg.scryfall.util;

import java.util.*;
import java.util.function.Consumer;

public class MirrorSet<T> implements Set<T> {
	public static <T> MirrorSet<T> of(Map<?, T> map) {
		return new MirrorSet<>(map);
	}

	private static class MirrorIterator<T> implements Iterator<T> {
		private final Iterator<Map.Entry<?, T>> backing;

		public MirrorIterator(Set<Map.Entry<?, T>> entrySet) {
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

	private final Map<?, T> backing;

	public MirrorSet(Map<?, T> backing) {
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
		return new MirrorIterator(backing.entrySet());
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
