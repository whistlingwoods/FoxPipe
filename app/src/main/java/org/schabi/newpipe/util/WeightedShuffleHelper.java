package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.dao.StreamDAO;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Helper class for weighted shuffling based on stream ratings.
 * Unrated streams get configurable weight to encourage users to rate them.
 * Supports adjustable curve exponent for different weighting behaviors.
 */
public final class WeightedShuffleHelper {
    private static final int MAX_RATING = 10;
    private static final Random RANDOM = new Random();

    private WeightedShuffleHelper() {
        // Utility class
    }

    /**
     * Performs weighted shuffle on play queue items based on ratings.
     * Unrated items get configurable weight, rated items get weight based on rating and curve.
     *
     * @param context the context for database access and preferences
     * @param items the items to shuffle
     * @param currentItem the currently playing item (will be placed first)
     * @return shuffled list with currentItem at index 0
     */
    @NonNull
    public static List<PlayQueueItem> weightedShuffle(@NonNull final Context context,
                                                       @NonNull final List<PlayQueueItem> items,
                                                       @NonNull final PlayQueueItem currentItem) {
        if (items.size() <= 2) {
            // Not enough items to shuffle meaningfully
            return new ArrayList<>(items);
        }

        // Load preferences
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        // ListPreference stores values as strings, not integers
        final int unratedWeight = Integer.parseInt(prefs.getString(
                context.getString(R.string.weighted_shuffle_unrated_weight_key), "10"));
        final int curveExponentInt = Integer.parseInt(prefs.getString(
                context.getString(R.string.weighted_shuffle_curve_exponent_key), "10"));
        final double curveExponent = curveExponentInt / 10.0; // 10-30 -> 1.0-3.0

        // Load ratings from database
        final Map<String, Integer> ratings = loadRatings(context, items);

        // Build weighted list (excluding current item)
        final List<WeightedItem> weighted = new ArrayList<>();
        for (final PlayQueueItem item : items) {
            if (!item.equals(currentItem)) {
                final Integer rating = ratings.get(item.getUrl());
                final double weight = calculateWeight(rating, unratedWeight, curveExponent);
                weighted.add(new WeightedItem(item, weight));
            }
        }

        // Perform weighted random selection
        final List<PlayQueueItem> result = new ArrayList<>();
        result.add(currentItem); // Current item always first
        result.addAll(selectWeighted(weighted));

        return result;
    }

    /**
     * Calculates weight for an item based on its rating and curve exponent.
     *
     * @param rating the rating (1-10), or null if unrated
     * @param unratedWeight the weight to use for unrated items
     * @param curveExponent the exponent for the curve (1.0 = linear, 2.0 = quadratic)
     * @return calculated weight
     */
    private static double calculateWeight(final Integer rating,
                                          final int unratedWeight,
                                          final double curveExponent) {
        if (rating == null) {
            return unratedWeight;
        }
        // Apply curve: weight = rating^exponent
        return Math.pow(rating, curveExponent);
    }

    /**
     * Loads ratings for all items from the database.
     *
     * @param context the context for database access
     * @param items the items to load ratings for
     * @return map of URL to rating
     */
    @NonNull
    private static Map<String, Integer> loadRatings(@NonNull final Context context,
                                                     @NonNull final List<PlayQueueItem> items) {
        final Map<String, Integer> ratings = new HashMap<>();

        try {
            final StreamDAO streamDAO = NewPipeDatabase.getInstance(context).streamDAO();

            for (final PlayQueueItem item : items) {
                final List<StreamEntity> entities =
                        streamDAO.getStream(item.getServiceId(), item.getUrl()).blockingFirst();

                if (!entities.isEmpty()) {
                    final StreamEntity entity = entities.get(0);
                    if (entity.getUserRating() != null) {
                        ratings.put(item.getUrl(), entity.getUserRating());
                    }
                }
            }
        } catch (final Exception e) {
            // Failed to load ratings, continue with empty map (all unrated = max weight)
            android.util.Log.w("WeightedShuffleHelper",
                    "Failed to load ratings: " + e.getMessage());
        }

        return ratings;
    }

    /**
     * Performs weighted random selection on the weighted items.
     * Uses algorithm: select each item with probability proportional to its weight.
     *
     * @param weighted the weighted items to select from
     * @return ordered list of selected items
     */
    @NonNull
    private static List<PlayQueueItem> selectWeighted(
            @NonNull final List<WeightedItem> weighted) {
        final List<PlayQueueItem> result = new ArrayList<>();
        final List<WeightedItem> remaining = new ArrayList<>(weighted);

        while (!remaining.isEmpty()) {
            // Calculate total weight of remaining items
            double totalWeight = 0;
            for (final WeightedItem item : remaining) {
                totalWeight += item.weight;
            }

            // Select random value in [0, totalWeight)
            final double random = RANDOM.nextDouble() * totalWeight;

            // Find which item corresponds to this random value
            double cumulative = 0;
            for (int i = 0; i < remaining.size(); i++) {
                cumulative += remaining.get(i).weight;
                if (random < cumulative) {
                    result.add(remaining.get(i).item);
                    remaining.remove(i);
                    break;
                }
            }
        }

        return result;
    }

    /**
     * Internal class to hold an item and its weight.
     */
    private static class WeightedItem {
        final PlayQueueItem item;
        final double weight;

        WeightedItem(final PlayQueueItem item, final double weight) {
            this.item = item;
            this.weight = weight;
        }
    }
}
