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

    public EpisodeSearchResult(String title, @Nullable String podcastTitle, @Nullable String feedUrl,
                               @Nullable String episodeUrl, @Nullable String imageUrl,
                               @Nullable Date releaseDate) {
        this.title = title;
        this.podcastTitle = podcastTitle;
        this.feedUrl = feedUrl;
        this.episodeUrl = episodeUrl;
        this.imageUrl = imageUrl;
        this.releaseDate = releaseDate != null ? new Date(releaseDate.getTime()) : null;
    }

    @Nullable
    public Date getReleaseDate() {
        return releaseDate != null ? new Date(releaseDate.getTime()) : null;
    }
}
