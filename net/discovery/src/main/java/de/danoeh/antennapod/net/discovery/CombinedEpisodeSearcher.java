package de.danoeh.antennapod.net.discovery;

import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.Nullable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CombinedEpisodeSearcher implements EpisodeSearcher {
    private static final String TAG = "CombinedEpisodeSearcher";
    private final ItunesEpisodeSearcher itunesSearcher = new ItunesEpisodeSearcher();
    private final FyydEpisodeSearcher fyydSearcher = new FyydEpisodeSearcher();

    public CombinedEpisodeSearcher() {
    }

    @Override
    public Single<List<EpisodeSearchResult>> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Single.just(Collections.emptyList())
                    .observeOn(AndroidSchedulers.mainThread());
        }
        Single<ProviderResult> itunesSingle = itunesSearcher.search(query)
                .map(ProviderResult::success)
                .onErrorReturn(throwable -> {
                    Log.d(TAG, Log.getStackTraceString(throwable));
                    return ProviderResult.failure(throwable);
                });
        Single<ProviderResult> fyydSingle = fyydSearcher.search(query)
                .map(ProviderResult::success)
                .onErrorReturn(throwable -> {
                    Log.d(TAG, Log.getStackTraceString(throwable));
                    return ProviderResult.failure(throwable);
                });

        return Single.zip(itunesSingle, fyydSingle, (first, second) -> {
            if (first.error != null && second.error != null) {
                return ProviderResult.failure(first.error);
            }
            List<List<EpisodeSearchResult>> allResults = new ArrayList<>();
            if (first.results != null) {
                allResults.add(first.results);
            }
            if (second.results != null) {
                allResults.add(second.results);
            }
            return ProviderResult.success(mergeAndDeduplicate(allResults));
        })
                .flatMap(combined -> {
                    if (combined.error != null) {
                        return Single.error(combined.error);
                    }
                    return Single.just(combined.results != null
                            ? combined.results : Collections.emptyList());
                })
                .observeOn(AndroidSchedulers.mainThread());
    }

    static List<EpisodeSearchResult> mergeAndDeduplicate(List<List<EpisodeSearchResult>> providerLists) {
        List<RankedEntry> entries = new ArrayList<>();
        Map<String, RankedEntry> feedTitleIndex = new HashMap<>();
        Map<String, RankedEntry> episodeUrlIndex = new HashMap<>();

        for (List<EpisodeSearchResult> providerResults : providerLists) {
            if (providerResults == null) {
                continue;
            }
            for (int position = 0; position < providerResults.size(); position++) {
                EpisodeSearchResult candidate = providerResults.get(position);
                if (candidate == null || TextUtils.isEmpty(candidate.title)
                        || !EpisodeSearchUtils.isValidFeedUrl(candidate.feedUrl)) {
                    continue;
                }
                String feedTitleKey = buildFeedTitleKey(candidate.feedUrl, candidate.title);
                String episodeUrlKey = normalizeEpisodeUrl(candidate.episodeUrl);
                RankedEntry existing = findExisting(feedTitleIndex, episodeUrlIndex, feedTitleKey, episodeUrlKey);
                float rankContribution = 1.0f / (position + 1.0f);
                if (existing != null) {
                    existing.score += rankContribution;
                    existing.result = mergeResults(existing.result, candidate);
                } else {
                    RankedEntry created = new RankedEntry(candidate, rankContribution);
                    entries.add(created);
                    feedTitleIndex.put(feedTitleKey, created);
                    if (!episodeUrlKey.isEmpty()) {
                        episodeUrlIndex.put(episodeUrlKey, created);
                    }
                }
            }
        }

        entries.sort((first, second) -> Float.compare(second.score, first.score));
        List<EpisodeSearchResult> merged = new ArrayList<>(entries.size());
        for (RankedEntry entry : entries) {
            merged.add(entry.result);
        }
        return merged;
    }

    @Nullable
    private static RankedEntry findExisting(Map<String, RankedEntry> feedTitleIndex,
                                            Map<String, RankedEntry> episodeUrlIndex,
                                            String feedTitleKey, String episodeUrlKey) {
        RankedEntry byFeedTitle = feedTitleIndex.get(feedTitleKey);
        if (byFeedTitle != null) {
            return byFeedTitle;
        }
        if (!episodeUrlKey.isEmpty()) {
            return episodeUrlIndex.get(episodeUrlKey);
        }
        return null;
    }

    private static EpisodeSearchResult mergeResults(EpisodeSearchResult primary, EpisodeSearchResult secondary) {
        String podcastTitle = pickNonEmpty(primary.podcastTitle, secondary.podcastTitle);
        String imageUrl = pickNonEmpty(primary.imageUrl, secondary.imageUrl);
        String episodeUrl = pickPreferredEpisodeUrl(primary.episodeUrl, secondary.episodeUrl);
        Date releaseDate = primary.getReleaseDate() != null
                ? primary.getReleaseDate() : secondary.getReleaseDate();
        return new EpisodeSearchResult(
                primary.title, podcastTitle, primary.feedUrl, episodeUrl, imageUrl, releaseDate);
    }

    private static String pickNonEmpty(@Nullable String first, @Nullable String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first;
        }
        return second != null ? second : "";
    }

    private static String pickPreferredEpisodeUrl(@Nullable String first, @Nullable String second) {
        boolean firstHasTracking = first != null && first.contains("_from=fyyd.de");
        boolean secondClean = second != null && !second.trim().isEmpty() && !second.contains("_from=fyyd.de");
        if (firstHasTracking && secondClean) {
            return second;
        }
        return pickNonEmpty(first, second);
    }

    private static String buildFeedTitleKey(String feedUrl, String title) {
        String normalizedFeed = normalizeSchemeAndSlash(feedUrl);
        String normalizedTitle = title.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return normalizedFeed + "\n" + normalizedTitle;
    }

    private static String normalizeEpisodeUrl(@Nullable String episodeUrl) {
        if (episodeUrl == null || episodeUrl.trim().isEmpty()) {
            return "";
        }
        String cleaned = episodeUrl.trim()
                .replace("?_from=fyyd.de", "")
                .replace("&_from=fyyd.de", "");
        return normalizeSchemeAndSlash(cleaned);
    }

    private static String normalizeSchemeAndSlash(String url) {
        String value = url.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("https://")) {
            value = value.substring("https://".length());
        } else if (value.startsWith("http://")) {
            value = value.substring("http://".length());
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    @Override
    public String getName() {
        return TextUtils.join(", ", new String[]{itunesSearcher.getName(), fyydSearcher.getName()});
    }

    private static final class RankedEntry {
        EpisodeSearchResult result;
        float score;

        RankedEntry(EpisodeSearchResult result, float score) {
            this.result = result;
            this.score = score;
        }
    }

    private static final class ProviderResult {
        @Nullable
        final List<EpisodeSearchResult> results;
        @Nullable
        final Throwable error;

        private ProviderResult(@Nullable List<EpisodeSearchResult> results, @Nullable Throwable error) {
            this.results = results;
            this.error = error;
        }

        static ProviderResult success(List<EpisodeSearchResult> results) {
            return new ProviderResult(results, null);
        }

        static ProviderResult failure(Throwable error) {
            return new ProviderResult(null, error);
        }
    }
}
