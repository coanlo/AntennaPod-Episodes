package de.danoeh.antennapod.net.discovery;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class EpisodeSearchCache {
    private static final int MAX_ENTRIES = 40;
    private static final long TTL_MS = 5L * 60L * 1000L;

    private static final Map<String, CacheEntry> CACHE = new LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private EpisodeSearchCache() {
    }

    static String buildKey(String provider, String query, EpisodeSearchOptions options) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return provider + "|" + normalizedQuery + "|" + options.getCacheKey();
    }

    @Nullable
    static synchronized List<EpisodeSearchResult> get(String key) {
        CacheEntry entry = CACHE.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() - entry.timestampMs > TTL_MS) {
            CACHE.remove(key);
            return null;
        }
        return new ArrayList<>(entry.results);
    }

    static synchronized void put(String key, List<EpisodeSearchResult> results) {
        CACHE.put(key, new CacheEntry(Collections.unmodifiableList(new ArrayList<>(results)),
                System.currentTimeMillis()));
    }

    private static final class CacheEntry {
        final List<EpisodeSearchResult> results;
        final long timestampMs;

        CacheEntry(List<EpisodeSearchResult> results, long timestampMs) {
            this.results = results;
            this.timestampMs = timestampMs;
        }
    }
}
