# NewPipeMP Refactoring Roadmap

**Version:** 1.0
**Created:** 2026-03-01
**Target Completion:** Q3 2026 (6 months)
**Current Status:** Planning Phase

---

## Executive Summary

This roadmap outlines a comprehensive 6-month plan to refactor NewPipeMP from its current state to a production-grade, maintainable codebase with zero critical issues, 80%+ test coverage, and modern Android architecture.

**Current State:**
- 88,105 lines of code
- 44 TODO/FIXME markers
- Missing tests for new features
- Some blocking database operations
- 2,792-line Player.java file
- Hybrid MVP/MVVM architecture
- No dependency injection

**Target State:**
- Zero critical issues
- 80%+ test coverage
- Full Kotlin migration
- Modern MVVM + Clean Architecture
- Dependency injection with Hilt
- Modular Player architecture
- Comprehensive documentation
- Performance optimized
- CI/CD pipeline

---

## Roadmap Overview

```
Phase 1: Foundation & Critical Fixes (Weeks 1-3)
    └─> Fix blocking operations, resource leaks, service connection issues

Phase 2: Testing Infrastructure (Weeks 4-6)
    └─> Build comprehensive test suite, achieve 50%+ coverage

Phase 3: Architecture Migration (Weeks 7-12)
    └─> Implement Hilt DI, migrate to MVVM, refactor Player.java

Phase 4: Kotlin Migration (Weeks 13-16)
    └─> Convert remaining Java to Kotlin, leverage coroutines

Phase 5: Performance & Polish (Weeks 17-20)
    └─> Optimize database, improve performance, add monitoring

Phase 6: Documentation & Release (Weeks 21-24)
    └─> Complete documentation, final testing, stable release
```

---

# PHASE 1: Foundation & Critical Fixes
**Duration:** 3 weeks
**Goal:** Resolve all critical and high-priority issues
**Success Criteria:** Zero critical bugs, all resource leaks fixed

## Week 1: Critical Bug Fixes

### Day 1-2: Fix Blocking Database Operations

**File:** `app/src/main/java/org/schabi/newpipe/util/OfflinePlaybackHelper.java`

**Tasks:**
- [ ] Refactor `hasOfflineFile()` to return `Single<Boolean>` instead of blocking
- [ ] Update all callers to handle async response
- [ ] Refactor `getOfflineUri()` to async pattern
- [ ] Add timeout safeguards (5 seconds max)
- [ ] Test on main thread detector

**Implementation:**
```java
// Before (BLOCKING)
public static boolean hasOfflineFile(Context context, int serviceId, String url) {
    return dao.getMapping(serviceId, url).blockingGet();  // BAD
}

// After (ASYNC)
public static Single<Boolean> hasOfflineFile(Context context, int serviceId, String url) {
    return NewPipeDatabase.getInstance(context)
        .offlineFileMappingDAO()
        .getMapping(serviceId, url)
        .firstOrError()
        .map(mappings -> !mappings.isEmpty() && mappings.get(0).isAvailable())
        .onErrorReturnItem(false)
        .timeout(5, TimeUnit.SECONDS)
        .subscribeOn(Schedulers.io());
}
```

**Files to modify:**
- `OfflinePlaybackHelper.java`
- `MediaSourceManager.java` (update callers)
- `VideoDetailFragment.java` (update callers)

**Tests to add:**
- `OfflinePlaybackHelperTest.java` (unit tests)
- Test timeout behavior
- Test error handling

---

### Day 3-4: Fix Service Connection Leaks

**File:** `app/src/main/java/org/schabi/newpipe/download/BulkDownloadInitiator.java`

**Tasks:**
- [ ] Add timeout handler for service binding (5 second timeout)
- [ ] Implement proper `onServiceDisconnected` cleanup
- [ ] Add connection state tracking
- [ ] Handle binding failures gracefully
- [ ] Add user feedback for failures

**Implementation:**
```java
public class BulkDownloadInitiator {
    private static final int SERVICE_BIND_TIMEOUT_MS = 5000;

    private static class ServiceBindingHelper {
        private final Context context;
        private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
        private ServiceConnection connection;
        private boolean isBound = false;

        public void bindService(Intent intent, ServiceConnection conn) {
            this.connection = conn;

            final Runnable timeoutRunnable = () -> {
                if (isBound) return;
                unbindSafely();
                notifyBindingTimeout();
            };

            if (context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                timeoutHandler.postDelayed(timeoutRunnable, SERVICE_BIND_TIMEOUT_MS);
            } else {
                notifyBindingFailed();
            }
        }

        public void unbindSafely() {
            if (connection != null) {
                try {
                    context.unbindService(connection);
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "Service was not bound", e);
                }
                connection = null;
                isBound = false;
            }
        }
    }
}
```

**Tests to add:**
- `BulkDownloadInitiatorTest.java`
- Test timeout scenario
- Test successful binding
- Test binding failure

---

### Day 5: Fix Resource Leaks

**Files:**
- `app/src/main/java/org/schabi/newpipe/util/StreamMetadataRepair.java`
- `app/src/main/java/us/shandian/giga/postprocessing/AudioMetadataTagging.java`

**Tasks:**
- [ ] Improve `MediaMetadataRetriever` cleanup
- [ ] Remove exception swallowing in finally blocks
- [ ] Add proper error logging
- [ ] Implement try-with-resources pattern where possible
- [ ] Add leak detection tests

**Implementation:**
```java
// Before
} finally {
    if (retriever != null) {
        try {
            retriever.release();
        } catch (final Exception ignored) {  // BAD - swallows errors
        }
    }
}

// After
} finally {
    if (retriever != null) {
        try {
            retriever.release();
        } catch (final RuntimeException e) {
            Log.w(TAG, "Error releasing MediaMetadataRetriever", e);
            // Don't swallow - log and potentially re-throw
        }
    }
}
```

**Tests to add:**
- `StreamMetadataRepairTest.java`
- Test resource cleanup
- Test exception handling

---

## Week 2: Code Quality & Technical Debt

### Day 6-7: Extract Hardcoded Values to Constants

**Tasks:**
- [ ] Create `AppConstants.java` for app-wide constants
- [ ] Create `DatabaseConstants.java` for database-related values
- [ ] Create `PlayerConstants.java` for player-related values
- [ ] Extract all magic numbers
- [ ] Document all constants

**Files to create:**
```java
// app/src/main/java/org/schabi/newpipe/util/AppConstants.java
public final class AppConstants {
    private AppConstants() {}

    // Timeouts
    public static final int SERVICE_BIND_TIMEOUT_MS = 5000;
    public static final int DATABASE_QUERY_TIMEOUT_SEC = 5;
    public static final int NETWORK_REQUEST_TIMEOUT_SEC = 30;

    // Buffer sizes
    public static final int DEFAULT_BUFFER_SIZE = 8192;
    public static final int LARGE_BUFFER_SIZE = 65536;

    // File extensions
    public static final String AUDIO_M4A_EXT = ".m4a";
    public static final String AUDIO_MP3_EXT = ".mp3";
    public static final String AUDIO_OGG_EXT = ".ogg";

    // Rating system
    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 10;
    public static final int DEFAULT_RATING = 5;
}

// app/src/main/java/org/schabi/newpipe/util/PlayerConstants.java
public final class PlayerConstants {
    private PlayerConstants() {}

    // Playback states
    public static final int STATE_IDLE = 0;
    public static final int STATE_BUFFERING = 1;
    public static final int STATE_READY = 2;
    public static final int STATE_ENDED = 3;

    // Seek increments (milliseconds)
    public static final long SEEK_INCREMENT_5_SEC = 5000;
    public static final long SEEK_INCREMENT_15_SEC = 15000;
    public static final long SEEK_INCREMENT_30_SEC = 30000;
}
```

**Files to modify:**
- 20+ files with hardcoded values
- Update all references

---

### Day 8-9: Fix Deprecated API Usage

**Tasks:**
- [ ] Migrate `ViewPager` to `ViewPager2` in 3 fragments
- [ ] Update deprecated fragment methods (`setHasOptionsMenu`, `onCreateOptionsMenu`)
- [ ] Migrate to modern storage APIs (Scoped Storage, API 29+)
- [ ] Update deprecated notification APIs
- [ ] Update deprecated preference APIs

**Files to modify:**
- `FragmentStatePagerAdapterMenuWorkaround.java` → DELETE
- `BaseLocalListFragment.java`
- `LocalItemListAdapter.java`
- `ImportExportManager.kt`
- `BackupFileLocator.kt`

**Migration guide:**
```kotlin
// Before: ViewPager
class MyFragment : Fragment() {
    override fun onCreateView(...): View {
        val adapter = FragmentStatePagerAdapter(childFragmentManager)
        viewPager.adapter = adapter
    }
}

// After: ViewPager2
class MyFragment : Fragment() {
    override fun onCreateView(...): View {
        val adapter = FragmentStateAdapter(this)
        viewPager2.adapter = adapter
    }
}
```

---

### Day 10: Create GitHub Issues for TODO Markers

**Tasks:**
- [ ] Audit all 44 TODO/FIXME markers
- [ ] Categorize by priority (Critical/High/Medium/Low)
- [ ] Create GitHub issues with proper labels
- [ ] Link to affected files and line numbers
- [ ] Assign to milestones
- [ ] Create project board for tracking

**Issue Template:**
```markdown
## TODO: [Short Description]

**Priority:** High
**Category:** Code Quality
**File:** `Player.java:1234`

**Current Code:**
```java
// TODO: Refactor notification handling
```

**Description:**
[Detailed explanation of what needs to be done]

**Acceptance Criteria:**
- [ ] Specific task 1
- [ ] Specific task 2

**Related Issues:** #123, #456
```

**Issue Categories:**
- Critical (10 issues)
- High Priority (15 issues)
- Medium Priority (12 issues)
- Low Priority (7 issues)

---

## Week 3: Error Handling & Logging

### Day 11-12: Standardize Error Handling

**Tasks:**
- [ ] Create `ErrorHandler.java` utility class
- [ ] Define error handling strategy
- [ ] Replace all `catch (Exception e)` with specific exceptions
- [ ] Remove all `catch (Exception ignored)`
- [ ] Add proper user feedback for errors
- [ ] Implement retry mechanisms

**Implementation:**
```java
// app/src/main/java/org/schabi/newpipe/error/ErrorHandler.java
public final class ErrorHandler {

    public static void handleDatabaseError(Context context, Throwable error, String operation) {
        Log.e(TAG, "Database error during: " + operation, error);

        if (error instanceof SQLiteException) {
            // Database corruption
            showErrorDialog(context, R.string.error_database_corrupted);
        } else if (error instanceof TimeoutException) {
            // Query timeout
            showErrorSnackbar(context, R.string.error_database_timeout);
        } else {
            // Generic database error
            ErrorUtil.showUiErrorSnackbar(context, operation, error);
        }
    }

    public static void handleNetworkError(Context context, Throwable error, String operation) {
        Log.e(TAG, "Network error during: " + operation, error);

        if (!NetworkUtils.isConnected(context)) {
            showErrorSnackbar(context, R.string.error_no_internet);
        } else if (error instanceof SocketTimeoutException) {
            showErrorSnackbar(context, R.string.error_network_timeout);
        } else {
            ErrorUtil.showUiErrorSnackbar(context, operation, error);
        }
    }

    public static <T> Consumer<Throwable> createErrorConsumer(
            Context context,
            String operation) {
        return error -> {
            if (error instanceof IOException) {
                handleNetworkError(context, error, operation);
            } else if (error instanceof SQLException) {
                handleDatabaseError(context, error, operation);
            } else {
                ErrorUtil.showUiErrorSnackbar(context, operation, error);
            }
        };
    }
}
```

**Usage:**
```java
// Before
dao.getStream(id)
    .subscribe(
        stream -> handleStream(stream),
        error -> Log.e(TAG, "Error", error)  // Inconsistent
    );

// After
dao.getStream(id)
    .subscribe(
        stream -> handleStream(stream),
        ErrorHandler.createErrorConsumer(context, "Loading stream")
    );
```

---

### Day 13-15: Add Comprehensive Logging

**Tasks:**
- [ ] Create `Logger.java` wrapper for Android Log
- [ ] Add log levels configuration
- [ ] Implement log file rotation for debug builds
- [ ] Add performance timing logs
- [ ] Add user action tracking (privacy-safe)
- [ ] Create log analysis tools

**Implementation:**
```java
// app/src/main/java/org/schabi/newpipe/util/Logger.java
public final class Logger {
    private static final boolean DEBUG = BuildConfig.DEBUG;
    private static final boolean VERBOSE = false;

    public static void d(String tag, String message) {
        if (DEBUG) {
            Log.d(tag, message);
        }
    }

    public static void i(String tag, String message) {
        Log.i(tag, message);
    }

    public static void w(String tag, String message, Throwable error) {
        Log.w(tag, message, error);
        // In production, send to crash reporter
        if (!DEBUG) {
            // ACRA.getErrorReporter().handleException(error);
        }
    }

    public static void e(String tag, String message, Throwable error) {
        Log.e(tag, message, error);
        // Always report errors
        // ACRA.getErrorReporter().handleException(error);
    }

    public static class PerformanceLogger {
        private final String operation;
        private final long startTime;

        public PerformanceLogger(String operation) {
            this.operation = operation;
            this.startTime = System.currentTimeMillis();
        }

        public void log() {
            long duration = System.currentTimeMillis() - startTime;
            if (DEBUG) {
                Log.d("Performance", operation + " took " + duration + "ms");
            }
        }
    }
}
```

---

# PHASE 2: Testing Infrastructure
**Duration:** 3 weeks
**Goal:** Achieve 50%+ test coverage
**Success Criteria:** All new features have tests, critical paths covered

## Week 4: Unit Testing Setup

### Day 16-17: Testing Framework Enhancement

**Tasks:**
- [ ] Update test dependencies (JUnit 5, MockK)
- [ ] Create test utilities and helpers
- [ ] Set up test data builders
- [ ] Configure Jacoco for coverage reporting
- [ ] Set up CI/CD pipeline (GitHub Actions)

**Dependencies to add:**
```kotlin
// build.gradle.kts
dependencies {
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("app.cash.turbine:turbine:1.1.0") // For Flow testing
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("io.mockk:mockk-android:1.13.10")

    // Room testing
    testImplementation("androidx.room:room-testing:2.7.2")

    // Hilt testing (for later phases)
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.51")
}
```

**Test utilities:**
```kotlin
// app/src/test/java/org/schabi/newpipe/TestUtils.kt
object TestUtils {
    fun createTestStreamEntity(
        serviceId: Int = 0,
        url: String = "https://example.com/test",
        title: String = "Test Stream",
        rating: Int? = null
    ): StreamEntity {
        return StreamEntity(
            serviceId = serviceId,
            url = url,
            title = title,
            streamType = StreamType.AUDIO_STREAM,
            duration = 180,
            uploader = "Test Uploader",
            uploaderUrl = "https://example.com/uploader",
            thumbnailUrl = "https://example.com/thumb.jpg",
            viewCount = 1000,
            textualUploadDate = "2026-01-01",
            uploadDate = null,
            description = "Test description",
            userRating = rating
        )
    }

    fun createTestOfflineMapping(
        serviceId: Int = 0,
        streamUrl: String = "https://example.com/test",
        localUri: String = "file:///storage/test.mp3"
    ): OfflineFileMappingEntity {
        return OfflineFileMappingEntity(
            serviceId = serviceId,
            streamUrl = streamUrl,
            localFileUri = localUri,
            isAvailable = true
        )
    }
}
```

---

### Day 18-20: Core Utility Tests

**Tasks:**
- [ ] `OfflinePlaybackHelperTest.kt` (100% coverage)
- [ ] `RatingHelperTest.kt` (100% coverage)
- [ ] `WeightedShuffleHelperTest.kt` (100% coverage)
- [ ] `StreamMetadataRepairTest.kt` (80% coverage)

**Example Test:**
```kotlin
// app/src/test/java/org/schabi/newpipe/util/WeightedShuffleHelperTest.kt
@ExtendWith(MockKExtension::class)
class WeightedShuffleHelperTest {

    @Test
    fun `shuffleByRating returns items in weighted random order`() {
        // Given: Stream list with different ratings
        val streams = listOf(
            createTestStreamEntity(url = "stream1", rating = 10),
            createTestStreamEntity(url = "stream2", rating = 5),
            createTestStreamEntity(url = "stream3", rating = 1),
            createTestStreamEntity(url = "stream4", rating = null)
        )

        // When: Shuffle 1000 times and track positions
        val positionCounts = mutableMapOf<String, MutableList<Int>>()
        repeat(1000) {
            val shuffled = WeightedShuffleHelper.shuffleByRating(streams)
            shuffled.forEachIndexed { index, stream ->
                positionCounts.getOrPut(stream.url) { mutableListOf() }.add(index)
            }
        }

        // Then: Highly rated items should appear earlier on average
        val avgPosition1 = positionCounts["stream1"]!!.average()
        val avgPosition3 = positionCounts["stream3"]!!.average()

        assertThat(avgPosition1).isLessThan(avgPosition3)
    }

    @Test
    fun `shuffleByRating handles empty list`() {
        val result = WeightedShuffleHelper.shuffleByRating(emptyList())
        assertThat(result).isEmpty()
    }

    @Test
    fun `shuffleByRating handles all unrated items`() {
        val streams = listOf(
            createTestStreamEntity(url = "stream1", rating = null),
            createTestStreamEntity(url = "stream2", rating = null)
        )

        val result = WeightedShuffleHelper.shuffleByRating(streams)

        assertThat(result).hasSize(2)
        assertThat(result).containsExactlyInAnyOrderElementsOf(streams)
    }
}
```

---

## Week 5: Database & Repository Tests

### Day 21-23: DAO Tests

**Tasks:**
- [ ] Enhance `StreamDAOTest.kt` with rating queries
- [ ] Create `OfflineFileMappingDAOTest.kt`
- [ ] Create `PlaybackStatisticsDAOTest.kt`
- [ ] Test all CRUD operations
- [ ] Test complex queries
- [ ] Test migrations

**Example Test:**
```kotlin
// app/src/androidTest/java/org/schabi/newpipe/database/OfflineFileMappingDAOTest.kt
@RunWith(AndroidJUnit4::class)
class OfflineFileMappingDAOTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: OfflineFileMappingDAO

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.offlineFileMappingDAO()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveMapping() = runTest {
        // Given
        val mapping = createTestOfflineMapping()

        // When
        dao.insert(mapping)

        // Then
        val retrieved = dao.getMapping(mapping.serviceId, mapping.streamUrl)
            .firstOrError()
            .blockingGet()

        assertThat(retrieved).hasSize(1)
        assertThat(retrieved[0]).isEqualTo(mapping)
    }

    @Test
    fun getMappingReturnsEmptyForNonExistent() = runTest {
        val result = dao.getMapping(0, "nonexistent")
            .firstOrError()
            .blockingGet()

        assertThat(result).isEmpty()
    }

    @Test
    fun updateAvailabilityStatus() = runTest {
        // Given
        val mapping = createTestOfflineMapping()
        dao.insert(mapping)

        // When
        dao.updateAvailability(mapping.serviceId, mapping.streamUrl, false)

        // Then
        val updated = dao.getMapping(mapping.serviceId, mapping.streamUrl)
            .firstOrError()
            .blockingGet()[0]

        assertThat(updated.isAvailable).isFalse()
    }
}
```

---

### Day 24-25: Integration Tests

**Tasks:**
- [ ] `BulkDownloadIntegrationTest.kt`
- [ ] `OfflinePlaybackIntegrationTest.kt`
- [ ] `MetadataRepairIntegrationTest.kt`
- [ ] Test end-to-end flows
- [ ] Test error scenarios

---

## Week 6: UI & Fragment Tests

### Day 26-28: Fragment Tests

**Tasks:**
- [ ] `VideoDetailFragmentTest.kt`
- [ ] `RatingStatisticsFragmentTest.kt`
- [ ] `BulkDownloadDialogTest.kt`
- [ ] Use Espresso for UI interactions
- [ ] Test user flows

**Example Test:**
```kotlin
// app/src/androidTest/java/org/schabi/newpipe/fragments/VideoDetailFragmentTest.kt
@RunWith(AndroidJUnit4::class)
class VideoDetailFragmentTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun ratingDialogAppearsOnRatingButtonClick() {
        // Given: Video detail is shown
        // When: Click rating button
        onView(withId(R.id.rating_button)).perform(click())

        // Then: Rating dialog appears
        onView(withText(R.string.rate_this_stream))
            .check(matches(isDisplayed()))
    }

    @Test
    fun offlineIndicatorShowsForDownloadedContent() {
        // Given: Stream has offline mapping
        // When: Fragment loads
        // Then: Offline indicator is visible
        onView(withId(R.id.offline_indicator))
            .check(matches(isDisplayed()))
    }
}
```

### Day 29-30: Coverage Analysis & Gaps

**Tasks:**
- [ ] Generate Jacoco coverage report
- [ ] Identify untested critical paths
- [ ] Prioritize remaining test work
- [ ] Document coverage metrics
- [ ] Set coverage goals for Phase 3

**Target Coverage:**
- Overall: 50%+
- New features: 80%+
- Critical utilities: 100%
- DAOs: 90%+

---

# PHASE 3: Architecture Migration
**Duration:** 6 weeks
**Goal:** Modern MVVM + Clean Architecture with Hilt DI
**Success Criteria:** Scalable, testable, maintainable architecture

## Week 7-8: Dependency Injection with Hilt

### Day 31-35: Hilt Setup

**Tasks:**
- [ ] Add Hilt dependencies
- [ ] Create `@HiltAndroidApp` application class
- [ ] Set up Hilt modules
- [ ] Configure component hierarchy
- [ ] Migrate singletons to Hilt

**Dependencies:**
```kotlin
// build.gradle.kts (project level)
plugins {
    id("com.google.dagger.hilt.android") version "2.51" apply false
}

// build.gradle.kts (app level)
plugins {
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

dependencies {
    implementation("com.google.dagger:hilt-android:2.51")
    ksp("com.google.dagger:hilt-compiler:2.51")

    // Hilt ViewModel integration
    implementation("androidx.hilt:hilt-navigation-fragment:1.2.0")

    // Testing
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.51")
    kspAndroidTest("com.google.dagger:hilt-compiler:2.51")
}
```

**Application class:**
```kotlin
// app/src/main/java/org/schabi/newpipe/App.kt
@HiltAndroidApp
class App : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize libraries
        initializeNewPipeExtractor()
        initializeErrorHandler()
        initializeImageLoader()
    }
}
```

**Hilt modules:**
```kotlin
// app/src/main/java/org/schabi/newpipe/di/DatabaseModule.kt
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return NewPipeDatabase.getInstance(context)
    }

    @Provides
    fun provideStreamDAO(database: AppDatabase): StreamDAO {
        return database.streamDAO()
    }

    @Provides
    fun provideOfflineFileMappingDAO(database: AppDatabase): OfflineFileMappingDAO {
        return database.offlineFileMappingDAO()
    }

    @Provides
    fun providePlaybackStatisticsDAO(database: AppDatabase): PlaybackStatisticsDAO {
        return database.playbackStatisticsDAO()
    }
}

// app/src/main/java/org/schabi/newpipe/di/RepositoryModule.kt
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideHistoryRecordManager(
        @ApplicationContext context: Context,
        streamDAO: StreamDAO,
        streamHistoryDAO: StreamHistoryDAO,
        playbackStatisticsDAO: PlaybackStatisticsDAO
    ): HistoryRecordManager {
        return HistoryRecordManager(context, streamDAO, streamHistoryDAO, playbackStatisticsDAO)
    }

    @Provides
    @Singleton
    fun provideOfflinePlaybackRepository(
        offlineFileMappingDAO: OfflineFileMappingDAO
    ): OfflinePlaybackRepository {
        return OfflinePlaybackRepositoryImpl(offlineFileMappingDAO)
    }
}

// app/src/main/java/org/schabi/newpipe/di/NetworkModule.kt
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
```

---

### Day 36-42: Repository Layer

**Tasks:**
- [ ] Create repository interfaces
- [ ] Implement repositories
- [ ] Migrate managers to repositories
- [ ] Add caching strategies
- [ ] Implement offline-first patterns

**Repository structure:**
```kotlin
// app/src/main/java/org/schabi/newpipe/data/repository/StreamRepository.kt
interface StreamRepository {
    fun getStream(serviceId: Int, url: String): Flow<StreamEntity?>
    fun getRatedStreams(): Flow<List<StreamEntity>>
    fun updateRating(serviceId: Int, url: String, rating: Int): Completable
    fun getStreamWithStatistics(serviceId: Int, url: String): Flow<StreamWithStatistics>
}

@Singleton
class StreamRepositoryImpl @Inject constructor(
    private val streamDAO: StreamDAO,
    private val playbackStatisticsDAO: PlaybackStatisticsDAO,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : StreamRepository {

    override fun getStream(serviceId: Int, url: String): Flow<StreamEntity?> {
        return streamDAO.getStream(serviceId, url)
            .subscribeOn(Schedulers.io())
            .toFlowable()
            .toFlow(ioDispatcher)
    }

    override fun getRatedStreams(): Flow<List<StreamEntity>> {
        return streamDAO.getAllRated()
            .subscribeOn(Schedulers.io())
            .toFlowable()
            .toFlow(ioDispatcher)
    }

    override fun updateRating(serviceId: Int, url: String, rating: Int): Completable {
        return streamDAO.updateRating(serviceId, url, rating)
            .subscribeOn(Schedulers.io())
    }

    override fun getStreamWithStatistics(
        serviceId: Int,
        url: String
    ): Flow<StreamWithStatistics> {
        return combine(
            getStream(serviceId, url),
            playbackStatisticsDAO.getStatistics(serviceId, url).asFlow()
        ) { stream, stats ->
            stream?.let { StreamWithStatistics(it, stats) }
        }.filterNotNull()
    }
}

// Data class for combined results
data class StreamWithStatistics(
    val stream: StreamEntity,
    val statistics: PlaybackStatisticsEntity?
)
```

**Offline-first repository:**
```kotlin
// app/src/main/java/org/schabi/newpipe/data/repository/OfflinePlaybackRepository.kt
interface OfflinePlaybackRepository {
    fun isAvailableOffline(serviceId: Int, url: String): Flow<Boolean>
    fun getOfflineUri(serviceId: Int, url: String): Flow<String?>
    fun createMapping(mapping: OfflineFileMappingEntity): Completable
    fun updateAvailability(serviceId: Int, url: String, available: Boolean): Completable
}

@Singleton
class OfflinePlaybackRepositoryImpl @Inject constructor(
    private val dao: OfflineFileMappingDAO,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : OfflinePlaybackRepository {

    // In-memory cache for frequent checks
    private val availabilityCache = LruCache<String, Boolean>(100)

    override fun isAvailableOffline(serviceId: Int, url: String): Flow<Boolean> = flow {
        val cacheKey = "$serviceId:$url"

        // Check cache first
        availabilityCache.get(cacheKey)?.let {
            emit(it)
            return@flow
        }

        // Query database
        val result = dao.getMapping(serviceId, url)
            .firstOrError()
            .map { mappings -> mappings.isNotEmpty() && mappings[0].isAvailable }
            .onErrorReturnItem(false)
            .blockingGet()

        // Update cache
        availabilityCache.put(cacheKey, result)
        emit(result)
    }.flowOn(ioDispatcher)

    override fun getOfflineUri(serviceId: Int, url: String): Flow<String?> = flow {
        val mappings = dao.getMapping(serviceId, url)
            .firstOrError()
            .blockingGet()

        if (mappings.isNotEmpty() && mappings[0].isAvailable) {
            emit(mappings[0].localFileUri)
        } else {
            emit(null)
        }
    }.flowOn(ioDispatcher)

    override fun createMapping(mapping: OfflineFileMappingEntity): Completable {
        return dao.insert(mapping)
            .doOnComplete {
                // Invalidate cache
                availabilityCache.remove("${mapping.serviceId}:${mapping.streamUrl}")
            }
            .subscribeOn(Schedulers.io())
    }

    override fun updateAvailability(
        serviceId: Int,
        url: String,
        available: Boolean
    ): Completable {
        return dao.updateAvailability(serviceId, url, available)
            .doOnComplete {
                // Update cache
                availabilityCache.put("$serviceId:$url", available)
            }
            .subscribeOn(Schedulers.io())
    }
}
```

---

## Week 9-10: Player Refactoring

### Day 43-50: Split Player.java into Modules

**Goal:** Reduce Player.java from 2,792 lines to <500 lines per module

**New structure:**
```
player/
├── Player.java (400 lines - coordinator)
├── core/
│   ├── PlayerCore.kt (600 lines - playback engine)
│   ├── PlaybackController.kt (300 lines - playback control)
│   └── PlayerState.kt (100 lines - state management)
├── ui/
│   ├── PlayerUI.kt (500 lines - UI management)
│   ├── PlayerControls.kt (300 lines - control buttons)
│   └── PlayerNotificationManager.kt (400 lines - notifications)
├── gesture/
│   ├── PlayerGestureListener.kt (200 lines - gesture detection)
│   └── GestureController.kt (200 lines - gesture handling)
└── queue/
    ├── QueueManager.kt (300 lines - queue management)
    └── QueueUI.kt (200 lines - queue UI)
```

**Tasks:**
- [ ] Day 43-44: Extract `PlayerCore` (playback engine)
- [ ] Day 45-46: Extract `PlayerUI` (UI management)
- [ ] Day 47: Extract `PlayerGestureListener`
- [ ] Day 48: Extract `PlayerNotificationManager`
- [ ] Day 49: Refactor main `Player.java` as coordinator
- [ ] Day 50: Testing and integration

**PlayerCore.kt:**
```kotlin
// app/src/main/java/org/schabi/newpipe/player/core/PlayerCore.kt
@Singleton
class PlayerCore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exoPlayerProvider: ExoPlayerProvider
) {
    private var exoPlayer: ExoPlayer? = null
    private val listeners = mutableListOf<PlaybackListener>()

    interface PlaybackListener {
        fun onPlaybackStateChanged(state: Int)
        fun onPlaybackPositionChanged(position: Long)
        fun onPlaybackError(error: PlaybackException)
    }

    fun initialize() {
        if (exoPlayer != null) return

        exoPlayer = exoPlayerProvider.create().apply {
            addListener(playerEventListener)
        }
    }

    fun play(streamUrl: String) {
        val mediaItem = MediaItem.fromUri(streamUrl)
        exoPlayer?.apply {
            setMediaItem(mediaItem)
            prepare()
            play()
        }
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun resume() {
        exoPlayer?.play()
    }

    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }

    private val playerEventListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            listeners.forEach { it.onPlaybackStateChanged(playbackState) }
        }

        override fun onPlayerError(error: PlaybackException) {
            listeners.forEach { it.onPlaybackError(error) }
        }
    }
}
```

**PlayerUI.kt:**
```kotlin
// app/src/main/java/org/schabi/newpipe/player/ui/PlayerUI.kt
class PlayerUI @Inject constructor(
    private val playerCore: PlayerCore,
    private val playerControls: PlayerControls
) : PlayerCore.PlaybackListener {

    private var binding: PlayerBinding? = null

    fun bind(binding: PlayerBinding) {
        this.binding = binding
        setupUI()
        playerCore.addListener(this)
    }

    fun unbind() {
        playerCore.removeListener(this)
        binding = null
    }

    private fun setupUI() {
        binding?.apply {
            playPauseButton.setOnClickListener {
                if (playerCore.isPlaying) {
                    playerCore.pause()
                } else {
                    playerCore.resume()
                }
            }

            seekBar.setOnSeekBarChangeListener(seekBarListener)
        }
    }

    override fun onPlaybackStateChanged(state: Int) {
        binding?.apply {
            when (state) {
                Player.STATE_BUFFERING -> {
                    progressBar.visibility = View.VISIBLE
                }
                Player.STATE_READY -> {
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    fun updateThumbnail(url: String) {
        binding?.thumbnail?.let { imageView ->
            Picasso.get()
                .load(url)
                .into(imageView)
        }
    }
}
```

---

## Week 11-12: MVVM ViewModels

### Day 51-56: Create ViewModels

**Tasks:**
- [ ] Create ViewModels for all major fragments
- [ ] Migrate business logic from fragments to ViewModels
- [ ] Implement proper state management
- [ ] Add SavedStateHandle support
- [ ] Test ViewModels

**ViewModel examples:**
```kotlin
// app/src/main/java/org/schabi/newpipe/fragments/detail/VideoDetailViewModel.kt
@HiltViewModel
class VideoDetailViewModel @Inject constructor(
    private val streamRepository: StreamRepository,
    private val offlinePlaybackRepository: OfflinePlaybackRepository,
    private val historyRecordManager: HistoryRecordManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val serviceId: Int = savedStateHandle["serviceId"] ?: 0
    private val url: String = savedStateHandle["url"] ?: ""

    private val _uiState = MutableStateFlow<VideoDetailState>(VideoDetailState.Loading)
    val uiState: StateFlow<VideoDetailState> = _uiState.asStateFlow()

    init {
        loadStream()
    }

    private fun loadStream() {
        viewModelScope.launch {
            streamRepository.getStreamWithStatistics(serviceId, url)
                .catch { error ->
                    _uiState.value = VideoDetailState.Error(error)
                }
                .collect { streamWithStats ->
                    _uiState.value = VideoDetailState.Success(streamWithStats)
                }
        }
    }

    fun updateRating(rating: Int) {
        viewModelScope.launch {
            streamRepository.updateRating(serviceId, url, rating)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    { loadStream() },
                    { error -> _uiState.value = VideoDetailState.Error(error) }
                )
        }
    }

    fun checkOfflineAvailability(): Flow<Boolean> {
        return offlinePlaybackRepository.isAvailableOffline(serviceId, url)
    }
}

sealed class VideoDetailState {
    object Loading : VideoDetailState()
    data class Success(val data: StreamWithStatistics) : VideoDetailState()
    data class Error(val error: Throwable) : VideoDetailState()
}
```

**Rating Statistics ViewModel:**
```kotlin
// app/src/main/java/org/schabi/newpipe/local/statistics/RatingStatisticsViewModel.kt
@HiltViewModel
class RatingStatisticsViewModel @Inject constructor(
    private val streamRepository: StreamRepository,
    private val playbackStatisticsDAO: PlaybackStatisticsDAO
) : ViewModel() {

    private val _statistics = MutableStateFlow<RatingStatistics?>(null)
    val statistics: StateFlow<RatingStatistics?> = _statistics.asStateFlow()

    init {
        loadStatistics()
    }

    private fun loadStatistics() {
        viewModelScope.launch {
            combine(
                streamRepository.getRatedStreams(),
                playbackStatisticsDAO.getAllStatistics().asFlow()
            ) { streams, stats ->
                calculateStatistics(streams, stats)
            }.collect { stats ->
                _statistics.value = stats
            }
        }
    }

    private fun calculateStatistics(
        streams: List<StreamEntity>,
        stats: List<PlaybackStatisticsEntity>
    ): RatingStatistics {
        val ratingDistribution = streams
            .groupBy { it.userRating ?: 0 }
            .mapValues { it.value.size }

        val avgRating = streams
            .mapNotNull { it.userRating }
            .average()

        val mostPlayed = streams
            .map { stream ->
                val stat = stats.find { it.streamUrl == stream.url }
                stream to (stat?.playCount ?: 0)
            }
            .sortedByDescending { it.second }
            .take(10)

        return RatingStatistics(
            ratingDistribution = ratingDistribution,
            averageRating = avgRating,
            totalRated = streams.size,
            mostPlayedRated = mostPlayed
        )
    }
}

data class RatingStatistics(
    val ratingDistribution: Map<Int, Int>,
    val averageRating: Double,
    val totalRated: Int,
    val mostPlayedRated: List<Pair<StreamEntity, Int>>
)
```

---

### Day 57-60: Fragment Refactoring

**Tasks:**
- [ ] Update fragments to use ViewModels
- [ ] Remove business logic from fragments
- [ ] Implement proper lifecycle management
- [ ] Add proper state collection
- [ ] Test fragment-ViewModel integration

**Example refactored fragment:**
```kotlin
// app/src/main/java/org/schabi/newpipe/fragments/detail/VideoDetailFragment.kt
@AndroidEntryPoint
class VideoDetailFragment : BaseFragment() {

    private val viewModel: VideoDetailViewModel by viewModels()
    private var _binding: FragmentVideoDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        observeState()
    }

    private fun setupUI() {
        binding.ratingButton.setOnClickListener {
            showRatingDialog()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is VideoDetailState.Loading -> showLoading()
                        is VideoDetailState.Success -> showStream(state.data)
                        is VideoDetailState.Error -> showError(state.error)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.checkOfflineAvailability().collect { isOffline ->
                binding.offlineIndicator.isVisible = isOffline
            }
        }
    }

    private fun showStream(data: StreamWithStatistics) {
        binding.apply {
            title.text = data.stream.title
            uploader.text = data.stream.uploader

            // Show rating
            data.stream.userRating?.let { rating ->
                ratingBar.rating = rating.toFloat()
            }

            // Show statistics
            data.statistics?.let { stats ->
                playCount.text = stats.playCount.toString()
            }
        }
    }

    private fun showRatingDialog() {
        RatingDialog.show(requireContext()) { rating ->
            viewModel.updateRating(rating)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

---

# PHASE 4: Kotlin Migration
**Duration:** 4 weeks
**Goal:** Convert all Java to Kotlin, leverage coroutines
**Success Criteria:** 100% Kotlin codebase, no RxJava in new code

## Week 13-14: Java to Kotlin Conversion

### Day 61-70: Automated Conversion + Manual Refinement

**Priority order:**
1. Week 13: Utilities and helpers (30 files)
2. Week 14: Fragments and adapters (40 files)

**Tasks:**
- [ ] Use Android Studio's Java to Kotlin converter
- [ ] Manually refine converted code
- [ ] Replace nullable types with proper null safety
- [ ] Use Kotlin idioms (data classes, extension functions, etc.)
- [ ] Replace loops with Kotlin collection operations
- [ ] Add proper coroutine support

**Files to convert (Week 13):**
- `RatingHelper.java` → `RatingHelper.kt`
- `WeightedShuffleHelper.java` → `WeightedShuffleHelper.kt`
- `OfflinePlaybackHelper.java` → `OfflinePlaybackHelper.kt`
- `StreamMetadataRepair.java` → `StreamMetadataRepair.kt`
- `BulkDownloadInitiator.java` → `BulkDownloadInitiator.kt`
- `BulkDownloadDialog.java` → `BulkDownloadDialog.kt`
- `NavigationHelper.java` → `NavigationHelper.kt`
- `ExtractorHelper.java` → `ExtractorHelper.kt`
- Plus 22 more utility files

**Example conversion:**
```kotlin
// Before (Java): RatingHelper.java
public final class RatingHelper {
    private RatingHelper() {}

    public static void showRatingDialog(
            @NonNull final Context context,
            final int serviceId,
            @NonNull final String streamUrl,
            @Nullable final Integer currentRating,
            @NonNull final RatingCallback callback) {

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        // ... dialog setup
    }

    public interface RatingCallback {
        void onRatingSelected(int rating);
    }
}

// After (Kotlin): RatingHelper.kt
object RatingHelper {

    fun showRatingDialog(
        context: Context,
        serviceId: Int,
        streamUrl: String,
        currentRating: Int? = null,
        callback: (Int) -> Unit
    ) {
        MaterialAlertDialogBuilder(context).apply {
            setTitle(R.string.rate_this_stream)

            val ratingBar = RatingBar(context).apply {
                numStars = 10
                rating = currentRating?.toFloat() ?: 0f
            }

            setView(ratingBar)
            setPositiveButton(R.string.ok) { _, _ ->
                callback(ratingBar.rating.toInt())
            }
            setNegativeButton(R.string.cancel, null)
        }.show()
    }
}
```

**Kotlin improvements:**
```kotlin
// Use extension functions
fun StreamEntity.hasRating(): Boolean = userRating != null

fun StreamEntity.getRatingOrDefault(default: Int = 5): Int =
    userRating ?: default

// Use data classes
data class StreamWithOfflineStatus(
    val stream: StreamEntity,
    val isOffline: Boolean,
    val localUri: String?
)

// Use sealed classes for state
sealed class LoadingState<out T> {
    object Loading : LoadingState<Nothing>()
    data class Success<T>(val data: T) : LoadingState<T>()
    data class Error(val error: Throwable) : LoadingState<Nothing>()
}

// Use scope functions
fun updateStreamRating(stream: StreamEntity, rating: Int) =
    stream.apply {
        userRating = rating
    }
```

---

## Week 15-16: Coroutines Migration

### Day 71-80: Replace RxJava with Coroutines in New Code

**Tasks:**
- [ ] Add coroutines dependencies
- [ ] Create coroutine dispatchers module
- [ ] Migrate repositories to use Flow
- [ ] Migrate ViewModels to use coroutines
- [ ] Keep RxJava for Room compatibility (legacy)
- [ ] Add proper exception handling

**Dependencies:**
```kotlin
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
}
```

**Dispatchers module:**
```kotlin
// app/src/main/java/org/schabi/newpipe/di/DispatchersModule.kt
@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {

    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @Provides
    @Singleton
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
```

**Coroutine-based repository:**
```kotlin
// app/src/main/java/org/schabi/newpipe/data/repository/StreamRepositoryCoroutines.kt
@Singleton
class StreamRepositoryCoroutines @Inject constructor(
    private val streamDAO: StreamDAO,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun getStream(serviceId: Int, url: String): StreamEntity? =
        withContext(ioDispatcher) {
            streamDAO.getStream(serviceId, url)
                .firstOrError()
                .blockingGet()
                .firstOrNull()
        }

    fun getRatedStreamsFlow(): Flow<List<StreamEntity>> = flow {
        streamDAO.getAllRated()
            .subscribeOn(Schedulers.io())
            .toFlowable()
            .collect { streams ->
                emit(streams)
            }
    }.flowOn(ioDispatcher)

    suspend fun updateRating(serviceId: Int, url: String, rating: Int) =
        withContext(ioDispatcher) {
            streamDAO.updateRating(serviceId, url, rating)
                .blockingAwait()
        }
}
```

**Use cases with coroutines:**
```kotlin
// app/src/main/java/org/schabi/newpipe/domain/usecase/GetRatedStreamsUseCase.kt
@Singleton
class GetRatedStreamsUseCase @Inject constructor(
    private val streamRepository: StreamRepositoryCoroutines,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    operator fun invoke(): Flow<Result<List<StreamEntity>>> = flow {
        emit(Result.Loading)
        try {
            streamRepository.getRatedStreamsFlow()
                .collect { streams ->
                    emit(Result.Success(streams))
                }
        } catch (e: Exception) {
            emit(Result.Error(e))
        }
    }.flowOn(ioDispatcher)
}

sealed class Result<out T> {
    object Loading : Result<Nothing>()
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val error: Throwable) : Result<Nothing>()
}
```

---

# PHASE 5: Performance & Polish
**Duration:** 4 weeks
**Goal:** Optimize performance, fix remaining issues
**Success Criteria:** Smooth 60fps, fast app startup, optimized database

## Week 17-18: Database Optimization

### Day 81-85: Add Missing Indices

**Tasks:**
- [ ] Analyze query performance with Room profiler
- [ ] Add missing database indices
- [ ] Optimize complex queries
- [ ] Add database query logging (debug builds)
- [ ] Benchmark before/after performance

**Database migration for indices:**
```kotlin
// app/src/main/java/org/schabi/newpipe/database/Migrations.kt
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add index for playback statistics sorting
        database.execSQL("""
            CREATE INDEX idx_playback_stats_last_updated
            ON playback_statistics(last_updated DESC)
        """)

        // Add index for offline availability queries
        database.execSQL("""
            CREATE INDEX idx_offline_mappings_available
            ON offline_file_mappings(is_available)
        """)

        // Add composite index for stream ratings
        database.execSQL("""
            CREATE INDEX idx_streams_rating_service
            ON streams(user_rating DESC, service_id)
            WHERE user_rating IS NOT NULL
        """)

        // Add index for frequently accessed stream states
        database.execSQL("""
            CREATE INDEX idx_stream_state_progress
            ON stream_state(progress_millis)
        """)
    }
}
```

**Query optimization:**
```kotlin
// Before: Inefficient query
@Query("""
    SELECT * FROM streams
    WHERE service_id = :serviceId
    AND user_rating IS NOT NULL
    ORDER BY user_rating DESC
""")
fun getRatedStreamsByService(serviceId: Int): Flowable<List<StreamEntity>>

// After: Use index, limit results
@Query("""
    SELECT * FROM streams
    WHERE service_id = :serviceId
    AND user_rating IS NOT NULL
    ORDER BY user_rating DESC
    LIMIT :limit
""")
fun getTopRatedStreamsByService(
    serviceId: Int,
    limit: Int = 100
): Flowable<List<StreamEntity>>
```

---

### Day 86-90: Memory & Performance Optimization

**Tasks:**
- [ ] Profile app with Android Profiler
- [ ] Fix memory leaks detected by LeakCanary
- [ ] Optimize bitmap loading and caching
- [ ] Reduce app startup time
- [ ] Optimize RecyclerView rendering

**Startup optimization:**
```kotlin
// app/src/main/java/org/schabi/newpipe/App.kt
@HiltAndroidApp
class App : Application() {

    override fun onCreate() {
        super.onCreate()

        // Use lazy initialization for non-critical components
        if (BuildConfig.DEBUG) {
            // Initialize debug tools immediately
            setupLeakCanary()
            setupStetho()
        }

        // Critical: Initialize immediately
        initializeNewPipeExtractor()

        // Non-critical: Initialize lazily in background
        GlobalScope.launch(Dispatchers.Default) {
            initializeImageLoader()
            initializeCrashReporter()
            preloadDatabaseConnections()
        }
    }

    private suspend fun preloadDatabaseConnections() {
        // Warm up database on background thread
        NewPipeDatabase.getInstance(this).streamDAO()
    }
}
```

**RecyclerView optimization:**
```kotlin
// app/src/main/java/org/schabi/newpipe/info_list/StreamItemAdapter.kt
class StreamItemAdapter : RecyclerView.Adapter<StreamViewHolder>() {

    init {
        // Enable stable IDs for better performance
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return items[position].url.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StreamViewHolder {
        val binding = StreamItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return StreamViewHolder(binding).apply {
            // Set fixed size for better performance
            itemView.layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                context.resources.getDimensionPixelSize(R.dimen.stream_item_height)
            )
        }
    }

    override fun onBindViewHolder(holder: StreamViewHolder, position: Int) {
        holder.bind(items[position])
    }

    // Use DiffUtil for efficient updates
    fun updateItems(newItems: List<StreamEntity>) {
        val diffCallback = StreamDiffCallback(items, newItems)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        items = newItems
        diffResult.dispatchUpdatesTo(this)
    }
}

class StreamDiffCallback(
    private val oldList: List<StreamEntity>,
    private val newList: List<StreamEntity>
) : DiffUtil.Callback() {

    override fun getOldListSize() = oldList.size
    override fun getNewListSize() = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].url == newList[newItemPosition].url
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}
```

---

## Week 19-20: UI/UX Polish

### Day 91-95: Material Design 3 Migration

**Tasks:**
- [ ] Update to Material Design 3 components
- [ ] Implement Material You dynamic colors
- [ ] Improve animations and transitions
- [ ] Add haptic feedback
- [ ] Improve accessibility

**Dependencies:**
```kotlin
dependencies {
    implementation("com.google.android.material:material:1.12.0")
}
```

**Material 3 theme:**
```xml
<!-- app/src/main/res/values/themes.xml -->
<resources>
    <style name="Theme.NewPipeMP" parent="Theme.Material3.DayNight.NoActionBar">
        <!-- Dynamic colors -->
        <item name="colorPrimary">@color/md_theme_primary</item>
        <item name="colorOnPrimary">@color/md_theme_onPrimary</item>
        <item name="colorPrimaryContainer">@color/md_theme_primaryContainer</item>

        <!-- Surface colors -->
        <item name="colorSurface">@color/md_theme_surface</item>
        <item name="colorOnSurface">@color/md_theme_onSurface</item>

        <!-- Enable dynamic colors on Android 12+ -->
        <item name="dynamicColorThemeOverlay">@style/ThemeOverlay.Material3.DynamicColors.DayNight</item>
    </style>
</resources>
```

---

### Day 96-100: Accessibility & Polish

**Tasks:**
- [ ] Add content descriptions to all UI elements
- [ ] Test with TalkBack
- [ ] Add keyboard navigation support
- [ ] Improve contrast ratios
- [ ] Add haptic feedback for important actions

**Accessibility improvements:**
```kotlin
// app/src/main/java/org/schabi/newpipe/fragments/detail/VideoDetailFragment.kt
private fun setupAccessibility() {
    binding.apply {
        // Content descriptions
        playButton.contentDescription = getString(R.string.play)
        ratingButton.contentDescription = getString(
            R.string.rate_stream_description,
            currentRating?.toString() ?: getString(R.string.unrated)
        )

        // Heading for screen readers
        title.accessibilityHeading = true

        // Live region for status updates
        ViewCompat.setAccessibilityLiveRegion(
            statusText,
            ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE
        )

        // Custom actions
        ViewCompat.addAccessibilityAction(
            thumbnail,
            getString(R.string.view_full_screen)
        ) { _, _ ->
            openFullScreenThumbnail()
            true
        }
    }
}

// Haptic feedback
private fun provideHapticFeedback() {
    binding.root.performHapticFeedback(
        HapticFeedbackConstants.CONTEXT_CLICK,
        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
    )
}
```

---

# PHASE 6: Documentation & Release
**Duration:** 4 weeks
**Goal:** Complete documentation, final testing, stable release
**Success Criteria:** Production-ready release with full documentation

## Week 21-22: Documentation

### Day 101-105: Code Documentation

**Tasks:**
- [ ] Add KDoc to all public classes and methods
- [ ] Create package-level documentation
- [ ] Document complex algorithms
- [ ] Add code examples to documentation
- [ ] Generate KDoc HTML

**KDoc examples:**
```kotlin
/**
 * Helper class for managing weighted shuffle of streams based on user ratings.
 *
 * Uses a probability-based algorithm where streams with higher ratings have
 * a greater chance of appearing earlier in the shuffled list.
 *
 * ## Algorithm
 *
 * 1. Calculate weight for each stream: `weight = (rating / 10)²`
 * 2. Generate random number for each stream: `random * weight`
 * 3. Sort by the generated weighted random values
 *
 * ## Example
 *
 * ```kotlin
 * val streams = listOf(
 *     StreamEntity(..., userRating = 10),
 *     StreamEntity(..., userRating = 5),
 *     StreamEntity(..., userRating = 1)
 * )
 *
 * val shuffled = WeightedShuffleHelper.shuffleByRating(streams)
 * // Stream with rating 10 has ~100x higher chance of being first
 * // compared to stream with rating 1
 * ```
 *
 * @see StreamEntity.userRating
 * @since 1.0.0
 */
object WeightedShuffleHelper {

    /**
     * Shuffles a list of streams using weighted random selection based on ratings.
     *
     * Unrated streams are assigned a default weight equivalent to rating 5.
     *
     * @param streams The list of streams to shuffle. Can include unrated streams.
     * @return A new shuffled list. The original list is not modified.
     * @throws IllegalArgumentException if streams is null
     */
    fun shuffleByRating(streams: List<StreamEntity>): List<StreamEntity> {
        // Implementation...
    }
}
```

---

### Day 106-110: Architecture Documentation

**Tasks:**
- [ ] Create architecture overview document
- [ ] Create data flow diagrams
- [ ] Document database schema with ER diagrams
- [ ] Create sequence diagrams for key flows
- [ ] Write developer onboarding guide

**Files to create:**
```
docs/
├── ARCHITECTURE.md              (architecture overview)
├── DATABASE_SCHEMA.md           (database documentation)
├── DATA_FLOW.md                 (data flow patterns)
├── OFFLINE_PLAYBACK.md          (offline playback implementation)
├── RATING_SYSTEM.md             (rating system documentation)
├── WEIGHTED_SHUFFLE.md          (algorithm explanation)
├── TESTING_GUIDE.md             (testing strategy)
├── CONTRIBUTING_GUIDE.md        (enhanced contribution guide)
└── diagrams/
    ├── architecture_overview.png
    ├── database_er_diagram.png
    ├── offline_playback_flow.png
    ├── bulk_download_sequence.png
    └── player_architecture.png
```

**ARCHITECTURE.md example:**
```markdown
# NewPipeMP Architecture

## Overview

NewPipeMP follows **Clean Architecture** principles with **MVVM** pattern for the presentation layer.

## Layers

### Presentation Layer
- **UI Components**: Fragments, Activities, Views
- **ViewModels**: State management and business logic coordination
- **State**: UI state classes (sealed classes)

### Domain Layer
- **Use Cases**: Business logic units
- **Models**: Domain entities
- **Repositories**: Abstract data access

### Data Layer
- **Repositories**: Concrete implementations
- **Data Sources**: Local (Room) and Remote (NewPipe Extractor)
- **DAOs**: Database access objects

## Dependency Flow

```
UI → ViewModel → UseCase → Repository → DataSource
```

Dependencies point inward (UI depends on ViewModel, ViewModel depends on UseCase, etc.)

## Key Components

### Player Architecture

```
PlayerService (Entry Point)
    ├─ PlayerCore (Playback engine)
    ├─ PlayerUI (UI management)
    ├─ PlayerNotificationManager (Notifications)
    └─ QueueManager (Queue management)
```

### Offline Playback Flow

1. User downloads stream via BulkDownloadInitiator
2. AudioMetadataTagging adds ID3 tags during postprocessing
3. OfflineFileMappingEntity created in database
4. MediaSourceManager checks for offline file before streaming
5. If offline file exists, use local URI instead of network stream

[Detailed sequence diagram: diagrams/offline_playback_flow.png]
```

---

## Week 23: Final Testing & Bug Fixes

### Day 111-115: Comprehensive Testing

**Tasks:**
- [ ] Full regression testing
- [ ] Performance testing on multiple devices
- [ ] Memory leak testing with LeakCanary
- [ ] Battery usage testing
- [ ] Offline mode testing
- [ ] Edge case testing

**Test devices:**
- Android 5.0 (API 21) - minimum supported
- Android 8.0 (API 26) - scoped storage transition
- Android 12+ (API 31+) - Material You, new permissions
- Low-end device (2GB RAM)
- High-end device (8GB+ RAM)
- Tablet

**Testing checklist:**
```markdown
## Functional Testing
- [ ] Bulk download entire playlist
- [ ] Offline playback works when network disabled
- [ ] Rating system saves and displays correctly
- [ ] Weighted shuffle prioritizes high-rated streams
- [ ] Metadata repair extracts from files correctly
- [ ] Player controls work in all states
- [ ] Notifications display correctly
- [ ] Background playback works
- [ ] Picture-in-picture mode works

## Performance Testing
- [ ] App starts in <2 seconds (cold start)
- [ ] UI is responsive (60fps)
- [ ] Database queries complete in <100ms
- [ ] No memory leaks detected
- [ ] Battery usage is reasonable
- [ ] No ANRs (Application Not Responding)

## Compatibility Testing
- [ ] Works on Android 5.0+
- [ ] Supports all screen sizes
- [ ] Works in landscape/portrait
- [ ] Supports split-screen mode
- [ ] Works with TalkBack
- [ ] Keyboard navigation works

## Edge Cases
- [ ] Handles no internet gracefully
- [ ] Handles corrupted downloads
- [ ] Handles deleted local files
- [ ] Handles database corruption
- [ ] Handles concurrent access
- [ ] Handles rapid user interactions
```

---

### Day 116-117: Bug Fixes

**Tasks:**
- [ ] Fix all critical bugs found in testing
- [ ] Fix all high-priority bugs
- [ ] Document known medium/low priority issues
- [ ] Update issue tracker

---

## Week 24: Release Preparation & Launch

### Day 118-120: Release Preparation

**Tasks:**
- [ ] Update version number to 1.0.0
- [ ] Create changelog (CHANGELOG.md)
- [ ] Update README with new features
- [ ] Create release notes
- [ ] Generate signed release APK
- [ ] Test release APK thoroughly

**CHANGELOG.md:**
```markdown
# Changelog

All notable changes to NewPipeMP will be documented in this file.

## [1.0.0] - 2026-09-01

### Added
- 10-star rating system for streams
- Weighted shuffle algorithm prioritizing highly-rated content
- Offline playback with automatic file mapping
- Bulk playlist download functionality
- Automatic ID3 metadata tagging for downloaded audio
- Metadata repair utility to extract from downloaded files
- Playback statistics tracking
- Rating statistics dashboard
- Modern MVVM architecture with Hilt dependency injection
- Comprehensive test suite (80%+ coverage)
- Full Kotlin migration

### Changed
- Refactored Player.java into modular architecture
- Migrated from RxJava to Kotlin Coroutines (new code)
- Updated to Material Design 3
- Improved performance and memory usage
- Enhanced error handling throughout app
- Optimized database with additional indices

### Fixed
- Blocking database operations on main thread
- Service connection leaks in bulk download
- Resource leaks in metadata extraction
- All deprecated API usage
- Memory leaks detected by LeakCanary

### Removed
- ViewPager (replaced with ViewPager2)
- Legacy Java utilities (converted to Kotlin)

## [0.28.3-fork] - 2026-03-01

Initial fork from NewPipe with basic rating and offline features.

[1.0.0]: https://github.com/yourusername/NewPipeMP/releases/tag/v1.0.0
```

---

### Day 121-124: Launch & Monitoring

**Tasks:**
- [ ] Create GitHub release
- [ ] Publish to F-Droid (if applicable)
- [ ] Update project website
- [ ] Announce on social media
- [ ] Monitor crash reports
- [ ] Respond to user feedback
- [ ] Create post-release roadmap

**GitHub Release:**
```markdown
# NewPipeMP v1.0.0 - Stable Release 🎉

We're excited to announce the first stable release of NewPipeMP!

## 🎵 What is NewPipeMP?

NewPipeMP is a fork of NewPipe that adds professional music library features:
- **10-star rating system** to organize your favorite content
- **Weighted shuffle** that plays highly-rated streams more often
- **Offline playback** with automatic file mapping
- **Bulk downloads** with proper metadata tagging
- **Playback statistics** to track your listening habits

## ✨ Highlights of v1.0.0

- **Complete architecture refactor** with modern MVVM + Clean Architecture
- **Full Kotlin migration** for better safety and conciseness
- **80%+ test coverage** ensuring reliability
- **Performance optimizations** for smooth 60fps experience
- **Material Design 3** with dynamic colors on Android 12+

## 📥 Download

[Download APK](https://github.com/yourusername/NewPipeMP/releases/download/v1.0.0/newpipemp-v1.0.0.apk)

## 📚 Documentation

- [User Guide](https://github.com/yourusername/NewPipeMP/blob/main/README.md)
- [Architecture](https://github.com/yourusername/NewPipeMP/blob/main/docs/ARCHITECTURE.md)
- [Contributing](https://github.com/yourusername/NewPipeMP/blob/main/CONTRIBUTING.md)

## 🙏 Acknowledgments

Special thanks to the NewPipe team for creating an amazing foundation.

## 🐛 Reporting Issues

Please report bugs at: https://github.com/yourusername/NewPipeMP/issues
```

---

# POST-RELEASE ROADMAP

## Maintenance Phase (Ongoing)

### Monthly Tasks
- [ ] Monitor crash reports
- [ ] Fix critical bugs
- [ ] Update dependencies
- [ ] Security patches
- [ ] Performance monitoring

### Quarterly Tasks
- [ ] Review and merge community PRs
- [ ] Add community-requested features
- [ ] Refine documentation based on feedback
- [ ] Performance audits
- [ ] Update to latest Android APIs

---

## Future Enhancements (v1.1+)

### v1.1 - Smart Features (Q4 2026)
- [ ] Smart playlists based on ratings and statistics
- [ ] Recommendation engine
- [ ] Advanced filtering and sorting
- [ ] Export/import ratings data
- [ ] Rating statistics visualization improvements

### v1.2 - Social Features (Q1 2027)
- [ ] Share ratings with friends (optional)
- [ ] Collaborative playlists
- [ ] Rating sync across devices
- [ ] Community recommendations

### v1.3 - AI Integration (Q2 2027)
- [ ] AI-powered recommendations
- [ ] Automatic content categorization
- [ ] Smart shuffle with mood detection
- [ ] Lyrics integration

---

# METRICS & SUCCESS CRITERIA

## Development Metrics

### Phase Completion Criteria

**Phase 1: Foundation**
- ✅ Zero critical bugs
- ✅ All resource leaks fixed
- ✅ All blocking operations resolved
- ✅ Service connection leaks fixed

**Phase 2: Testing**
- ✅ 50%+ overall test coverage
- ✅ 80%+ coverage for new features
- ✅ 100% coverage for critical utilities
- ✅ All DAOs tested

**Phase 3: Architecture**
- ✅ Hilt dependency injection implemented
- ✅ Repository layer complete
- ✅ Player.java refactored (<500 lines per module)
- ✅ ViewModels for all major fragments

**Phase 4: Kotlin**
- ✅ 100% Kotlin codebase
- ✅ Coroutines for all new async code
- ✅ Proper null safety throughout

**Phase 5: Performance**
- ✅ Database indices added
- ✅ Zero memory leaks
- ✅ <2s cold start time
- ✅ Consistent 60fps UI

**Phase 6: Release**
- ✅ Complete documentation
- ✅ All tests passing
- ✅ Release APK generated
- ✅ Public release

## Quality Metrics

### Code Quality
- **Lines of Code:** ~88,000 → ~75,000 (Kotlin is more concise)
- **Test Coverage:** 15% → 80%+
- **TODO Markers:** 44 → 0
- **Deprecated APIs:** 5 files → 0
- **Critical Issues:** 4 → 0
- **Cyclomatic Complexity:** High (Player.java) → Low (modular)

### Performance Metrics
- **Cold Start Time:** <2 seconds
- **Database Query Time:** <100ms average
- **Memory Usage:** <150MB idle, <300MB active
- **Frame Rate:** Consistent 60fps
- **APK Size:** <15MB

### Maintainability Metrics
- **Max File Size:** <600 lines
- **Max Method Complexity:** <10 cyclomatic complexity
- **Documentation Coverage:** 80%+
- **Dependency Injection:** 100% of new code

---

# RISK MANAGEMENT

## Identified Risks

### Technical Risks

**1. Breaking Changes During Refactor**
- **Risk:** Refactoring may introduce regressions
- **Mitigation:**
  - Comprehensive test suite before refactoring
  - Feature flags for major changes
  - Incremental refactoring in small PRs
  - Beta testing program

**2. Performance Degradation**
- **Risk:** New architecture may be slower
- **Mitigation:**
  - Benchmark before/after each phase
  - Performance testing on low-end devices
  - Profiling at every milestone

**3. Database Migration Issues**
- **Risk:** Data loss during schema changes
- **Mitigation:**
  - Extensive migration testing
  - Database backup/restore functionality
  - Fallback migration paths
  - Version skipping support

### Timeline Risks

**1. Underestimated Complexity**
- **Risk:** Some tasks may take longer than planned
- **Mitigation:**
  - 20% buffer time in each phase
  - Prioritize critical features
  - Flexible scope for lower-priority items

**2. Blocking Issues**
- **Risk:** Critical bugs blocking progress
- **Mitigation:**
  - Parallel workstreams where possible
  - Quick escalation process
  - Emergency bug fix protocol

### Resource Risks

**1. Single Developer Bandwidth**
- **Risk:** One person can't complete all work
- **Mitigation:**
  - Community contributions welcome
  - Clear contribution guidelines
  - Good documentation for onboarding
  - Break work into manageable chunks

---

# ROLLBACK PLAN

## If Critical Issues Arise

### Rollback Triggers
- Critical data loss bug
- Widespread crashes (>5% crash rate)
- Major security vulnerability
- Unrecoverable performance regression

### Rollback Process
1. **Immediate:** Remove release from distribution
2. **Communication:** Notify users of issue
3. **Revert:** Roll back to last stable version
4. **Fix:** Address critical issue in hotfix branch
5. **Test:** Thorough testing of fix
6. **Release:** Deploy patched version

### Version Control Strategy
- Main branch: Always stable, production-ready
- Develop branch: Integration branch for features
- Feature branches: Individual features/refactors
- Release branches: Preparation for releases
- Hotfix branches: Critical fixes for production

---

# COMMUNICATION PLAN

## Stakeholder Updates

### Weekly (During Refactoring)
- Progress update on GitHub discussions
- Completed tasks vs. planned
- Any blockers or risks
- Next week's focus

### Phase Completion
- Detailed phase report
- Metrics achieved
- Lessons learned
- Preview of next phase

### Pre-Release
- Beta testing announcement
- Feature showcase
- Migration guide for users
- Developer changelog

---

# CONCLUSION

This roadmap provides a comprehensive path to transform NewPipeMP from its current state to a production-grade, modern Android application.

**Estimated Timeline:** 6 months
**Total Phases:** 6
**Target Code Quality:** Production-ready
**Test Coverage Goal:** 80%+
**Architecture:** Clean Architecture + MVVM
**Language:** 100% Kotlin

**Success will be measured by:**
- Zero critical issues
- Comprehensive test coverage
- Complete documentation
- Modern, maintainable architecture
- Excellent performance
- Happy users! 🎉

---

**Next Step:** Begin Phase 1, Day 1 - Fix Blocking Database Operations

**Questions?** Open a GitHub discussion or issue.

**Contributors Welcome!** See CONTRIBUTING.md for guidelines.
