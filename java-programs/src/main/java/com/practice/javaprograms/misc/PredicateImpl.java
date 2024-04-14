package com.practice.javaprograms.misc;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class PredicateImpl implements Predicate<String> {

    private final int size;

    public PredicateImpl(int size){
        this.size = size;
    }

    @Override
    public boolean test(String s) {
        Map<String,String> test = new HashMap<>();
        return s.length() > size;
    }

    public class MapImpl<K, V> extends AbstractMap<K, V> {

        @Override
        public Set<Entry<K, V>> entrySet() {
            return null;
        }
    }
}
