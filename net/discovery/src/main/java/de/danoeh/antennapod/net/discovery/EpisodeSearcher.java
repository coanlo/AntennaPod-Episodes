package de.danoeh.antennapod.net.discovery;

import io.reactivex.rxjava3.core.Single;

import java.util.List;

public interface EpisodeSearcher {
    Single<List<EpisodeSearchResult>> search(String query);

    String getName();
}
