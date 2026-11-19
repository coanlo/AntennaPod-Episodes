package de.danoeh.antennapod.net.discovery;

import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleOnSubscribe;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class ItunesEpisodeSearcher implements EpisodeSearcher {
    private static final String ITUNES_EPISODE_API_URL =
            "https://itunes.apple.com/search?media=podcast&entity=podcastEpisode&term=%s&limit=25";

    public ItunesEpisodeSearcher() {
    }

    @Override
    public Single<List<EpisodeSearchResult>> search(String query) {
        return Single.create((SingleOnSubscribe<List<EpisodeSearchResult>>) subscriber -> {
            if (query == null || query.trim().isEmpty()) {
                subscriber.onSuccess(Collections.emptyList());
                return;
            }
            String formattedUrl = String.format(ITUNES_EPISODE_API_URL, EpisodeSearchUtils.encodeQuery(query));
            OkHttpClient client = AntennapodHttpClient.getHttpClient();
            Request request = new Request.Builder().url(formattedUrl).build();
            List<EpisodeSearchResult> episodes = new ArrayList<>();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    subscriber.onError(new IOException(response.toString()));
                    return;
                }
                String resultString = response.body().string();
                JSONObject result = new JSONObject(resultString);
                JSONArray results = result.optJSONArray("results");
                if (results != null) {
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
                        episodes.add(new EpisodeSearchResult(
                                title, podcastTitle, feedUrl, episodeUrl, imageUrl, releaseDate));
                    }
                }
            } catch (IOException | JSONException e) {
                subscriber.onError(e);
                return;
            }
            subscriber.onSuccess(episodes);
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
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
