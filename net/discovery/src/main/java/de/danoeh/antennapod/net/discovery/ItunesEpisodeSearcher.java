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
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ItunesEpisodeSearcher implements EpisodeSearcher {
    private static final String CACHE_PROVIDER = "itunes";
    private static final String ITUNES_EPISODE_API_URL =
            "https://itunes.apple.com/search?media=podcast&entity=podcastEpisode&term=%s&limit=50";
    private static final int CALL_TIMEOUT_SECONDS = 8;
    private static final long NINETY_DAYS_MS = 90L * 24L * 60L * 60L * 1000L;

    public ItunesEpisodeSearcher() {
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
        return Observable.fromCallable(() -> {
            List<EpisodeSearchResult> episodes = fetchEpisodes(query, resolvedOptions);
            EpisodeSearchCache.put(cacheKey, episodes);
            return episodes;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    private List<EpisodeSearchResult> fetchEpisodes(String query, EpisodeSearchOptions options) throws Exception {
        StringBuilder urlBuilder = new StringBuilder(
                String.format(ITUNES_EPISODE_API_URL, EpisodeSearchUtils.encodeQuery(query)));
        String countryCode = options.language.getResolvedCountryCode();
        String itunesLang = options.language.getResolvedItunesLang();
        if (!countryCode.isEmpty()) {
            urlBuilder.append("&country=").append(EpisodeSearchUtils.encodeQuery(countryCode));
        }
        if (!itunesLang.isEmpty()) {
            urlBuilder.append("&lang=").append(EpisodeSearchUtils.encodeQuery(itunesLang));
        }

        OkHttpClient client = AntennapodHttpClient.getHttpClient().newBuilder()
                .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder().url(urlBuilder.toString()).build();
        List<EpisodeSearchResult> episodes = new ArrayList<>();
        long now = System.currentTimeMillis();
        String resolvedIsoLang = options.language.getResolvedIsoLanguage();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException(response.toString());
            }
            String resultString = response.body().string();
            JSONObject result = new JSONObject(resultString);
            JSONArray results = result.optJSONArray("results");
            if (results != null) {
                int validRank = 0;
                for (int i = 0; i < results.length(); i++) {
                    JSONObject episodeJson = results.optJSONObject(i);
                    if (episodeJson == null) {
                        continue;
                    }
                    String feedUrl = EpisodeSearchUtils.optNonNullString(episodeJson, "feedUrl");
                    if (!EpisodeSearchUtils.isValidFeedUrl(feedUrl)) {
                        continue;
                    }
                    String title = EpisodeSearchUtils.optNonNullString(episodeJson, "trackName");
                    if (title.isEmpty()) {
                        continue;
                    }
                    String podcastTitle = EpisodeSearchUtils.optNonNullString(episodeJson, "collectionName");
                    String episodeUrl = EpisodeSearchUtils.optNonNullString(episodeJson, "episodeUrl");
                    String imageUrl = extractImageUrl(episodeJson);
                    Date releaseDate = EpisodeSearchUtils.parseIsoDate(
                            EpisodeSearchUtils.optNonNullString(episodeJson, "releaseDate"));
                    long durationMs = episodeJson.optLong("trackTimeMillis", 0L);
                    float relevance = (1.0f / (validRank + 1.0f))
                            + EpisodeSearchUtils.computeQueryMatchBonus(query, title, podcastTitle);
                    float popularity = (50.0f - Math.min(validRank, 49)) / 50.0f;
                    if (releaseDate != null && now - releaseDate.getTime() < NINETY_DAYS_MS) {
                        popularity += 0.15f;
                    }
                    episodes.add(new EpisodeSearchResult(
                            title, podcastTitle, feedUrl, episodeUrl, imageUrl, releaseDate,
                            durationMs, resolvedIsoLang, relevance, popularity));
                    validRank++;
                }
            }
        }
        return episodes;
    }

    private static String extractImageUrl(JSONObject episodeJson) {
        String[] keys = {"artworkUrl600", "artworkUrl160", "artworkUrl100", "artworkUrl60"};
        for (String key : keys) {
            String url = EpisodeSearchUtils.optNonNullString(episodeJson, key);
            if (!url.isEmpty()) {
                return url;
            }
        }
        return "";
    }

    @Override
    public String getName() {
        return "Apple";
    }
}
