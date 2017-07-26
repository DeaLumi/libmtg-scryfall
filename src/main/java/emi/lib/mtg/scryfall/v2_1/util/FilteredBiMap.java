package emi.lib.mtg.scryfall.v2_1.util;

import com.google.common.collect.BiMap;

import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

public class FilteredBiMap<K, V> implements BiMap<K, V> {
	private final BiMap<K, V> backing;
	private final Predicate<V> predicate;

	public static <K, V> FilteredBiMap<K, V> of(BiMap<K, V> backing, Predicate<V> predicate) {
		return new FilteredBiMap<>(backing, predicate);
	}

	public FilteredBiMap(BiMap<K, V> backing, Predicate<V> predicate) {
		this.backing = backing;
		this.predicate = predicate;
	}

	@Override
	public int size() {
		return (int) backing.values().stream()
				.filter(predicate)
				.count();
	}

	@Override
	public boolean isEmpty() {
		return backing.isEmpty() || this.size() == 0;
	}

	@Override
	public boolean containsKey(Object key) {
		return backing.containsKey(key) && predicate.test(backing.get(key));
	}

	@Override
	public boolean containsValue(Object value) {
		return backing.containsValue(value) && predicate.test((V) value);
	}

	@Override
	public V get(Object key) {
		V value = backing.get(key);

		if (value == null) {
			return null;
		}

		return predicate.test(value) ? value : null;
	}

	@Override
	public V put(K key, V value) {
		return null;
	}

	@Override
	public V remove(Object key) {
		return null;
	}

	@Override
	public V forcePut(K key, V value) {
		return null;
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> map) {

	}

	@Override
	public void clear() {

	}

	@Override
	public Set<K> keySet() {
		return null;
	}

	@Override
	public Set<V> values() {
		return null;
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		return null;
	}

	@Override
	public V getOrDefault(Object key, V defaultValue) {
		return null;
	}

	@Override
	public void forEach(BiConsumer<? super K, ? super V> action) {

	}

	@Override
	public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {

	}

	@Override
	public V putIfAbsent(K key, V value) {
		return null;
	}

	@Override
	public boolean remove(Object key, Object value) {
		return false;
	}

	@Override
	public boolean replace(K key, V oldValue, V newValue) {
		return false;
	}

	@Override
	public V replace(K key, V value) {
		return null;
	}

	@Override
	public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
		return null;
	}

	@Override
	public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		return null;
	}

	@Override
	public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		return null;
	}

	@Override
	public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
		return null;
	}

	@Override
	public BiMap<V, K> inverse() {
		return null;
	}
}
