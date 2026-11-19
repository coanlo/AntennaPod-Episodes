package de.danoeh.antennapod.net.discovery;

import androidx.annotation.Nullable;

import java.util.Date;

public class EpisodeSearchResult {
    public final String title;
    @Nullable
    public final String podcastTitle;
    @Nullable
    public final String feedUrl;
    @Nullable
    public final String episodeUrl;
    @Nullable
    public final String imageUrl;
    @Nullable
    public final Date releaseDate;
    public final long durationMs;
    @Nullable
    public final String language;
    public final float relevanceScore;
    public final float popularityScore;

    public EpisodeSearchResult(String title, @Nullable String podcastTitle, @Nullable String feedUrl,
                               @Nullable String episodeUrl, @Nullable String imageUrl,
                               @Nullable Date releaseDate) {
        this(title, podcastTitle, feedUrl, episodeUrl, imageUrl, releaseDate, 0L, null, 0f, 0f);
    }

    public EpisodeSearchResult(String title, @Nullable String podcastTitle, @Nullable String feedUrl,
                               @Nullable String episodeUrl, @Nullable String imageUrl,
                               @Nullable Date releaseDate, long durationMs, @Nullable String language,
                               float relevanceScore, float popularityScore) {
        this.title = title;
        this.podcastTitle = podcastTitle;
        this.feedUrl = feedUrl;
        this.episodeUrl = episodeUrl;
        this.imageUrl = imageUrl;
        this.releaseDate = releaseDate != null ? new Date(releaseDate.getTime()) : null;
        this.durationMs = Math.max(0L, durationMs);
        this.language = language;
        this.relevanceScore = relevanceScore;
        this.popularityScore = popularityScore;
    }

    @Nullable
    public Date getReleaseDate() {
        return releaseDate != null ? new Date(releaseDate.getTime()) : null;
    }
}
