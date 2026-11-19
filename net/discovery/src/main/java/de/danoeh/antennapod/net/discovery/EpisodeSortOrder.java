package de.danoeh.antennapod.net.discovery;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public enum EpisodeSortOrder {
    RELEVANCE("relevance"),
    NEWEST("newest"),
    OLDEST("oldest"),
    POPULAR("popular"),
    SHORTEST("shortest"),
    LONGEST("longest");

    private final String storageKey;

    EpisodeSortOrder(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public static EpisodeSortOrder fromStorageKey(@Nullable String key) {
        if (key == null || key.trim().isEmpty()) {
            return RELEVANCE;
        }
        for (EpisodeSortOrder order : values()) {
            if (order.storageKey.equalsIgnoreCase(key.trim())) {
                return order;
            }
        }
        return RELEVANCE;
    }

    public List<EpisodeSearchResult> sort(@Nullable List<EpisodeSearchResult> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        List<EpisodeSearchResult> sorted = new ArrayList<>(items);
        Collections.sort(sorted, (first, second) -> compare(first, second));
        return sorted;
    }

    private int compare(EpisodeSearchResult first, EpisodeSearchResult second) {
        switch (this) {
            case NEWEST:
                return compareByDate(first, second, true);
            case OLDEST:
                return compareByDate(first, second, false);
            case POPULAR:
                int popCmp = Float.compare(second.popularityScore, first.popularityScore);
                if (popCmp != 0) {
                    return popCmp;
                }
                int relTie = Float.compare(second.relevanceScore, first.relevanceScore);
                if (relTie != 0) {
                    return relTie;
                }
                return compareByDate(first, second, true);
            case SHORTEST:
                return compareByDuration(first, second, true);
            case LONGEST:
                return compareByDuration(first, second, false);
            case RELEVANCE:
            default:
                int relCmp = Float.compare(second.relevanceScore, first.relevanceScore);
                if (relCmp != 0) {
                    return relCmp;
                }
                return compareByDate(first, second, true);
        }
    }

    private static int compareByDate(EpisodeSearchResult first, EpisodeSearchResult second, boolean descending) {
        Date firstDate = first.getReleaseDate();
        Date secondDate = second.getReleaseDate();
        if (firstDate == null && secondDate == null) {
            return Float.compare(second.relevanceScore, first.relevanceScore);
        }
        if (firstDate == null) {
            return 1;
        }
        if (secondDate == null) {
            return -1;
        }
        int cmp = descending
                ? Long.compare(secondDate.getTime(), firstDate.getTime())
                : Long.compare(firstDate.getTime(), secondDate.getTime());
        if (cmp != 0) {
            return cmp;
        }
        return Float.compare(second.relevanceScore, first.relevanceScore);
    }

    private static int compareByDuration(EpisodeSearchResult first, EpisodeSearchResult second, boolean ascending) {
        boolean firstValid = first.durationMs > 0;
        boolean secondValid = second.durationMs > 0;
        if (!firstValid && !secondValid) {
            return Float.compare(second.relevanceScore, first.relevanceScore);
        }
        if (!firstValid) {
            return 1;
        }
        if (!secondValid) {
            return -1;
        }
        int cmp = ascending
                ? Long.compare(first.durationMs, second.durationMs)
                : Long.compare(second.durationMs, first.durationMs);
        if (cmp != 0) {
            return cmp;
        }
        return Float.compare(second.relevanceScore, first.relevanceScore);
    }
}
