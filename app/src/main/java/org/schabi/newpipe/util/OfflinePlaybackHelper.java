package org.schabi.newpipe.util;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.database.download.dao.OfflineFileMappingDAO;
import org.schabi.newpipe.database.download.model.OfflineFileMappingEntity;

import java.io.File;
import java.util.List;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Helper class for offline playback detection and file management.
 * Checks if streams have been downloaded and provides local file paths.
 */
public final class OfflinePlaybackHelper {
    private static final String TAG = "OfflinePlaybackHelper";

    private OfflinePlaybackHelper() {
        // Utility class
    }

    /**
     * Checks if a stream has an offline file available.
     *
     * @param context   the context
     * @param serviceId the service ID
     * @param streamUrl the stream URL
     * @return Single emitting the offline file URI if available, null otherwise
     */
    public static Single<String> getOfflineFileUri(
            @NonNull final Context context,
            final int serviceId,
            @NonNull final String streamUrl) {

        final OfflineFileMappingDAO dao =
            NewPipeDatabase.getInstance(context).offlineFileMappingDAO();

        return dao.getMapping(serviceId, streamUrl)
            .firstOrError()
            .flatMap(mappings -> {
                if (mappings.isEmpty()) {
                    return io.reactivex.rxjava3.core.Single.just("");
                }

                final OfflineFileMappingEntity mapping = mappings.get(0);

                // Check if file is marked as available
                if (!mapping.isAvailable()) {
                    return io.reactivex.rxjava3.core.Single.just("");
                }

                // Verify file actually exists
                final Uri fileUri = Uri.parse(mapping.getLocalFileUri());
                if (!fileExists(fileUri)) {
                    // File was deleted, mark as unavailable
                    dao.updateAvailability(mapping.getUid(), false);
                    return io.reactivex.rxjava3.core.Single.just("");
                }

                return io.reactivex.rxjava3.core.Single.just(mapping.getLocalFileUri());
            })
            .onErrorReturn(throwable -> "")
            .map(uri -> uri.isEmpty() ? null : uri)
            .subscribeOn(Schedulers.io());
    }

    /**
     * Checks if a stream has been downloaded (synchronous).
     * Use this sparingly - prefer getOfflineFileUri() for actual playback.
     *
     * @param context   the context
     * @param serviceId the service ID
     * @param streamUrl the stream URL
     * @return true if an offline file exists and is available
     */
    public static boolean hasOfflineFile(
            @NonNull final Context context,
            final int serviceId,
            @NonNull final String streamUrl) {

        try {
            final OfflineFileMappingDAO dao =
                NewPipeDatabase.getInstance(context).offlineFileMappingDAO();

            final List<OfflineFileMappingEntity> mappings =
                dao.getMapping(serviceId, streamUrl)
                    .firstOrError()
                    .blockingGet();

            if (mappings.isEmpty()) {
                return false;
            }

            final OfflineFileMappingEntity mapping = mappings.get(0);
            return mapping.isAvailable();

        } catch (final Exception e) {
            Log.e(TAG, "Error checking offline file", e);
            return false;
        }
    }

    /**
     * Gets the offline file URI synchronously.
     * Use this for playback to avoid blocking issues.
     *
     * @param context   the context
     * @param serviceId the service ID
     * @param streamUrl the stream URL
     * @return the file URI if available, null otherwise
     */
    @Nullable
    public static String getOfflineFileUriSync(
            @NonNull final Context context,
            final int serviceId,
            @NonNull final String streamUrl) {

        try {
            final OfflineFileMappingDAO dao =
                NewPipeDatabase.getInstance(context).offlineFileMappingDAO();

            final List<OfflineFileMappingEntity> mappings =
                dao.getMapping(serviceId, streamUrl)
                    .firstOrError()
                    .timeout(1, java.util.concurrent.TimeUnit.SECONDS)
                    .blockingGet();

            if (mappings.isEmpty()) {
                return null;
            }

            final OfflineFileMappingEntity mapping = mappings.get(0);

            // Check if file is marked as available
            if (!mapping.isAvailable()) {
                return null;
            }

            // Verify file actually exists
            final android.net.Uri fileUri = android.net.Uri.parse(mapping.getLocalFileUri());
            if (!fileExists(fileUri)) {
                // File was deleted, mark as unavailable
                dao.updateAvailability(mapping.getUid(), false);
                return null;
            }

            Log.d(TAG, "Found offline file for stream: " + mapping.getLocalFileUri());
            return mapping.getLocalFileUri();

        } catch (final Exception e) {
            Log.e(TAG, "Error getting offline file URI", e);
            return null;
        }
    }

    /**
     * Checks if a file exists at the given URI.
     *
     * @param fileUri the file URI
     * @return true if file exists, false otherwise
     */
    private static boolean fileExists(@Nullable final Uri fileUri) {
        if (fileUri == null) {
            return false;
        }

        try {
            // Handle file:// URIs
            if ("file".equals(fileUri.getScheme())) {
                final String path = fileUri.getPath();
                if (path != null) {
                    final File file = new File(path);
                    return file.exists() && file.length() > 0;
                }
                return false;
            }

            // For content:// URIs (SAF), check via DocumentFile
            if ("content".equals(fileUri.getScheme())) {
                final androidx.documentfile.provider.DocumentFile docFile =
                    androidx.documentfile.provider.DocumentFile.fromSingleUri(
                        org.schabi.newpipe.App.getApp(), fileUri);
                return docFile != null && docFile.exists() && docFile.length() > 0;
            }

            // Unknown scheme
            return false;

        } catch (final Exception e) {
            Log.e(TAG, "Error checking file existence", e);
            return false;
        }
    }
}
