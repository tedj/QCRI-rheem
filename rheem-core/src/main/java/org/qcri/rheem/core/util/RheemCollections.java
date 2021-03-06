package org.qcri.rheem.core.util;

import org.apache.commons.lang3.Validate;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Utilities to operate {@link java.util.Collection}s.
 */
public class RheemCollections {

    private RheemCollections() {
    }

    public static <K, V> void put(Map<K, Collection<V>> map, K key, V value) {
        map.compute(key, (k, values) -> {
            if (values == null) {
                values = new LinkedList<>();
            }
            values.add(value);
            return values;
        });
    }

    /**
     * Provides the given {@code collection} as {@link Set}, thereby checking if it is already a {@link Set}.
     */
    public static <T> Set<T> asSet(Collection<T> collection) {
        if (collection instanceof Set<?>) {
            return (Set<T>) collection;
        }
        return new HashSet<>(collection);
    }

    /**
     * Provides the given {@code collection} as {@link List}, thereby checking if it is already a {@link List}.
     */
    public static <T> List<T> asList(Collection<T> collection) {
        if (collection instanceof List<?>) {
            return (List<T>) collection;
        }
        return new ArrayList<>(collection);
    }

    /**
     * Validate that there is only a single element in the {@code collection} and return it.
     */
    public static <T> T getSingle(Collection<T> collection) {
        Validate.isTrue(collection.size() == 1, "%s is not a singleton.", collection);
        return getAny(collection);
    }

    /**
     * Validate that there is at most one element in the {@code collection} and return it (or {@code null} otherwise).
     */
    public static <T> T getSingleOrNull(Collection<T> collection) {
        Validate.isTrue(collection.size() <= 1, "Expected 0 or 1 elements, found %d.", collection.size());
        return collection.isEmpty() ? null : getAny(collection);
    }

    /**
     * Return any element from the {@code collection}.
     */
    public static <T> T getAny(Collection<T> collection) {
        return collection.iterator().next();
    }

    /**
     * Return a new {@link List} with mapped values.
     */
    public static <S, T> List<T> map(List<S> list, Function<S, T> mapFunction) {
        List<T> result = new ArrayList<>(list.size());
        for (S element : list) {
            result.add(mapFunction.apply(element));
        }
        return result;
    }

    /**
     * Return a new {@link List} with mapped values.
     */
    public static <S, T> List<T> map(List<S> list, BiFunction<Integer, S, T> mapFunction) {
        List<T> result = new ArrayList<>(list.size());
        int i = 0;
        for (S element : list) {
            result.add(mapFunction.apply(i++, element));
        }
        return result;
    }

    /**
     * Returns an {@link Iterable} that iterates the cross product of the given {@code iterables}.
     *
     * @param iterables should be iterable multiple times
     */
    public static <T> Iterable<List<T>> streamedCrossProduct(List<? extends Iterable<T>> iterables) {
        return new CrossProductIterable<>(iterables);
    }

    /**
     * Creates an {@link ArrayList} that is filled with {@code k} {@code null}s.
     */
    public static <T> ArrayList<T> createNullFilledArrayList(int k) {
        ArrayList<T> list = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            list.add(null);
        }
        return list;
    }

    /**
     * Creates the <i>power list</i> of the given {@code base} (akin to power sets of sets).
     */
    public static <T> Collection<List<T>> createPowerList(Collection<T> base) {
        return createPowerList(base, base.size());
    }

    /**
     * Creates the <i>power list</i> of the given {@code base} (akin to power sets of sets).
     *
     * @param maxElements maximum number of elements in the {@link List}s in the power list
     */
    public static <T> Collection<List<T>> createPowerList(Collection<T> base, int maxElements) {
        List<T> baseList = asList(base);
        List<List<T>> powerList = new ArrayList<>();
        createPowerListAux(baseList, 0, maxElements, powerList);
        powerList.sort((a, b) -> Integer.compare(a.size(), b.size()));
        return powerList;
    }

    /**
     * Helper method to create power lists.
     *
     * @param base        the {@link List} whose power list is to be created
     * @param startIndex  index of the first element in {@code base} to be considered
     * @param maxElements the maximum number of elements in {@link List}s within the power list
     * @param collector   collects power list members
     */
    private static <T> void createPowerListAux(List<T> base, int startIndex, int maxElements, List<List<T>> collector) {
        if (startIndex >= base.size()) {
            collector.add(Collections.emptyList());
        } else {
            T head = base.get(startIndex);
            int collectorStartIndex = collector.size();
            createPowerListAux(base, startIndex + 1, maxElements, collector);
            int collectorEndIndex = collector.size();
            for (int i = collectorStartIndex; i < collectorEndIndex; i++) {
                final List<T> recursivelyCreatedElement = collector.get(i);
                if (recursivelyCreatedElement.size() < maxElements) {
                    List<T> derivativeElement = new ArrayList<>(recursivelyCreatedElement.size() + 1);
                    derivativeElement.add(head);
                    derivativeElement.addAll(recursivelyCreatedElement);
                    collector.add(derivativeElement);
                }
            }
        }
    }
}
