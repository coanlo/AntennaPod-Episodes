package de.danoeh.antennapod.ui.discovery;

import android.content.Context;
import android.content.SharedPreferences;
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
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.danoeh.antennapod.net.discovery.CombinedEpisodeSearcher;
import de.danoeh.antennapod.net.discovery.CombinedSearcher;
import de.danoeh.antennapod.net.discovery.EpisodeLanguage;
import de.danoeh.antennapod.net.discovery.EpisodeSearchOptions;
import de.danoeh.antennapod.net.discovery.EpisodeSearchResult;
import de.danoeh.antennapod.net.discovery.EpisodeSearcher;
import de.danoeh.antennapod.net.discovery.EpisodeSortOrder;
import de.danoeh.antennapod.net.discovery.FyydEpisodeSearcher;
import de.danoeh.antennapod.net.discovery.FyydPodcastSearcher;
import de.danoeh.antennapod.net.discovery.ItunesEpisodeSearcher;
import de.danoeh.antennapod.net.discovery.ItunesPodcastSearcher;
import de.danoeh.antennapod.net.discovery.PodcastSearchResult;
import de.danoeh.antennapod.net.discovery.PodcastSearcher;
import de.danoeh.antennapod.net.discovery.PodcastSearcherRegistry;
import de.danoeh.antennapod.ui.appstartintent.OnlineFeedviewActivityStarter;
import de.danoeh.antennapod.ui.common.Keyboard;
import de.danoeh.antennapod.ui.common.NavigationToolbarActivity;
import io.reactivex.rxjava3.disposables.Disposable;

public class OnlineSearchFragment extends Fragment {

    public static final String TAG_EPISODE_SEARCH = "EpisodeSearchFragment";

    private static final String TAG = "FyydSearchFragment";
    private static final String PREFS_NAME = "OnlineEpisodeSearchPrefs";
    private static final String PREF_EPISODE_LANGUAGE = "episode_language";
    private static final String PREF_EPISODE_SORT = "episode_sort";
    private static final String ARG_SEARCHER = "searcher";
    private static final String ARG_QUERY = "query";
    private static final String ARG_DEFAULT_EPISODE_SEARCH = "default_episode_search";
    private static final String ARG_TOP_LEVEL = "top_level";
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
    private LinearProgressIndicator loadingMoreIndicator;
    private View episodeFilterBar;
    private Chip chipSort;
    private Chip chipLanguage;
    private TextView txtvError;
    private Button butRetry;
    private TextView txtvEmpty;
    private TextView txtvPoweredBy;
    private SearchView searchView;
    private SharedPreferences prefs;
    private boolean isEpisodeSearch;
    private boolean isTopLevel;
    private EpisodeLanguage selectedLanguage = EpisodeLanguage.SYSTEM;
    private EpisodeSortOrder selectedSortOrder = EpisodeSortOrder.RELEVANCE;
    private String lastPodcastQuery;
    private String lastEpisodeQuery;
    private String lastEpisodeLanguageKey;
    private String savedQuery;

    /**
     * List of podcasts retreived from the search
     */
    private List<PodcastSearchResult> searchResults;
    private List<EpisodeSearchResult> rawEpisodeSearchResults;
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

    public static OnlineSearchFragment newEpisodeSearchInstance() {
        OnlineSearchFragment fragment = new OnlineSearchFragment();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_SEARCHER, CombinedSearcher.class.getName());
        arguments.putBoolean(ARG_DEFAULT_EPISODE_SEARCH, true);
        arguments.putBoolean(ARG_TOP_LEVEL, true);
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

        Bundle args = getArguments();
        String searcherName = args != null ? args.getString(ARG_SEARCHER) : CombinedSearcher.class.getName();
        for (PodcastSearcherRegistry.SearcherInfo info : PodcastSearcherRegistry.getSearchProviders()) {
            if (info.searcher.getClass().getName().equals(searcherName)) {
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
        isTopLevel = args != null && args.getBoolean(ARG_TOP_LEVEL, false);
        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        selectedLanguage = EpisodeLanguage.fromStorageKey(prefs.getString(PREF_EPISODE_LANGUAGE, null));
        selectedSortOrder = EpisodeSortOrder.fromStorageKey(prefs.getString(PREF_EPISODE_SORT, null));
        if (savedInstanceState != null) {
            isEpisodeSearch = savedInstanceState.getBoolean(KEY_EPISODE_SEARCH, false);
            savedQuery = savedInstanceState.getString(KEY_QUERY);
        } else {
            isEpisodeSearch = args != null && args.getBoolean(ARG_DEFAULT_EPISODE_SEARCH, false);
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
        loadingMoreIndicator = root.findViewById(R.id.loading_more_indicator);
        episodeFilterBar = root.findViewById(R.id.episode_filter_bar);
        chipSort = root.findViewById(R.id.chip_sort);
        chipLanguage = root.findViewById(R.id.chip_language);
        txtvError = root.findViewById(R.id.txtvError);
        butRetry = root.findViewById(R.id.butRetry);
        txtvEmpty = root.findViewById(android.R.id.empty);
        txtvPoweredBy = root.findViewById(R.id.search_powered_by);

        chipSort.setOnClickListener(v -> showSortDialog());
        chipLanguage.setOnClickListener(v -> showLanguageDialog());
        updateFilterChipLabels();

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
    public void onPause() {
        super.onPause();
        Keyboard.hide(getActivity());
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
    public void onDestroyView() {
        super.onDestroyView();
        if (disposable != null) {
            disposable.dispose();
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
                loadingMoreIndicator.setVisibility(View.GONE);
                updateModeUi();
                String currentQuery = searchView != null ? searchView.getQuery().toString().trim() : "";
                if (currentQuery.isEmpty()) {
                    showIdlePrompt();
                } else if (isEpisodeSearch && TextUtils.equals(currentQuery, lastEpisodeQuery)
                        && TextUtils.equals(selectedLanguage.getResolvedCacheKey(), lastEpisodeLanguageKey)
                        && rawEpisodeSearchResults != null) {
                    showEpisodeResults(rawEpisodeSearchResults, currentQuery, false);
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
        if (episodeFilterBar != null) {
            episodeFilterBar.setVisibility(isEpisodeSearch ? View.VISIBLE : View.GONE);
        }
        if (gridView != null) {
            gridView.setAdapter(isEpisodeSearch ? episodeAdapter : adapter);
        }
    }

    private void updateFilterChipLabels() {
        if (chipSort != null) {
            chipSort.setText(getSortOrderLabel(selectedSortOrder));
        }
        if (chipLanguage != null) {
            chipLanguage.setText(getLanguageLabel(selectedLanguage));
        }
    }

    private void showSortDialog() {
        EpisodeSortOrder[] orders = EpisodeSortOrder.values();
        String[] labels = new String[orders.length];
        int checkedIndex = 0;
        for (int i = 0; i < orders.length; i++) {
            labels[i] = getSortOrderLabel(orders[i]);
            if (orders[i] == selectedSortOrder) {
                checkedIndex = i;
            }
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.sort)
                .setSingleChoiceItems(labels, checkedIndex, (dialog, which) -> {
                    selectedSortOrder = orders[which];
                    prefs.edit().putString(PREF_EPISODE_SORT, selectedSortOrder.getStorageKey()).apply();
                    updateFilterChipLabels();
                    if (rawEpisodeSearchResults != null && lastEpisodeQuery != null) {
                        showEpisodeResults(rawEpisodeSearchResults, lastEpisodeQuery, true);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private void showLanguageDialog() {
        EpisodeLanguage[] languages = EpisodeLanguage.values();
        String[] labels = new String[languages.length];
        int checkedIndex = 0;
        for (int i = 0; i < languages.length; i++) {
            labels[i] = getLanguageLabel(languages[i]);
            if (languages[i] == selectedLanguage) {
                checkedIndex = i;
            }
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.episode_language_title)
                .setSingleChoiceItems(labels, checkedIndex, (dialog, which) -> {
                    EpisodeLanguage chosen = languages[which];
                    dialog.dismiss();
                    if (chosen == selectedLanguage) {
                        return;
                    }
                    selectedLanguage = chosen;
                    prefs.edit().putString(PREF_EPISODE_LANGUAGE, selectedLanguage.getStorageKey()).apply();
                    updateFilterChipLabels();
                    String currentQuery = searchView != null ? searchView.getQuery().toString().trim() : "";
                    if (!currentQuery.isEmpty() && isEpisodeSearch) {
                        search(currentQuery);
                    }
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private String getSortOrderLabel(EpisodeSortOrder order) {
        switch (order) {
            case NEWEST:
                return getString(R.string.episode_sort_newest);
            case OLDEST:
                return getString(R.string.episode_sort_oldest);
            case POPULAR:
                return getString(R.string.episode_sort_popular);
            case SHORTEST:
                return getString(R.string.episode_sort_shortest);
            case LONGEST:
                return getString(R.string.episode_sort_longest);
            case RELEVANCE:
            default:
                return getString(R.string.episode_sort_relevance);
        }
    }

    private String getLanguageLabel(EpisodeLanguage language) {
        if (language == EpisodeLanguage.ALL) {
            return getString(R.string.episode_language_all);
        }
        String localizedName = language.getLocalizedLanguageName(Locale.getDefault());
        if (language == EpisodeLanguage.SYSTEM) {
            return getString(R.string.episode_language_system, localizedName);
        }
        return localizedName;
    }

    private void showIdlePrompt() {
        progressBar.setVisibility(View.GONE);
        loadingMoreIndicator.setVisibility(View.GONE);
        txtvError.setVisibility(View.GONE);
        butRetry.setVisibility(View.GONE);
        gridView.setVisibility(View.GONE);
        if (isTopLevel) {
            txtvEmpty.setText(R.string.type_to_search);
            txtvEmpty.setVisibility(View.VISIBLE);
        } else {
            txtvEmpty.setVisibility(View.GONE);
        }
    }

    private void setupToolbar(MaterialToolbar toolbar) {
        toolbar.inflateMenu(R.menu.online_search);
        if (isTopLevel) {
            toolbar.setTitle(R.string.episode_search_label);
            if (getActivity() instanceof NavigationToolbarActivity) {
                ((NavigationToolbarActivity) getActivity()).setupToolbarToggle(toolbar, false);
            }
        } else {
            toolbar.setNavigationOnClickListener(v -> getParentFragmentManager().popBackStack());
        }

        MenuItem searchItem = toolbar.getMenu().findItem(R.id.action_search);
        final SearchView sv = (SearchView) searchItem.getActionView();
        searchView = sv;
        sv.setQueryHint(getString(isEpisodeSearch ? R.string.search_episode_hint : R.string.search_podcast_hint));
        sv.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                sv.clearFocus();
                Keyboard.hide(getActivity());
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
                if (!isTopLevel && getParentFragmentManager().getBackStackEntryCount() > 0) {
                    getActivity().getSupportFragmentManager().popBackStack();
                } else {
                    Keyboard.hide(getActivity());
                }
                return true;
            }
        });
        searchItem.expandActionView();

        String initialQuery = savedQuery != null
                ? savedQuery : (getArguments() != null ? getArguments().getString(ARG_QUERY, null) : null);
        if (initialQuery != null && !initialQuery.trim().isEmpty()) {
            sv.setQuery(initialQuery, true);
        } else if (isTopLevel) {
            showIdlePrompt();
        }
    }

    private void search(String query) {
        String trimmedQuery = query == null ? "" : query.trim();
        if (trimmedQuery.isEmpty()) {
            showIdlePrompt();
            return;
        }
        if (disposable != null) {
            disposable.dispose();
        }
        showOnlyProgressBar();
        if (isEpisodeSearch) {
            lastEpisodeQuery = trimmedQuery;
            lastEpisodeLanguageKey = selectedLanguage.getResolvedCacheKey();
            EpisodeSearchOptions options = new EpisodeSearchOptions(selectedLanguage);
            disposable = episodeSearchProvider.searchStream(trimmedQuery, options).subscribe(
                    result -> {
                        boolean firstEmission = progressBar.getVisibility() == View.VISIBLE;
                        loadingMoreIndicator.setVisibility(View.VISIBLE);
                        showEpisodeResults(result, trimmedQuery, firstEmission);
                    },
                    error -> showSearchError(trimmedQuery, error),
                    () -> {
                        progressBar.setVisibility(View.GONE);
                        loadingMoreIndicator.setVisibility(View.GONE);
                    });
        } else {
            lastPodcastQuery = trimmedQuery;
            disposable = searchProvider.search(trimmedQuery).subscribe(
                    result -> showPodcastResults(result, trimmedQuery),
                    error -> showSearchError(trimmedQuery, error));
        }
    }

    private void showPodcastResults(List<PodcastSearchResult> result, String query) {
        searchResults = result;
        progressBar.setVisibility(View.GONE);
        loadingMoreIndicator.setVisibility(View.GONE);
        txtvError.setVisibility(View.GONE);
        butRetry.setVisibility(View.GONE);
        adapter.clear();
        adapter.addAll(searchResults);
        adapter.notifyDataSetInvalidated();
        gridView.setVisibility(!searchResults.isEmpty() ? View.VISIBLE : View.GONE);
        txtvEmpty.setVisibility(searchResults.isEmpty() ? View.VISIBLE : View.GONE);
        txtvEmpty.setText(getString(R.string.no_results_for_query, query));
    }

    private void showEpisodeResults(List<EpisodeSearchResult> result, String query, boolean scrollToTop) {
        rawEpisodeSearchResults = result;
        episodeSearchResults = selectedSortOrder.sort(result);
        progressBar.setVisibility(View.GONE);
        txtvError.setVisibility(View.GONE);
        butRetry.setVisibility(View.GONE);
        episodeAdapter.clear();
        episodeAdapter.addAll(episodeSearchResults);
        episodeAdapter.notifyDataSetChanged();
        if (scrollToTop && !episodeSearchResults.isEmpty()) {
            gridView.setSelection(0);
        }
        gridView.setVisibility(!episodeSearchResults.isEmpty() ? View.VISIBLE : View.GONE);
        txtvEmpty.setVisibility(episodeSearchResults.isEmpty() ? View.VISIBLE : View.GONE);
        txtvEmpty.setText(getString(R.string.no_results_for_query, query));
    }

    private void showSearchError(String query, Throwable error) {
        Log.e(TAG, Log.getStackTraceString(error));
        progressBar.setVisibility(View.GONE);
        loadingMoreIndicator.setVisibility(View.GONE);
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
        loadingMoreIndicator.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
    }
}
