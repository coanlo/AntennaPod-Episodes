package de.danoeh.antennapod.net.discovery;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;

import java.util.List;

public interface EpisodeSearcher {
    Single<List<EpisodeSearchResult>> search(String query);

    Observable<List<EpisodeSearchResult>> searchStream(String query, EpisodeSearchOptions options);

    String getName();
}
