package de.danoeh.antennapod.net.discovery;

import androidx.annotation.NonNull;

public final class EpisodeSearchOptions {
    @NonNull
    public final EpisodeLanguage language;

    public EpisodeSearchOptions(@NonNull EpisodeLanguage language) {
        this.language = language;
    }

    public static EpisodeSearchOptions defaultOptions() {
        return new EpisodeSearchOptions(EpisodeLanguage.SYSTEM);
    }

    public String getCacheKey() {
        return language.getResolvedCacheKey();
    }
}
