package de.danoeh.antennapod.net.discovery;

import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class FyydEpisodeSearcher implements EpisodeSearcher {
    private static final String CACHE_PROVIDER = "fyyd";
    private static final String FYYD_EPISODE_API_URL =
            "https://api.fyyd.de/0.2/search/episode?title=%s&count=15";
    private static final String FYYD_PODCAST_API_URL =
            "https://api.fyyd.de/0.2/podcast?podcast_id=%d";
    private static final int MAX_CACHE_SIZE = 500;
    private static final int MAX_CONCURRENT_PODCAST_LOOKUPS = 8;
    private static final int DEFAULT_SEARCH_TIMEOUT_SECONDS = 10;
    private static final int PODCAST_LOOKUP_TIMEOUT_SECONDS = 3;
    private static final Map<Long, FyydPodcastMeta> PODCAST_CACHE = new ConcurrentHashMap<>();

    private final int searchTimeoutSeconds;

    public FyydEpisodeSearcher() {
        this(DEFAULT_SEARCH_TIMEOUT_SECONDS);
    }

    public FyydEpisodeSearcher(int searchTimeoutSeconds) {
        this.searchTimeoutSeconds = searchTimeoutSeconds;
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
        return Single.fromCallable(() -> fetchRawEpisodes(query))
                .flatMap(rawEpisodes -> {
                    Set<Long> missingPodcastIds = new LinkedHashSet<>();
                    for (RawEpisode episode : rawEpisodes) {
                        if (episode.podcastId > 0 && !PODCAST_CACHE.containsKey(episode.podcastId)) {
                            missingPodcastIds.add(episode.podcastId);
                        }
                    }
                    if (missingPodcastIds.isEmpty()) {
                        List<EpisodeSearchResult> results = buildResults(query, rawEpisodes, resolvedOptions);
                        EpisodeSearchCache.put(cacheKey, results);
                        return Single.just(results);
                    }
                    return Observable.fromIterable(missingPodcastIds)
                            .flatMap(id -> Observable.fromCallable(() -> fetchAndCachePodcast(id))
                                    .subscribeOn(Schedulers.io())
                                    .onErrorResumeNext(throwable -> Observable.empty()),
                                    MAX_CONCURRENT_PODCAST_LOOKUPS)
                            .toList()
                            .map(ignored -> {
                                List<EpisodeSearchResult> results = buildResults(query, rawEpisodes, resolvedOptions);
                                EpisodeSearchCache.put(cacheKey, results);
                                return results;
                            });
                })
                .toObservable()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    private List<RawEpisode> fetchRawEpisodes(String query) throws Exception {
        String formattedUrl = String.format(FYYD_EPISODE_API_URL, EpisodeSearchUtils.encodeQuery(query));
        OkHttpClient client = AntennapodHttpClient.getHttpClient().newBuilder()
                .callTimeout(searchTimeoutSeconds, TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder().url(formattedUrl).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException(response.toString());
            }
            String resultString = response.body().string();
            JSONObject result = new JSONObject(resultString);
            JSONArray data = result.optJSONArray("data");
            if (data == null) {
                throw new IOException("Null response");
            }
            List<RawEpisode> episodes = new ArrayList<>();
            for (int i = 0; i < data.length(); i++) {
                JSONObject episodeJson = data.optJSONObject(i);
                if (episodeJson == null) {
                    continue;
                }
                String title = EpisodeSearchUtils.optNonNullString(episodeJson, "title");
                long podcastId = episodeJson.optLong("podcast_id", -1L);
                if (title.isEmpty() || podcastId <= 0) {
                    continue;
                }
                String episodeUrl = EpisodeSearchUtils.optNonNullString(episodeJson, "enclosure");
                String imageUrl = EpisodeSearchUtils.optNonNullString(episodeJson, "imgURL");
                Date releaseDate = EpisodeSearchUtils.parseIsoDate(
                        EpisodeSearchUtils.optNonNullString(episodeJson, "pubdate"));
                long durationSec = episodeJson.optLong("duration", 0L);
                long durationMs = durationSec > 0 ? durationSec * 1000L : 0L;
                episodes.add(new RawEpisode(title, podcastId, episodeUrl, imageUrl, releaseDate, durationMs));
            }
            return episodes;
        }
    }

    private FyydPodcastMeta fetchAndCachePodcast(long podcastId) throws Exception {
        String formattedUrl = String.format(Locale.US, FYYD_PODCAST_API_URL, podcastId);
        OkHttpClient client = AntennapodHttpClient.getHttpClient().newBuilder()
                .callTimeout(PODCAST_LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder().url(formattedUrl).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException(response.toString());
            }
            String resultString = response.body().string();
            JSONObject result = new JSONObject(resultString);
            JSONObject data = result.optJSONObject("data");
            if (data == null) {
                throw new IOException("Null podcast data");
            }
            String podcastTitle = EpisodeSearchUtils.optNonNullString(data, "title");
            String feedUrl = EpisodeSearchUtils.optNonNullString(data, "xmlURL");
            String imageUrl = EpisodeSearchUtils.optNonNullString(data, "thumbImageURL");
            if (imageUrl.isEmpty()) {
                imageUrl = EpisodeSearchUtils.optNonNullString(data, "imgURL");
            }
            String language = EpisodeSearchUtils.optNonNullString(data, "language").toLowerCase(Locale.ROOT);
            int rank = data.optInt("rank", 0);
            FyydPodcastMeta meta = new FyydPodcastMeta(podcastTitle, feedUrl, imageUrl, language, rank);
            if (PODCAST_CACHE.size() >= MAX_CACHE_SIZE) {
                PODCAST_CACHE.clear();
            }
            PODCAST_CACHE.put(podcastId, meta);
            return meta;
        }
    }

    private List<EpisodeSearchResult> buildResults(String query, List<RawEpisode> rawEpisodes,
                                                   EpisodeSearchOptions options) {
        String targetIsoLang = options.language.getResolvedIsoLanguage();
        List<EpisodeSearchResult> results = new ArrayList<>();
        int validRank = 0;
        for (RawEpisode episode : rawEpisodes) {
            FyydPodcastMeta meta = PODCAST_CACHE.get(episode.podcastId);
            if (meta == null || !EpisodeSearchUtils.isValidFeedUrl(meta.feedUrl)) {
                continue;
            }
            if (!EpisodeSearchUtils.matchesLanguage(meta.language, targetIsoLang)) {
                continue;
            }
            String imageUrl = episode.imageUrl.isEmpty() ? meta.imageUrl : episode.imageUrl;
            float relevance = (1.0f / (validRank + 1.0f))
                    + EpisodeSearchUtils.computeQueryMatchBonus(query, episode.title, meta.title);
            float popularity = meta.rank > 0
                    ? (float) (1.2 / Math.log10(meta.rank + 9.0))
                    : 0.35f;
            results.add(new EpisodeSearchResult(
                    episode.title, meta.title, meta.feedUrl, episode.episodeUrl, imageUrl, episode.releaseDate,
                    episode.durationMs, meta.language, relevance, popularity));
            validRank++;
        }
        return results;
    }

    @Override
    public String getName() {
        return "fyyd";
    }

    private static final class RawEpisode {
        final String title;
        final long podcastId;
        final String episodeUrl;
        final String imageUrl;
        final Date releaseDate;
        final long durationMs;

        RawEpisode(String title, long podcastId, String episodeUrl, String imageUrl,
                   Date releaseDate, long durationMs) {
            this.title = title;
            this.podcastId = podcastId;
            this.episodeUrl = episodeUrl;
            this.imageUrl = imageUrl;
            this.releaseDate = releaseDate;
            this.durationMs = durationMs;
        }
    }

    private static final class FyydPodcastMeta {
        final String title;
        final String feedUrl;
        final String imageUrl;
        final String language;
        final int rank;

        FyydPodcastMeta(String title, String feedUrl, String imageUrl, String language, int rank) {
            this.title = title;
            this.feedUrl = feedUrl;
            this.imageUrl = imageUrl;
            this.language = language;
            this.rank = rank;
        }
    }
}
