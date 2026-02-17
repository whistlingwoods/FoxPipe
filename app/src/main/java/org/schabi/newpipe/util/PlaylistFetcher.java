/*
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util;

import android.util.Log;

import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Utility class for fetching complete playlists with pagination handling.
 */
public final class PlaylistFetcher {
    private static final String TAG = "PlaylistFetcher";

    private PlaylistFetcher() {
        // Utility class - no instances
    }

    /**
     * Callback interface for monitoring playlist fetching progress.
     */
    public interface ProgressCallback {
        /**
         * Called when progress is made in fetching playlist items.
         *
         * @param itemsFetched number of items fetched so far
         * @param totalItems   total number of items in the playlist
         *                     (from PlaylistInfo.streamCount), or -1 if unknown
         */
        void onProgress(int itemsFetched, int totalItems);
    }

    /**
     * Fetches ALL items from a playlist, handling pagination automatically.
     * This method will repeatedly fetch pages until all items are retrieved.
     *
     * @param serviceId        the service ID (e.g., YouTube = 0)
     * @param url              the playlist URL
     * @param initialInfo      the initial PlaylistInfo (typically from getPlaylistInfo())
     * @param progressCallback optional callback to monitor progress (can be null)
     * @return Single emitting a list of all StreamInfoItems in the playlist
     */
    public static Single<List<StreamInfoItem>> fetchAllPlaylistItems(
            final int serviceId,
            final String url,
            final PlaylistInfo initialInfo,
            @Nullable final ProgressCallback progressCallback) {

        return Single.fromCallable(() -> {
            // Start with items from the initial info
            final List<StreamInfoItem> allItems = new ArrayList<>(initialInfo.getRelatedItems());
            final int totalItems = (int) initialInfo.getStreamCount();
            Page nextPage = initialInfo.getNextPage();

            if (progressCallback != null) {
                progressCallback.onProgress(allItems.size(), totalItems);
            }

            Log.d(TAG, String.format(
                "Starting playlist fetch: %d initial items, total count: %d, hasNextPage: %s",
                allItems.size(), totalItems, nextPage != null
            ));

            // Fetch subsequent pages
            int pageNumber = 1;
            while (nextPage != null) {
                try {
                    // Fetch the next page
                    final InfoItemsPage<StreamInfoItem> page =
                        ExtractorHelper.getMorePlaylistItems(serviceId, url, nextPage)
                            .blockingGet();

                    if (page == null) {
                        Log.w(TAG, "Received null page at page " + pageNumber);
                        break;
                    }

                    final List<StreamInfoItem> pageItems = page.getItems();
                    if (pageItems.isEmpty()) {
                        Log.d(TAG, "Received empty page at page " + pageNumber + ", stopping");
                        break;
                    }

                    allItems.addAll(pageItems);
                    pageNumber++;

                    Log.d(TAG, String.format(
                        "Fetched page %d: %d items (total: %d/%d)",
                        pageNumber, pageItems.size(), allItems.size(), totalItems
                    ));

                    if (progressCallback != null) {
                        progressCallback.onProgress(allItems.size(), totalItems);
                    }

                    // Check if there's another page
                    nextPage = page.hasNextPage() ? page.getNextPage() : null;

                } catch (final Exception e) {
                    // Log the error but return what we've fetched so far
                    Log.e(TAG, "Error fetching page " + pageNumber
                        + ", returning items fetched so far", e);
                    break;
                }
            }

            Log.d(TAG, String.format(
                "Completed playlist fetch: %d total items fetched across %d pages",
                allItems.size(), pageNumber
            ));

            return allItems;

        }).subscribeOn(Schedulers.io());
    }

    /**
     * Fetches all items from a playlist without a progress callback.
     * Convenience method that calls
     * {@link #fetchAllPlaylistItems(int, String, PlaylistInfo, ProgressCallback)}
     * with a null callback.
     *
     * @param serviceId   the service ID
     * @param url         the playlist URL
     * @param initialInfo the initial PlaylistInfo
     * @return Single emitting a list of all StreamInfoItems in the playlist
     */
    public static Single<List<StreamInfoItem>> fetchAllPlaylistItems(
            final int serviceId,
            final String url,
            final PlaylistInfo initialInfo) {
        return fetchAllPlaylistItems(serviceId, url, initialInfo, null);
    }
}
