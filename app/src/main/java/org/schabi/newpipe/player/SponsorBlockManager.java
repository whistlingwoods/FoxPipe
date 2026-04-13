package org.schabi.newpipe.player;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.extractor.ServiceList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SponsorBlockManager {
    private static final String TAG = "SponsorBlockManager";
    private static final String SPONSORBLOCK_API_URL = "https://sponsor.ajay.app/api/skipSegments?videoID=";

    private final OkHttpClient httpClient;
    private List<Segment> segments = new ArrayList<>();
    private Set<String> enabledCategories;

    public static class Segment {
        public final double start;
        public final double end;
        public final String category;

        public Segment(double start, double end, String category) {
            this.start = start;
            this.end = end;
            this.category = category;
        }

        public boolean contains(double position) {
            return position >= start && position < end;
        }
    }

    public SponsorBlockManager() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    public void setEnabledCategories(Set<String> categories) {
        this.enabledCategories = categories;
    }

    /**
     * Extract video ID from YouTube URL
     */
    @Nullable
    public static String extractVideoId(@NonNull String url) {
        if (!url.contains("youtube.com") && !url.contains("youtu.be")) {
            return null;
        }

        try {
            // Handle youtu.be short links
            if (url.contains("youtu.be/")) {
                int start = url.indexOf("youtu.be/") + 9;
                int end = url.indexOf('?', start);
                if (end == -1) end = url.length();
                return url.substring(start, end);
            }

            // Handle youtube.com/watch?v=VIDEO_ID
            if (url.contains("youtube.com/watch")) {
                int vIndex = url.indexOf("v=");
                if (vIndex != -1) {
                    int start = vIndex + 2;
                    int end = url.indexOf('&', start);
                    if (end == -1) end = url.length();
                    return url.substring(start, end);
                }
            }

            // Handle youtube.com/embed/VIDEO_ID
            if (url.contains("youtube.com/embed/")) {
                int start = url.indexOf("embed/") + 6;
                int end = url.indexOf('?', start);
                if (end == -1) end = url.length();
                return url.substring(start, end);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to extract video ID from URL: " + url, e);
        }

        return null;
    }

    /**
     * Load sponsor segments for a video asynchronously
     */
    public Single<List<Segment>> loadSegmentsAsync(@NonNull String videoUrl) {
        String videoId = extractVideoId(videoUrl);
        if (videoId == null) {
            Log.d(TAG, "Not a YouTube video, skipping SponsorBlock");
            return Single.<List<Segment>>just(new ArrayList<>());
        }

        return Single.<List<Segment>>fromCallable(() -> {
            try {
                // Build API URL with category filtering
                StringBuilder apiUrl = new StringBuilder(SPONSORBLOCK_API_URL).append(videoId);

                if (enabledCategories != null && !enabledCategories.isEmpty()) {
                    // Map NewPipe category names to SponsorBlock API names and deduplicate
                    Set<String> apiCategoriesSet = new HashSet<>();
                    for (String category : enabledCategories) {
                        List<String> apiCategories = mapToApiCategories(category);
                        if (apiCategories != null) {
                            apiCategoriesSet.addAll(apiCategories);
                        }
                    }

                    if (!apiCategoriesSet.isEmpty()) {
                        List<String> apiCategoriesList = new ArrayList<>(apiCategoriesSet);
                        List<String> quotedCategories = new ArrayList<>();
                        for (String category : apiCategoriesList) {
                            quotedCategories.add("\"" + category + "\"");
                        }
                        apiUrl.append("&categories=[").append(String.join(",", quotedCategories)).append("]");
                    }
                }

                Log.d(TAG, "Fetching SponsorBlock data from: " + apiUrl.toString());

                Request request = new Request.Builder()
                        .url(apiUrl.toString())
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String jsonResponse = response.body().string();
                        return parseSegments(jsonResponse);
                    } else {
                        Log.w(TAG, "Failed to fetch SponsorBlock data: " + response.code());
                        return new ArrayList<>();
                    }
                }
            } catch (IOException e) {
                Log.w(TAG, "Error loading SponsorBlock segments", e);
                return new ArrayList<>();
            }
        }).subscribeOn(Schedulers.io());
    }

    /**
     * Map NewPipe category display names to SponsorBlock API category names
     * Returns a list since some display categories may map to multiple API categories
     */
    private List<String> mapToApiCategories(String displayCategory) {
        List<String> apiCategories = new ArrayList<>();
        switch (displayCategory.toLowerCase()) {
            case "sponsor":
                apiCategories.add("sponsor");
                break;
            case "intro":
                apiCategories.add("intro");
                break;
            case "outro":
                apiCategories.add("outro");
                break;
            case "interaction reminder":
            case "interaction":
                apiCategories.add("interaction");
                break;
            case "self promotion":
            case "self-promotion":
            case "selfpromo":
                apiCategories.add("selfpromo");
                break;
            case "music/off-topic":
            case "music_offtopic":
            case "music: off-topic":
                apiCategories.add("music_offtopic");
                break;
            case "filler/tangent":
            case "filler tangent":
                // Filler/Tangent maps to both music_offtopic and filler API categories
                apiCategories.add("music_offtopic");
                apiCategories.add("filler");
                break;
            case "preview/recap":
            case "preview":
                apiCategories.add("preview");
                break;
            default:
                Log.w(TAG, "Unknown category: " + displayCategory);
                return null;
        }
        return apiCategories;
    }

    /**
     * Map NewPipe category display names to SponsorBlock API category names
     * @deprecated Use mapToApiCategories() instead for multi-category support
     */
    @Deprecated
    private String mapToApiCategory(String displayCategory) {
        List<String> categories = mapToApiCategories(displayCategory);
        return categories != null && !categories.isEmpty() ? categories.get(0) : null;
    }

    /**
     * Load sponsor segments for a video (synchronous - deprecated, use loadSegmentsAsync)
     */
    @Deprecated
    public void loadSegments(@NonNull String videoUrl) {
        loadSegmentsAsync(videoUrl)
                .subscribe(loadedSegments -> {
                    segments = loadedSegments;
                    Log.d(TAG, "Loaded " + segments.size() + " segments asynchronously");
                }, error -> {
                    Log.e(TAG, "Failed to load segments asynchronously", error);
                    segments.clear();
                });
    }

    private List<Segment> parseSegments(String jsonResponse) {
        List<Segment> parsedSegments = new ArrayList<>();
        try {
            Log.d(TAG, "Parsing SponsorBlock response: " + jsonResponse);

            // Use a more robust JSON parsing approach
            // The response format is: [{"category":"sponsor","actionType":"skip","segment":[start,end],...}, ...]

            // Simple approach: find each object and parse its fields manually
            int startIndex = jsonResponse.indexOf('{');
            while (startIndex != -1) {
                int endIndex = findMatchingBrace(jsonResponse, startIndex);
                if (endIndex == -1) break;

                String objectStr = jsonResponse.substring(startIndex + 1, endIndex);
                Segment segment = parseSingleSegment(objectStr);
                if (segment != null) {
                    parsedSegments.add(segment);
                }

                startIndex = jsonResponse.indexOf('{', endIndex + 1);
            }

            Log.d(TAG, "Successfully loaded " + parsedSegments.size() + " SponsorBlock segments");
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse SponsorBlock response: " + e.getMessage(), e);
        }
        return parsedSegments;
    }

    private int findMatchingBrace(String json, int startIndex) {
        int braceCount = 0;
        for (int i = startIndex; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private Segment parseSingleSegment(String objectStr) {
        double start = -1, end = -1;
        String category = null;

        try {
            Log.d(TAG, "Parsing object: " + objectStr);

            // Parse category - look for "category":"value"
            int categoryStart = objectStr.indexOf("category\":\"");
            if (categoryStart != -1) {
                categoryStart += 11; // length of "category\":\""
                int categoryEnd = objectStr.indexOf('"', categoryStart);
                if (categoryEnd != -1) {
                    category = objectStr.substring(categoryStart, categoryEnd);
                    Log.d(TAG, "Parsed category: " + category);
                }
            }

            // Parse segment array - look for "segment":[start,end]
            int segmentStart = objectStr.indexOf("segment\":[");
            if (segmentStart != -1) {
                segmentStart += 10; // length of "segment\":["
                int segmentEnd = objectStr.indexOf(']', segmentStart);
                if (segmentEnd != -1) {
                    String arrayContent = objectStr.substring(segmentStart, segmentEnd);
                    String[] times = arrayContent.split(",");
                    if (times.length == 2) {
                        try {
                            start = Double.parseDouble(times[0].trim());
                            end = Double.parseDouble(times[1].trim());
                            Log.d(TAG, "Parsed segment: " + start + " -> " + end);
                        } catch (NumberFormatException e) {
                            Log.w(TAG, "Failed to parse segment times: " + arrayContent);
                        }
                    }
                }
            }

            if (start >= 0 && end >= 0 && category != null && !category.isEmpty()) {
                Log.d(TAG, "Added segment: " + start + "-" + end + " (" + category + ")");
                return new Segment(start, end, category);
            } else {
                Log.w(TAG, "Invalid segment data: start=" + start + ", end=" + end + ", category=" + category);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse segment object: " + objectStr, e);
        }

        return null;
    }

    /**
     * Check if current position should be skipped
     * @return end time of segment to skip to, or -1 if no skip needed
     */
    public double shouldSkip(double currentPosition) {
        Log.d(TAG, "shouldSkip called: position=" + currentPosition + ", enabledCategories=" + enabledCategories + ", segments=" + segments.size());
        if (enabledCategories == null || enabledCategories.isEmpty()) {
            Log.d(TAG, "shouldSkip: enabledCategories is null or empty");
            return -1;
        }

        for (Segment segment : segments) {
            // Check if this segment's category is enabled by mapping display names to API categories
            boolean categoryEnabled = isCategoryEnabled(segment.category);

            Log.d(TAG, "Checking segment: " + segment.category + " (" + segment.start + " -> " + segment.end + "), contains=" + segment.contains(currentPosition) + ", enabled=" + categoryEnabled);
            if (categoryEnabled && segment.contains(currentPosition)) {
                Log.d(TAG, "Skipping " + segment.category + " segment: " + segment.start + " -> " + segment.end);
                return segment.end;
            }
        }

        return -1;
    }

    /**
     * Check if a segment category (API name) is enabled by checking if any enabled display category maps to it
     */
    private boolean isCategoryEnabled(String segmentCategory) {
        if (enabledCategories == null) return false;

        for (String enabledCategory : enabledCategories) {
            List<String> apiCategories = mapToApiCategories(enabledCategory);
            if (apiCategories != null && apiCategories.contains(segmentCategory)) {
                return true;
            }
        }
        return false;
    }

    public void clearSegments() {
        segments.clear();
    }

    public void setSegments(List<Segment> newSegments) {
        segments.clear();
        segments.addAll(newSegments);
    }

    public List<Segment> getSegments() {
        return new ArrayList<>(segments);
    }
}
