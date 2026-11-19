package de.danoeh.antennapod.net.discovery;

import androidx.annotation.Nullable;
import okhttp3.HttpUrl;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class EpisodeSearchUtils {
    private EpisodeSearchUtils() {
    }

    static boolean isValidFeedUrl(@Nullable String url) {
        if (url == null) {
            return false;
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        return HttpUrl.parse(trimmed) != null;
    }

    static String optNonNullString(@Nullable JSONObject json, String key) {
        if (json == null || json.isNull(key)) {
            return "";
        }
        return json.optString(key, "").trim();
    }

    static String encodeQuery(String query) {
        String trimmed = query == null ? "" : query.trim();
        try {
            return URLEncoder.encode(trimmed, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return trimmed;
        }
    }

    static boolean matchesLanguage(@Nullable String itemLanguage, @Nullable String targetIsoLanguage) {
        if (targetIsoLanguage == null || targetIsoLanguage.isEmpty()) {
            return true;
        }
        if (itemLanguage == null || itemLanguage.trim().isEmpty()) {
            return true;
        }
        String normalizedItem = itemLanguage.trim().toLowerCase(Locale.ROOT);
        String normalizedTarget = targetIsoLanguage.trim().toLowerCase(Locale.ROOT);
        return normalizedItem.equals(normalizedTarget)
                || normalizedItem.startsWith(normalizedTarget + "-")
                || normalizedItem.startsWith(normalizedTarget + "_");
    }

    static float computeQueryMatchBonus(@Nullable String query, @Nullable String title,
                                        @Nullable String podcastTitle) {
        if (query == null || query.trim().isEmpty()) {
            return 0f;
        }
        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        String normalizedTitle = title == null ? "" : title.toLowerCase(Locale.ROOT);
        String normalizedPodcast = podcastTitle == null ? "" : podcastTitle.toLowerCase(Locale.ROOT);
        float bonus = 0f;
        if (normalizedTitle.contains(normalizedQuery)) {
            bonus += 0.35f;
        }
        String[] tokens = normalizedQuery.split("\\s+");
        if (tokens.length > 1) {
            int matchedInTitle = 0;
            for (String token : tokens) {
                if (token.length() >= 2 && normalizedTitle.contains(token)) {
                    matchedInTitle++;
                }
            }
            bonus += 0.25f * ((float) matchedInTitle / tokens.length);
        }
        if (normalizedPodcast.contains(normalizedQuery)) {
            bonus += 0.1f;
        }
        return bonus;
    }

    @Nullable
    static Date parseIsoDate(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        int dotIndex = value.indexOf('.');
        if (dotIndex > 0) {
            int tzStart = Math.max(value.indexOf('Z', dotIndex),
                    Math.max(value.indexOf('+', dotIndex), value.indexOf('-', dotIndex)));
            if (tzStart > dotIndex) {
                value = value.substring(0, dotIndex) + value.substring(tzStart);
            } else {
                value = value.substring(0, dotIndex);
            }
        }
        if (value.endsWith("Z")) {
            value = value.substring(0, value.length() - 1) + "+0000";
        } else if (value.length() >= 6 && value.charAt(value.length() - 3) == ':'
                && (value.charAt(value.length() - 6) == '+' || value.charAt(value.length() - 6) == '-')) {
            value = value.substring(0, value.length() - 3) + value.substring(value.length() - 2);
        }
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
            return format.parse(value);
        } catch (ParseException e) {
            try {
                SimpleDateFormat fallback = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                fallback.setTimeZone(TimeZone.getTimeZone("UTC"));
                return fallback.parse(value);
            } catch (ParseException ignored) {
                return null;
            }
        }
    }
}
