package org.schabi.newpipe.fragments.list.search;

import static androidx.recyclerview.widget.ItemTouchHelper.Callback.makeMovementFlags;
import static org.schabi.newpipe.extractor.utils.Utils.isBlank;
import static org.schabi.newpipe.ktx.ViewUtils.animate;
import static org.schabi.newpipe.util.ExtractorHelper.showMetaInfoInTextView;
import static java.util.Arrays.asList;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.CharacterStyle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.TooltipCompat;
import androidx.collection.SparseArrayCompat;
import androidx.core.text.HtmlCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.evernote.android.state.State;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.FragmentSearchBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.ReCaptchaActivity;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.MetaInfo;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.services.peertube.linkHandler.PeertubeSearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory;
import org.schabi.newpipe.fragments.BackPressable;
import org.schabi.newpipe.fragments.list.BaseListFragment;
import org.schabi.newpipe.ktx.AnimationType;
import org.schabi.newpipe.ktx.ExceptionUtils;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.settings.NewPipeSettings;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.KeyboardUtil;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ServiceHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

// ADD THIS IMPORT
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.local.blockedchannel.BlockedChannelManager;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;

public class SearchFragment extends BaseListFragment<SearchInfo, ListExtractor.InfoItemsPage<?>>
        implements BackPressable {

    private static final int THRESHOLD_NETWORK_SUGGESTION = 1;
    private static final int SUGGESTIONS_DEBOUNCE = 120; //ms
    private final PublishSubject<String> suggestionPublisher = PublishSubject.create();

    @State
    int filterItemCheckedId = -1;

    @State
    protected int serviceId = Constants.NO_SERVICE_ID;

    @State
    String searchString;

    @State
    String[] contentFilter = new String[0];

    @State
    String sortFilter;

    @State
    String lastSearchedString;

    @State
    String searchSuggestion;

    @State
    boolean isCorrectedSearch;

    @State
    MetaInfo[] metaInfo;

    @State
    boolean wasSearchFocused = false;

    private final SparseArrayCompat<String> menuItemToFilterName = new SparseArrayCompat<>();
    private StreamingService service;
    @Nullable
    private Page nextPage;
    private boolean showLocalSuggestions = true;
    private boolean showRemoteSuggestions = true;

    private Disposable searchDisposable;
    private Disposable suggestionDisposable;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private SuggestionListAdapter suggestionListAdapter;
    private HistoryRecordManager historyRecordManager;
    private BlockedChannelManager blockedChannelManager;
    private List<org.schabi.newpipe.database.blockedchannel.BlockedChannelEntity> blockedChannelsCache = new ArrayList<>();

    private FragmentSearchBinding searchBinding;
    private View searchToolbarContainer;
    private EditText searchEditText;
    private View searchClear;
    private boolean suggestionsPanelVisible = false;
    private TextWatcher textWatcher;

    public static SearchFragment getInstance(final int serviceId, final String searchString) {
        final SearchFragment searchFragment = new SearchFragment();
        searchFragment.setQuery(serviceId, searchString, new String[0], "");
        if (!TextUtils.isEmpty(searchString)) {
            searchFragment.setSearchOnResume();
        }
        return searchFragment;
    }

    private void setSearchOnResume() {
        wasLoading.set(true);
    }

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        showLocalSuggestions = NewPipeSettings.showLocalSearchSuggestions(activity, prefs);
        showRemoteSuggestions = NewPipeSettings.showRemoteSearchSuggestions(activity, prefs);
        suggestionListAdapter = new SuggestionListAdapter();
        historyRecordManager = new HistoryRecordManager(context);
        blockedChannelManager = new BlockedChannelManager(context);
        loadBlockedChannelsCache();
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View rootView, final Bundle savedInstanceState) {
        searchBinding = FragmentSearchBinding.bind(rootView);
        super.onViewCreated(rootView, savedInstanceState);
        updateService();
        if (service != null) {
            searchEditText.setHint(getString(R.string.search_with_service_name, service.getServiceInfo().getName()));
        }
        showSearchOnStart();
        initSearchListeners();
    }

    private void updateService() {
        try {
            service = NewPipe.getService(serviceId);
        } catch (final Exception e) {
            ErrorUtil.showUiErrorSnackbar(this, "Getting service for id " + serviceId, e);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        updateService();
    }

    @Override
    public void onPause() {
        super.onPause();
        wasSearchFocused = searchEditText.hasFocus();
        if (searchDisposable != null) { searchDisposable.dispose(); }
        if (suggestionDisposable != null) { suggestionDisposable.dispose(); }
        disposables.clear();
        hideKeyboardSearch();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (suggestionDisposable == null || suggestionDisposable.isDisposed()) { initSuggestionObserver(); }
        if (!TextUtils.isEmpty(searchString)) {
            if (wasLoading.getAndSet(false)) {
                search(searchString, contentFilter, sortFilter);
                return;
            } else if (infoListAdapter.getItemsList().isEmpty()) {
                if (savedState == null) {
                    search(searchString, contentFilter, sortFilter);
                    return;
                } else if (!isLoading.get() && !wasSearchFocused && lastPanelError == null) {
                    infoListAdapter.clearStreamItemList();
                    showEmptyState();
                }
            }
        }
        handleSearchSuggestion();
        showMetaInfoInTextView(metaInfo == null ? null : Arrays.asList(metaInfo), searchBinding.searchMetaInfoTextView, searchBinding.searchMetaInfoSeparator, disposables);
        if (TextUtils.isEmpty(searchString) || wasSearchFocused) {
            showKeyboardSearch();
            showSuggestionsPanel();
        } else {
            hideKeyboardSearch();
            hideSuggestionsPanel();
        }
        wasSearchFocused = false;
    }

    @Override
    public void onDestroyView() {
        unsetSearchListeners();
        searchBinding = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (searchDisposable != null) { searchDisposable.dispose(); }
        if (suggestionDisposable != null) { suggestionDisposable.dispose(); }
        disposables.clear();
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (requestCode == ReCaptchaActivity.RECAPTCHA_REQUEST) {
            if (resultCode == Activity.RESULT_OK && !TextUtils.isEmpty(searchString)) {
                search(searchString, contentFilter, sortFilter);
            }
        }
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        searchBinding.suggestionsList.setAdapter(suggestionListAdapter);
        searchBinding.suggestionsList.setItemAnimator(null);
        new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull final RecyclerView recyclerView, @NonNull final RecyclerView.ViewHolder viewHolder) {
                return getSuggestionMovementFlags(viewHolder);
            }
            @Override
            public boolean onMove(@NonNull final RecyclerView recyclerView, @NonNull final RecyclerView.ViewHolder viewHolder, @NonNull final RecyclerView.ViewHolder viewHolder1) {
                return false;
            }
            @Override
            public void onSwiped(@NonNull final RecyclerView.ViewHolder viewHolder, final int i) {
                onSuggestionItemSwiped(viewHolder);
            }
        }).attachToRecyclerView(searchBinding.suggestionsList);
        searchToolbarContainer = activity.findViewById(R.id.toolbar_search_container);
        searchEditText = searchToolbarContainer.findViewById(R.id.toolbar_search_edit_text);
        searchClear = searchToolbarContainer.findViewById(R.id.toolbar_search_clear);
    }

    @Override
    public void writeTo(final Queue<Object> objectsToSave) {
        super.writeTo(objectsToSave);
        objectsToSave.add(nextPage);
    }

    @Override
    public void readFrom(@NonNull final Queue<Object> savedObjects) throws Exception {
        super.readFrom(savedObjects);
        nextPage = (Page) savedObjects.poll();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle bundle) {
        searchString = searchEditText != null ? getSearchEditString().trim() : searchString;
        super.onSaveInstanceState(bundle);
    }

    @Override
    public void reloadContent() {
        if (!TextUtils.isEmpty(searchString) || (searchEditText != null && !isSearchEditBlank())) {
            search(!TextUtils.isEmpty(searchString) ? searchString : getSearchEditString(), this.contentFilter, "");
        } else {
            if (searchEditText != null) {
                searchEditText.setText("");
                showKeyboardSearch();
            }
            hideErrorPanel();
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu, @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        final ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayShowTitleEnabled(false);
            supportActionBar.setDisplayHomeAsUpEnabled(true);
        }
        int itemId = 0;
        boolean isFirstItem = true;
        final Context c = getContext();
        if (service == null) { updateService(); }
        for (final String filter : service.getSearchQHFactory().getAvailableContentFilter()) {
            menuItemToFilterName.put(itemId, filter);
            final MenuItem item = menu.add(1, itemId++, 0, ServiceHelper.getTranslatedFilterString(filter, c));
            if (isFirstItem) {
                item.setChecked(true);
                isFirstItem = false;
            }
        }
        menu.setGroupCheckable(1, true, true);
        restoreFilterChecked(menu, filterItemCheckedId);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final var filter = Collections.singletonList(menuItemToFilterName.get(item.getItemId()));
        changeContentFilter(item, filter);
        return true;
    }

    private void restoreFilterChecked(final Menu menu, final int itemId) {
        if (itemId != -1) {
            final MenuItem item = menu.findItem(itemId);
            if (item != null) { item.setChecked(true); }
        }
    }

    private void showSearchOnStart() {
        searchEditText.setText(searchString);
        if (TextUtils.isEmpty(searchString) || isSearchEditBlank()) {
            searchToolbarContainer.setTranslationX(100);
            searchToolbarContainer.setAlpha(0.0f);
            searchToolbarContainer.setVisibility(View.VISIBLE);
            searchToolbarContainer.animate().translationX(0).alpha(1.0f).setDuration(200).setInterpolator(new DecelerateInterpolator()).start();
        } else {
            searchToolbarContainer.setVisibility(View.VISIBLE);
        }
    }

    private void initSearchListeners() {
        searchClear.setOnClickListener(v -> {
            if (isSearchEditBlank()) {
                NavigationHelper.gotoMainFragment(getFM());
                return;
            }
            searchBinding.correctSuggestion.setVisibility(View.GONE);
            searchEditText.setText("");
            suggestionListAdapter.submitList(null);
            showKeyboardSearch();
        });
        searchEditText.setOnClickListener(v -> {
            if ((showLocalSuggestions || showRemoteSuggestions) && !isErrorPanelVisible()) { showSuggestionsPanel(); }
        });
        searchEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if ((showLocalSuggestions || showRemoteSuggestions) && hasFocus && !isErrorPanelVisible()) { showSuggestionsPanel(); }
        });
        suggestionListAdapter.setListener(new SuggestionListAdapter.OnSuggestionItemSelected() {
            @Override
            public void onSuggestionItemSelected(final SuggestionItem item) {
                search(item.query, new String[0], "");
                searchEditText.setText(item.query);
            }
            @Override
            public void onSuggestionItemInserted(final SuggestionItem item) {
                searchEditText.setText(item.query);
                searchEditText.setSelection(searchEditText.getText().length());
            }
            @Override
            public void onSuggestionItemLongClick(final SuggestionItem item) {
                if (item.fromHistory) { showDeleteSuggestionDialog(item); }
            }
        });
        textWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                for (final CharacterStyle span : s.getSpans(0, s.length(), CharacterStyle.class)) { s.removeSpan(span); }
                suggestionPublisher.onNext(getSearchEditString().trim());
            }
        };
        searchEditText.addTextChangedListener(textWatcher);
        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_PREVIOUS) { hideKeyboardSearch(); }
            else if (event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER || event.getAction() == EditorInfo.IME_ACTION_SEARCH)) {
                searchEditText.setText(getSearchEditString().trim());
                search(getSearchEditString(), new String[0], "");
                return true;
            }
            return false;
        });
        if (suggestionDisposable == null || suggestionDisposable.isDisposed()) { initSuggestionObserver(); }
    }

    private void unsetSearchListeners() {
        searchClear.setOnClickListener(null);
        searchEditText.setOnClickListener(null);
        searchEditText.setOnFocusChangeListener(null);
        searchEditText.setOnEditorActionListener(null);
        if (textWatcher != null) { searchEditText.removeTextChangedListener(textWatcher); }
        textWatcher = null;
    }

    private void showSuggestionsPanel() {
        suggestionsPanelVisible = true;
        animate(searchBinding.suggestionsPanel, true, 200, AnimationType.LIGHT_SLIDE_AND_ALPHA);
    }

    private void hideSuggestionsPanel() {
        suggestionsPanelVisible = false;
        animate(searchBinding.suggestionsPanel, false, 200, AnimationType.LIGHT_SLIDE_AND_ALPHA);
    }

    private void showKeyboardSearch() { KeyboardUtil.showKeyboard(activity, searchEditText); }
    private void hideKeyboardSearch() { KeyboardUtil.hideKeyboard(activity, searchEditText); }

    private void showDeleteSuggestionDialog(final SuggestionItem item) {
        new AlertDialog.Builder(activity)
                .setTitle(item.query)
                .setMessage(R.string.delete_item_search_history)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    disposables.add(historyRecordManager.deleteSearchHistory(item.query).observeOn(AndroidSchedulers.mainThread()).subscribe(h -> suggestionPublisher.onNext(getSearchEditString())));
                }).show();
    }

    @Override
    public boolean onBackPressed() {
        if (suggestionsPanelVisible && !infoListAdapter.getItemsList().isEmpty() && !isLoading.get()) {
            hideSuggestionsPanel();
            hideKeyboardSearch();
            searchEditText.setText(lastSearchedString);
            return true;
        }
        return false;
    }

    private Observable<List<SuggestionItem>> getLocalSuggestionsObservable(final String query, final int limit) {
        return historyRecordManager.getRelatedSearches(query, limit, 25).toObservable().map(entries -> entries.stream().map(e -> new SuggestionItem(true, e)).collect(Collectors.toList()));
    }

    private Observable<List<SuggestionItem>> getRemoteSuggestionsObservable(final String query) {
        return ExtractorHelper.suggestionsFor(serviceId, query).toObservable().map(strings -> strings.stream().map(s -> new SuggestionItem(false, s)).collect(Collectors.toList()));
    }

    private void initSuggestionObserver() {
        if (suggestionDisposable != null) { suggestionDisposable.dispose(); }
        suggestionDisposable = suggestionPublisher.debounce(SUGGESTIONS_DEBOUNCE, TimeUnit.MILLISECONDS)
                .startWithItem(searchString == null ? "" : searchString)
                .switchMap(query -> {
                    final boolean showRemote = showRemoteSuggestions && query.length() >= THRESHOLD_NETWORK_SUGGESTION;
                    if (showLocalSuggestions && showRemote) {
                        return Observable.zip(getLocalSuggestionsObservable(query, 3), getRemoteSuggestionsObservable(query), (l, r) -> {
                            r.removeIf(ri -> l.stream().anyMatch(li -> li.equals(ri)));
                            l.addAll(r);
                            return l;
                        }).materialize();
                    } else if (showLocalSuggestions) { return getLocalSuggestionsObservable(query, 25).materialize(); }
                    else if (showRemote) { return getRemoteSuggestionsObservable(query).materialize(); }
                    return Single.fromCallable(Collections::<SuggestionItem>emptyList).toObservable().materialize();
                }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(n -> { if (n.isOnNext() && n.getValue() != null) handleSuggestions(n.getValue()); });
    }

    @Override protected void doInitialLoadLogic() {}

    private void search(@NonNull final String theSearchString, final String[] theContentFilter, final String theSortFilter) {
        if (theSearchString.isEmpty()) return;
        try {
            final StreamingService ss = NewPipe.getServiceByUrl(theSearchString);
            showLoading();
            disposables.add(Observable.fromCallable(() -> NavigationHelper.getIntentByLink(activity, ss, theSearchString)).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(intent -> { getFM().popBackStackImmediate(); activity.startActivity(intent); }));
            return;
        } catch (Exception ignored) {}
        lastSearchedString = this.searchString;
        this.searchString = theSearchString;
        infoListAdapter.clearStreamItemList();
        hideSuggestionsPanel();
        showMetaInfoInTextView(null, searchBinding.searchMetaInfoTextView, searchBinding.searchMetaInfoSeparator, disposables);
        hideKeyboardSearch();
        disposables.add(historyRecordManager.onSearched(serviceId, theSearchString).observeOn(AndroidSchedulers.mainThread()).subscribe());
        suggestionPublisher.onNext(theSearchString);
        startLoading(false);
    }

    @Override
    public void startLoading(final boolean forceLoad) {
        super.startLoading(forceLoad);
        disposables.clear();
        if (searchDisposable != null) { searchDisposable.dispose(); }
        searchDisposable = ExtractorHelper.searchFor(serviceId, searchString, Arrays.asList(contentFilter), sortFilter).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).doOnEvent((r, t) -> isLoading.set(false)).subscribe(this::handleResult, this::onItemError);
    }

    @Override
    protected void loadMoreItems() {
        if (!Page.isValid(nextPage)) return;
        isLoading.set(true);
        showListFooter(true);
        if (searchDisposable != null) { searchDisposable.dispose(); }
        searchDisposable = ExtractorHelper.getMoreSearchItems(serviceId, searchString, asList(contentFilter), sortFilter, nextPage).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).doOnEvent((r, t) -> isLoading.set(false)).subscribe(this::handleNextItems, this::onItemError);
    }

    @Override protected boolean hasMoreItems() { return Page.isValid(nextPage); }
    @Override protected void onItemSelected(final InfoItem selectedItem) { super.onItemSelected(selectedItem); hideKeyboardSearch(); }

    private void onItemError(final Throwable exception) {
        if (exception instanceof SearchExtractor.NothingFoundException) {
            infoListAdapter.clearStreamItemList();
            showEmptyState();
        } else {
            showError(new ErrorInfo(exception, UserAction.SEARCHED, searchString, serviceId, getOpenInBrowserUrlForErrors()));
        }
    }

    @Nullable
    private String getOpenInBrowserUrlForErrors() {
        if (TextUtils.isEmpty(searchString)) return null;
        try { return service.getSearchQHFactory().getUrl(searchString, Arrays.asList(contentFilter), sortFilter); }
        catch (Exception ignored) { return null; }
    }

    private void changeContentFilter(final MenuItem item, final List<String> theContentFilter) {
        filterItemCheckedId = item.getItemId();
        item.setChecked(true);
        if (service != null) {
            final boolean isNotFiltered = theContentFilter.isEmpty() || "all".equals(theContentFilter.get(0));
            searchEditText.setHint(getString(isNotFiltered ? R.string.search_with_service_name : R.string.search_with_service_name_and_filter, service.getServiceInfo().getName(), item.getTitle()));
        }
        contentFilter = theContentFilter.toArray(new String[0]);
        if (!TextUtils.isEmpty(searchString)) { search(searchString, contentFilter, sortFilter); }
    }

    private void setQuery(final int sid, final String s, final String[] cf, final String sf) {
        serviceId = sid; searchString = s; contentFilter = cf; sortFilter = sf;
    }

    private String getSearchEditString() { return searchEditText.getText().toString(); }
    private boolean isSearchEditBlank() { return isBlank(getSearchEditString()); }

    public void handleSuggestions(@NonNull final List<SuggestionItem> suggestions) {
        if (DEBUG) {
            Log.d(TAG, "handleSuggestions() called with: suggestions = [" + suggestions + "]");
        }
        suggestionListAdapter.submitList(suggestions,
                () -> {
                    if (searchBinding != null) {
                        searchBinding.suggestionsList.scrollToPosition(0);
                    }
                });

        if (suggestionsPanelVisible && isErrorPanelVisible()) {
            hideLoading();
        }
    }

    @Override public void hideLoading() { super.hideLoading(); showListFooter(false); }

    /*//////////////////////////////////////////////////////////////////////////
    // MODIFIED Search Results (Filtering Shorts)
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void handleResult(@NonNull final SearchInfo result) {
        final List<Throwable> exceptions = result.getErrors();
        if (!exceptions.isEmpty() && !(exceptions.size() == 1 && exceptions.get(0) instanceof SearchExtractor.NothingFoundException)) {
            showSnackBarError(new ErrorInfo(result.getErrors(), UserAction.SEARCHED, searchString, serviceId, getOpenInBrowserUrlForErrors()));
        }

        searchSuggestion = result.getSearchSuggestion();
        if (searchSuggestion != null) { searchSuggestion = searchSuggestion.trim(); }
        isCorrectedSearch = result.isCorrectedSearch();
        metaInfo = result.getMetaInfo().toArray(new MetaInfo[0]);
        showMetaInfoInTextView(result.getMetaInfo(), searchBinding.searchMetaInfoTextView, searchBinding.searchMetaInfoSeparator, disposables);
        handleSearchSuggestion();
        lastSearchedString = searchString;
        nextPage = result.getNextPage();

        if (infoListAdapter.getItemsList().isEmpty()) {
            // FIX: SearchInfo uses getRelatedItems() for initial list, not getItems()
            List<InfoItem> itemsToProcess = result.getRelatedItems();
            List<InfoItem> filteredItems = new ArrayList<>();

            for (InfoItem item : itemsToProcess) {
                if (item instanceof StreamInfoItem) {
                    StreamInfoItem streamItem = (StreamInfoItem) item;
                    // Filter by Short tag OR duration <= 60 seconds
                    if (streamItem.isShortFormContent() || streamItem.getDuration() <= 60) {
                        continue;
                    }
                    // Filter by blocked channels
                    if (isChannelBlockedCached(streamItem.getServiceId(), streamItem.getUploaderUrl())) {
                        continue;
                    }
                }
                filteredItems.add(item);
            }

            if (!filteredItems.isEmpty()) {
                infoListAdapter.addInfoItemList(filteredItems);
            } else {
                infoListAdapter.clearStreamItemList();
                showEmptyState();
                return;
            }
        }
        super.handleResult(result);
    }

    private void handleSearchSuggestion() {
        if (TextUtils.isEmpty(searchSuggestion)) {
            searchBinding.correctSuggestion.setVisibility(View.GONE);
        } else {
            final String helperText = getString(isCorrectedSearch ? R.string.search_showing_result_for : R.string.did_you_mean);
            final String high = "<b><i>" + Html.escapeHtml(searchSuggestion) + "</i></b>";
            searchBinding.correctSuggestion.setText(HtmlCompat.fromHtml(String.format(helperText, high), HtmlCompat.FROM_HTML_MODE_LEGACY));
            searchBinding.correctSuggestion.setOnClickListener(v -> {
                searchBinding.correctSuggestion.setVisibility(View.GONE);
                search(searchSuggestion, contentFilter, sortFilter);
                searchEditText.setText(searchSuggestion);
            });
            searchBinding.correctSuggestion.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void handleNextItems(final ListExtractor.InfoItemsPage<?> result) {
        showListFooter(false);

        List<InfoItem> filteredItems = new ArrayList<>();
        // In handleNextItems, getItems() IS valid as it returns ListExtractor.InfoItemsPage
        for (InfoItem item : result.getItems()) {
            if (item instanceof StreamInfoItem) {
                StreamInfoItem streamItem = (StreamInfoItem) item;
                if (streamItem.isShortFormContent() || streamItem.getDuration() <= 60) {
                    continue;
                }
                // Filter by blocked channels
                if (isChannelBlockedCached(streamItem.getServiceId(), streamItem.getUploaderUrl())) {
                    continue;
                }
            }
            filteredItems.add(item);
        }
        infoListAdapter.addInfoItemList(filteredItems);
        nextPage = result.getNextPage();
        super.handleNextItems(result);
    }

    @Override public void handleError() { super.handleError(); hideSuggestionsPanel(); hideKeyboardSearch(); }

    public int getSuggestionMovementFlags(@NonNull final RecyclerView.ViewHolder viewHolder) {
        final int position = viewHolder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) return 0;
        final SuggestionItem item = suggestionListAdapter.getCurrentList().get(position);
        return item.fromHistory ? makeMovementFlags(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) : 0;
    }

    private void loadBlockedChannelsCache() {
        disposables.add(blockedChannelManager.blockedChannels()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(channels -> blockedChannelsCache = new ArrayList<>(channels)));
    }

    private boolean isChannelBlockedCached(final int serviceId, final String url) {
        return blockedChannelsCache.stream()
                .anyMatch(channel -> channel.getServiceId() == serviceId && url != null && url.equals(channel.getUrl()));
    }

    public void onSuggestionItemSwiped(@NonNull final RecyclerView.ViewHolder viewHolder) {
        final int position = viewHolder.getBindingAdapterPosition();
        final String query = suggestionListAdapter.getCurrentList().get(position).query;
        disposables.add(historyRecordManager.deleteSearchHistory(query).observeOn(AndroidSchedulers.mainThread()).subscribe(h -> suggestionPublisher.onNext(getSearchEditString())));
    }
}
