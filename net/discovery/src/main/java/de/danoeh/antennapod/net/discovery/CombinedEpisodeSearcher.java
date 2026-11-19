package de.danoeh.antennapod.net.discovery;

import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.Nullable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableEmitter;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class CombinedEpisodeSearcher implements EpisodeSearcher {
    private static final String TAG = "CombinedEpisodeSearcher";
    private static final String CACHE_PROVIDER = "combined";
    private static final int FYYD_COMBINED_TIMEOUT_SECONDS = 4;
    private static final long FYYD_STREAM_TIMEOUT_MS = 4500L;

    private final ItunesEpisodeSearcher itunesSearcher = new ItunesEpisodeSearcher();
    private final FyydEpisodeSearcher fyydSearcher = new FyydEpisodeSearcher(FYYD_COMBINED_TIMEOUT_SECONDS);

    public CombinedEpisodeSearcher() {
    }

    @Override
    public Single<List<EpisodeSearchResult>> search(String query) {
        return searchStream(query, EpisodeSearchOptions.defaultOptions())
                .last(Collections.<EpisodeSearchResult>emptyList());
    }

    @Override
    public Observable<List<EpisodeSearchResult>> searchStream(String query, EpisodeSearchOptions options) {
        if (query == null || query.trim().isEmpty()) {
            return Observable.just(Collections.<EpisodeSearchResult>emptyList())
                    .observeOn(AndroidSchedulers.mainThread());
        }
        final EpisodeSearchOptions resolvedOptions = options != null ? options : EpisodeSearchOptions.defaultOptions();
        final String cacheKey = EpisodeSearchCache.buildKey(CACHE_PROVIDER, query, resolvedOptions);
        List<EpisodeSearchResult> cached = EpisodeSearchCache.get(cacheKey);
        if (cached != null) {
            return Observable.just(cached)
                    .observeOn(AndroidSchedulers.mainThread());
        }

        return Observable.<List<EpisodeSearchResult>>create(emitter -> {
            CompositeDisposable disposables = new CompositeDisposable();
            emitter.setCancellable(disposables::dispose);

            StreamAccumulator accumulator = new StreamAccumulator(query, cacheKey);

            disposables.add(itunesSearcher.searchStream(query, resolvedOptions)
                    .last(Collections.<EpisodeSearchResult>emptyList())
                    .subscribe(
                            results -> accumulator.onProviderSuccess(0, results, emitter),
                            error -> {
                                Log.d(TAG, Log.getStackTraceString(error));
                                accumulator.onProviderFailure(error, emitter);
                            }));

            disposables.add(fyydSearcher.searchStream(query, resolvedOptions)
                    .timeout(FYYD_STREAM_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .last(Collections.<EpisodeSearchResult>emptyList())
                    .subscribe(
                            results -> accumulator.onProviderSuccess(1, results, emitter),
                            error -> {
                                Log.d(TAG, Log.getStackTraceString(error));
                                accumulator.onProviderFailure(error, emitter);
                            }));
        }).observeOn(AndroidSchedulers.mainThread());
    }

    static List<EpisodeSearchResult> mergeAndDeduplicate(String query,
                                                         List<List<EpisodeSearchResult>> providerLists) {
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
                float rankContribution = candidate.relevanceScore > 0f
                        ? candidate.relevanceScore : (1.0f / (position + 1.0f));
                float popContribution = candidate.popularityScore > 0f
                        ? candidate.popularityScore : (1.0f / (position + 1.0f));
                if (existing != null) {
                    existing.relevanceScore += rankContribution;
                    existing.popularityScore = Math.max(existing.popularityScore, popContribution) + 0.8f;
                    existing.result = mergeResults(existing.result, candidate);
                } else {
                    RankedEntry created = new RankedEntry(candidate, rankContribution, popContribution);
                    entries.add(created);
                    feedTitleIndex.put(feedTitleKey, created);
                    if (!episodeUrlKey.isEmpty()) {
                        episodeUrlIndex.put(episodeUrlKey, created);
                    }
                }
            }
        }

        Map<String, Integer> feedFrequency = new HashMap<>();
        for (RankedEntry entry : entries) {
            String normalizedFeed = normalizeSchemeAndSlash(entry.result.feedUrl);
            Integer count = feedFrequency.get(normalizedFeed);
            feedFrequency.put(normalizedFeed, count == null ? 1 : count + 1);
        }

        List<EpisodeSearchResult> merged = new ArrayList<>(entries.size());
        for (RankedEntry entry : entries) {
            String normalizedFeed = normalizeSchemeAndSlash(entry.result.feedUrl);
            Integer count = feedFrequency.get(normalizedFeed);
            int feedCount = count != null ? count : 1;
            float frequencyBonus = Math.min(0.4f, (feedCount - 1) * 0.1f);
            float finalRelevance = entry.relevanceScore
                    + EpisodeSearchUtils.computeQueryMatchBonus(query, entry.result.title, entry.result.podcastTitle);
            float finalPopularity = entry.popularityScore + frequencyBonus;
            EpisodeSearchResult base = entry.result;
            merged.add(new EpisodeSearchResult(
                    base.title, base.podcastTitle, base.feedUrl, base.episodeUrl, base.imageUrl,
                    base.getReleaseDate(), base.durationMs, base.language, finalRelevance, finalPopularity));
        }

        return EpisodeSortOrder.RELEVANCE.sort(merged);
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
        long durationMs = primary.durationMs > 0 ? primary.durationMs : secondary.durationMs;
        String language = pickNonEmpty(primary.language, secondary.language);
        return new EpisodeSearchResult(
                primary.title, podcastTitle, primary.feedUrl, episodeUrl, imageUrl, releaseDate,
                durationMs, language, primary.relevanceScore, primary.popularityScore);
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

    private static String normalizeSchemeAndSlash(@Nullable String url) {
        if (url == null) {
            return "";
        }
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

    private static final class StreamAccumulator {
        private final String query;
        private final String cacheKey;
        private final List<List<EpisodeSearchResult>> resultsByProvider = new ArrayList<>(2);
        private int completedCount = 0;
        private int successCount = 0;
        private boolean emittedAny = false;
        private List<EpisodeSearchResult> lastMerged = Collections.emptyList();
        private Throwable firstError;

        StreamAccumulator(String query, String cacheKey) {
            this.query = query;
            this.cacheKey = cacheKey;
            this.resultsByProvider.add(null);
            this.resultsByProvider.add(null);
        }

        void onProviderSuccess(int providerIndex, List<EpisodeSearchResult> results,
                               ObservableEmitter<List<EpisodeSearchResult>> emitter) {
            List<EpisodeSearchResult> toEmit = null;
            List<EpisodeSearchResult> cacheSnapshot = null;
            boolean shouldComplete;
            synchronized (this) {
                resultsByProvider.set(providerIndex, results);
                successCount++;
                completedCount++;
                List<EpisodeSearchResult> merged = mergeAndDeduplicate(query, resultsByProvider);
                boolean hasNewProviderData = !emittedAny || !results.isEmpty();
                lastMerged = merged;
                shouldComplete = completedCount >= 2;
                if ((!merged.isEmpty() && hasNewProviderData) || (shouldComplete && !emittedAny)) {
                    emittedAny = true;
                    toEmit = merged;
                }
                if (shouldComplete) {
                    cacheSnapshot = lastMerged;
                }
            }
            if (emitter.isDisposed()) {
                return;
            }
            if (toEmit != null) {
                emitter.onNext(toEmit);
            }
            if (shouldComplete) {
                if (cacheSnapshot != null) {
                    EpisodeSearchCache.put(cacheKey, cacheSnapshot);
                }
                emitter.onComplete();
            }
        }

        void onProviderFailure(Throwable error, ObservableEmitter<List<EpisodeSearchResult>> emitter) {
            boolean shouldError = false;
            boolean shouldComplete = false;
            List<EpisodeSearchResult> fallbackEmit = null;
            List<EpisodeSearchResult> cacheSnapshot = null;
            Throwable errorToEmit = null;
            synchronized (this) {
                completedCount++;
                if (firstError == null) {
                    firstError = error;
                }
                if (completedCount >= 2) {
                    if (successCount == 0) {
                        shouldError = true;
                        errorToEmit = firstError;
                    } else {
                        shouldComplete = true;
                        cacheSnapshot = lastMerged;
                        if (!emittedAny) {
                            emittedAny = true;
                            fallbackEmit = lastMerged;
                        }
                    }
                }
            }
            if (emitter.isDisposed()) {
                return;
            }
            if (shouldError) {
                emitter.onError(errorToEmit);
            } else if (shouldComplete) {
                if (fallbackEmit != null) {
                    emitter.onNext(fallbackEmit);
                }
                if (cacheSnapshot != null) {
                    EpisodeSearchCache.put(cacheKey, cacheSnapshot);
                }
                emitter.onComplete();
            }
        }
    }

    private static final class RankedEntry {
        EpisodeSearchResult result;
        float relevanceScore;
        float popularityScore;

        RankedEntry(EpisodeSearchResult result, float relevanceScore, float popularityScore) {
            this.result = result;
            this.relevanceScore = relevanceScore;
            this.popularityScore = popularityScore;
        }
    }
}
