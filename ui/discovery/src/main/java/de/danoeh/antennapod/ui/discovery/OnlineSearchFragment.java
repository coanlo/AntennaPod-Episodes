package de.danoeh.antennapod.ui.discovery;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.net.discovery.CombinedEpisodeSearcher;
import de.danoeh.antennapod.net.discovery.EpisodeSearchResult;
import de.danoeh.antennapod.net.discovery.EpisodeSearcher;
import de.danoeh.antennapod.net.discovery.FyydEpisodeSearcher;
import de.danoeh.antennapod.net.discovery.FyydPodcastSearcher;
import de.danoeh.antennapod.net.discovery.ItunesEpisodeSearcher;
import de.danoeh.antennapod.net.discovery.ItunesPodcastSearcher;
import de.danoeh.antennapod.net.discovery.PodcastSearchResult;
import de.danoeh.antennapod.net.discovery.PodcastSearcher;
import de.danoeh.antennapod.net.discovery.PodcastSearcherRegistry;
import de.danoeh.antennapod.ui.appstartintent.OnlineFeedviewActivityStarter;
import de.danoeh.antennapod.ui.common.Keyboard;
import io.reactivex.rxjava3.disposables.Disposable;

public class OnlineSearchFragment extends Fragment {

    private static final String TAG = "FyydSearchFragment";
    private static final String ARG_SEARCHER = "searcher";
    private static final String ARG_QUERY = "query";
    private static final String KEY_EPISODE_SEARCH = "episode_search";
    private static final String KEY_QUERY = "saved_query";

    /**
     * Adapter responsible with the search results
     */
    private OnlineSearchAdapter adapter;
    private EpisodeSearchAdapter episodeAdapter;
    private PodcastSearcher searchProvider;
    private EpisodeSearcher episodeSearchProvider;
    private GridView gridView;
    private ProgressBar progressBar;
    private TextView txtvError;
    private Button butRetry;
    private TextView txtvEmpty;
    private TextView txtvPoweredBy;
    private SearchView searchView;
    private boolean isEpisodeSearch;
    private String lastPodcastQuery;
    private String lastEpisodeQuery;
    private String savedQuery;

    /**
     * List of podcasts retreived from the search
     */
    private List<PodcastSearchResult> searchResults;
    private List<EpisodeSearchResult> episodeSearchResults;
    private Disposable disposable;

    public static OnlineSearchFragment newInstance(Class<? extends PodcastSearcher> searchProvider) {
        return newInstance(searchProvider, null);
    }

    public static OnlineSearchFragment newInstance(Class<? extends PodcastSearcher> searchProvider, String query) {
        OnlineSearchFragment fragment = new OnlineSearchFragment();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_SEARCHER, searchProvider.getName());
        arguments.putString(ARG_QUERY, query);
        fragment.setArguments(arguments);
        return fragment;
    }

    /**
     * Constructor
     */
    public OnlineSearchFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        for (PodcastSearcherRegistry.SearcherInfo info : PodcastSearcherRegistry.getSearchProviders()) {
            if (info.searcher.getClass().getName().equals(getArguments().getString(ARG_SEARCHER))) {
                searchProvider = info.searcher;
                break;
            }
        }
        if (searchProvider == null) {
            throw new IllegalArgumentException("Podcast searcher not found");
        }
        if (searchProvider instanceof ItunesPodcastSearcher) {
            episodeSearchProvider = new ItunesEpisodeSearcher();
        } else if (searchProvider instanceof FyydPodcastSearcher) {
            episodeSearchProvider = new FyydEpisodeSearcher();
        } else {
            episodeSearchProvider = new CombinedEpisodeSearcher();
        }
        if (savedInstanceState != null) {
            isEpisodeSearch = savedInstanceState.getBoolean(KEY_EPISODE_SEARCH, false);
            savedQuery = savedInstanceState.getString(KEY_QUERY);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_online_search, container, false);
        gridView = root.findViewById(R.id.gridView);
        adapter = new OnlineSearchAdapter(getActivity(), new ArrayList<>());
        episodeAdapter = new EpisodeSearchAdapter(getActivity(), new ArrayList<>());
        gridView.setAdapter(isEpisodeSearch ? episodeAdapter : adapter);

        //Show information about the podcast when the list item is clicked
        gridView.setOnItemClickListener((parent, view1, position, id) -> {
            if (isEpisodeSearch) {
                if (episodeSearchResults == null || position < 0 || position >= episodeSearchResults.size()) {
                    return;
                }
                EpisodeSearchResult episode = episodeSearchResults.get(position);
                if (episode.feedUrl == null) {
                    return;
                }
                startActivity(new OnlineFeedviewActivityStarter(getContext(), episode.feedUrl)
                        .withEpisode(episode.title, episode.episodeUrl)
                        .getIntent());
            } else {
                if (searchResults == null || position < 0 || position >= searchResults.size()) {
                    return;
                }
                PodcastSearchResult podcast = searchResults.get(position);
                startActivity(new OnlineFeedviewActivityStarter(getContext(), podcast.feedUrl).getIntent());
            }
        });
        progressBar = root.findViewById(R.id.progressBar);
        txtvError = root.findViewById(R.id.txtvError);
        butRetry = root.findViewById(R.id.butRetry);
        txtvEmpty = root.findViewById(android.R.id.empty);
        txtvPoweredBy = root.findViewById(R.id.search_powered_by);
        setupSearchTabs(root.findViewById(R.id.search_tabs));
        setupToolbar(root.findViewById(R.id.toolbar));
        updateModeUi();

        gridView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == SCROLL_STATE_TOUCH_SCROLL) {
                    Keyboard.hide(getActivity());
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            }
        });
        return root;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_EPISODE_SEARCH, isEpisodeSearch);
        if (searchView != null) {
            outState.putString(KEY_QUERY, searchView.getQuery().toString());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (disposable != null) {
            disposable.dispose();
        }
        adapter = null;
        episodeAdapter = null;
    }

    private void setupSearchTabs(TabLayout searchTabs) {
        searchTabs.setVisibility(View.VISIBLE);
        searchTabs.addTab(searchTabs.newTab().setText(R.string.search_podcasts_tab));
        searchTabs.addTab(searchTabs.newTab().setText(R.string.episodes_label));
        if (isEpisodeSearch) {
            TabLayout.Tab episodesTab = searchTabs.getTabAt(1);
            if (episodesTab != null) {
                episodesTab.select();
            }
        }
        searchTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                boolean episodeMode = tab.getPosition() == 1;
                if (episodeMode == isEpisodeSearch) {
                    return;
                }
                isEpisodeSearch = episodeMode;
                if (disposable != null) {
                    disposable.dispose();
                }
                updateModeUi();
                String currentQuery = searchView != null ? searchView.getQuery().toString().trim() : "";
                if (currentQuery.isEmpty()) {
                    clearResultsViews();
                } else if (isEpisodeSearch && TextUtils.equals(currentQuery, lastEpisodeQuery)
                        && episodeSearchResults != null) {
                    showEpisodeResults(episodeSearchResults, currentQuery);
                } else if (!isEpisodeSearch && TextUtils.equals(currentQuery, lastPodcastQuery)
                        && searchResults != null) {
                    showPodcastResults(searchResults, currentQuery);
                } else {
                    search(currentQuery);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private void updateModeUi() {
        if (txtvPoweredBy != null) {
            String providerName = isEpisodeSearch ? episodeSearchProvider.getName() : searchProvider.getName();
            txtvPoweredBy.setText(getString(R.string.search_powered_by, providerName));
        }
        if (searchView != null) {
            searchView.setQueryHint(getString(
                    isEpisodeSearch ? R.string.search_episode_hint : R.string.search_podcast_hint));
        }
        if (gridView != null) {
            gridView.setAdapter(isEpisodeSearch ? episodeAdapter : adapter);
        }
    }

    private void clearResultsViews() {
        progressBar.setVisibility(View.GONE);
        txtvError.setVisibility(View.GONE);
        butRetry.setVisibility(View.GONE);
        txtvEmpty.setVisibility(View.GONE);
        gridView.setVisibility(View.GONE);
    }

    private void setupToolbar(MaterialToolbar toolbar) {
        toolbar.inflateMenu(R.menu.online_search);
        toolbar.setNavigationOnClickListener(v -> getParentFragmentManager().popBackStack());

        MenuItem searchItem = toolbar.getMenu().findItem(R.id.action_search);
        final SearchView sv = (SearchView) searchItem.getActionView();
        searchView = sv;
        sv.setQueryHint(getString(isEpisodeSearch ? R.string.search_episode_hint : R.string.search_podcast_hint));
        sv.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                sv.clearFocus();
                search(s);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                return false;
            }
        });
        sv.setOnQueryTextFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                Keyboard.show(getContext(), view.findFocus());
            }
        });
        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                getActivity().getSupportFragmentManager().popBackStack();
                return true;
            }
        });
        searchItem.expandActionView();

        String initialQuery = savedQuery != null ? savedQuery : getArguments().getString(ARG_QUERY, null);
        if (initialQuery != null && !initialQuery.trim().isEmpty()) {
            sv.setQuery(initialQuery, true);
        }
    }

    private void search(String query) {
        if (disposable != null) {
            disposable.dispose();
        }
        showOnlyProgressBar();
        if (isEpisodeSearch) {
            lastEpisodeQuery = query.trim();
            disposable = episodeSearchProvider.search(query).subscribe(
                    result -> showEpisodeResults(result, query),
                    error -> showSearchError(query, error));
        } else {
            lastPodcastQuery = query.trim();
            disposable = searchProvider.search(query).subscribe(
                    result -> showPodcastResults(result, query),
                    error -> showSearchError(query, error));
        }
    }

    private void showPodcastResults(List<PodcastSearchResult> result, String query) {
        searchResults = result;
        progressBar.setVisibility(View.GONE);
        txtvError.setVisibility(View.GONE);
        butRetry.setVisibility(View.GONE);
        adapter.clear();
        adapter.addAll(searchResults);
        adapter.notifyDataSetInvalidated();
        gridView.setVisibility(!searchResults.isEmpty() ? View.VISIBLE : View.GONE);
        txtvEmpty.setVisibility(searchResults.isEmpty() ? View.VISIBLE : View.GONE);
        txtvEmpty.setText(getString(R.string.no_results_for_query, query));
    }

    private void showEpisodeResults(List<EpisodeSearchResult> result, String query) {
        episodeSearchResults = result;
        progressBar.setVisibility(View.GONE);
        txtvError.setVisibility(View.GONE);
        butRetry.setVisibility(View.GONE);
        episodeAdapter.clear();
        episodeAdapter.addAll(episodeSearchResults);
        episodeAdapter.notifyDataSetInvalidated();
        gridView.setVisibility(!episodeSearchResults.isEmpty() ? View.VISIBLE : View.GONE);
        txtvEmpty.setVisibility(episodeSearchResults.isEmpty() ? View.VISIBLE : View.GONE);
        txtvEmpty.setText(getString(R.string.no_results_for_query, query));
    }

    private void showSearchError(String query, Throwable error) {
        Log.e(TAG, Log.getStackTraceString(error));
        progressBar.setVisibility(View.GONE);
        txtvError.setText(error.toString());
        txtvError.setVisibility(View.VISIBLE);
        butRetry.setOnClickListener(v -> search(query));
        butRetry.setVisibility(View.VISIBLE);
    }

    private void showOnlyProgressBar() {
        gridView.setVisibility(View.GONE);
        txtvError.setVisibility(View.GONE);
        butRetry.setVisibility(View.GONE);
        txtvEmpty.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
    }
}
