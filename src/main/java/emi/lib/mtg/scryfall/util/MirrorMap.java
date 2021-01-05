package emi.lib.mtg.scryfall.util;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class MirrorMap<K, V> implements Map<K, V> {
	private final Map<K, V> backing;
	private final MirrorSet<K, V> mirror;

	public MirrorMap(Supplier<Map<K, V>> backingFactory) {
		this.backing = backingFactory.get();
		this.mirror = MirrorSet.of(this.backing);
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
	public boolean containsKey(Object key) {
		return backing.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return backing.containsValue(value);
	}

	@Override
	public V get(Object key) {
		return backing.get(key);
	}

	@Override
	public V put(K key, V value) {
		return backing.put(key, value);
	}

	@Override
	public V remove(Object key) {
		return backing.remove(key);
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		backing.putAll(m);
	}

	@Override
	public void clear() {
		backing.clear();
	}

	@Override
	public Set<K> keySet() {
		return backing.keySet();
	}

	@Override
	public Collection<V> values() {
		return backing.values();
	}

	/**
	 * Returns an unmodifiable mirrored view of the map's values, as a set.
	 * Note that this probably breaks the set contract -- an iterator may
	 * return the same value multiple times, since the backing map is not
	 * guaranteed reversible. Use with care.
	 * @return The map's values, imitating a set.
	 */
	public Set<V> valueSet() {
		return mirror;
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		return backing.entrySet();
	}

	@Override
	public boolean equals(Object o) {
		return backing.equals(o);
	}

	@Override
	public int hashCode() {
		return backing.hashCode();
	}

	@Override
	public V getOrDefault(Object key, V defaultValue) {
		return backing.getOrDefault(key, defaultValue);
	}

	@Override
	public void forEach(BiConsumer<? super K, ? super V> action) {
		backing.forEach(action);
	}

	@Override
	public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
		backing.replaceAll(function);
	}

	@Override
	public V putIfAbsent(K key, V value) {
		return backing.putIfAbsent(key, value);
	}

	@Override
	public boolean remove(Object key, Object value) {
		return backing.remove(key, value);
	}

	@Override
	public boolean replace(K key, V oldValue, V newValue) {
		return backing.replace(key, oldValue, newValue);
	}

	@Override
	public V replace(K key, V value) {
		return backing.replace(key, value);
	}

	@Override
	public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
		return backing.computeIfAbsent(key, mappingFunction);
	}

	@Override
	public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		return backing.computeIfPresent(key, remappingFunction);
	}

	@Override
	public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		return backing.compute(key, remappingFunction);
	}

	@Override
	public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
		return backing.merge(key, value, remappingFunction);
	}
}
