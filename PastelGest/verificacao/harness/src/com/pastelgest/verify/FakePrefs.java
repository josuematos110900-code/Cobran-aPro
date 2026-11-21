package com.pastelgest.verify;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** SharedPreferences em memória. */
public class FakePrefs implements SharedPreferences {
    final Map<String, Object> map = new HashMap<>();

    @Override public Map<String, ?> getAll() { return new HashMap<>(map); }
    @Override public String getString(String k, String d) { return map.containsKey(k) ? (String) map.get(k) : d; }
    @Override public Set<String> getStringSet(String k, Set<String> d) { return d; }
    @Override public int getInt(String k, int d) { return map.containsKey(k) ? (Integer) map.get(k) : d; }
    @Override public long getLong(String k, long d) { return map.containsKey(k) ? (Long) map.get(k) : d; }
    @Override public float getFloat(String k, float d) { return d; }
    @Override public boolean getBoolean(String k, boolean d) { return map.containsKey(k) ? (Boolean) map.get(k) : d; }
    @Override public boolean contains(String k) { return map.containsKey(k); }
    @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
    @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }

    @Override
    public Editor edit() {
        return new Editor() {
            final Map<String, Object> put = new HashMap<>();
            final java.util.List<String> rm = new java.util.ArrayList<>();
            @Override public Editor putString(String k, String v) { put.put(k, v); return this; }
            @Override public Editor putStringSet(String k, Set<String> v) { return this; }
            @Override public Editor putInt(String k, int v) { put.put(k, v); return this; }
            @Override public Editor putLong(String k, long v) { put.put(k, v); return this; }
            @Override public Editor putFloat(String k, float v) { return this; }
            @Override public Editor putBoolean(String k, boolean v) { put.put(k, v); return this; }
            @Override public Editor remove(String k) { rm.add(k); return this; }
            @Override public Editor clear() { map.clear(); return this; }
            @Override public boolean commit() { for (String k : rm) map.remove(k); map.putAll(put); return true; }
            @Override public void apply() { commit(); }
        };
    }
}
