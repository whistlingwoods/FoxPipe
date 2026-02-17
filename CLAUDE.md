# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

NewPipe is a libre lightweight streaming front-end for Android that supports YouTube, PeerTube, Bandcamp, SoundCloud, and media.ccc.de. The app works by fetching data from official APIs or parsing websites, without requiring user accounts or Google Play Services.

**IMPORTANT CONTEXT:**
- The codebase is in **maintenance mode** on the `dev` branch. Only bugfixes are accepted here.
- New features should be contributed to the `refactor` branch (separate rewrite effort).
- The main branch for development is `dev`, not `master`.

## Build & Development Commands

### Building the App

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (unsigned)
./gradlew assembleRelease

# Install debug build on connected device
./gradlew installDebug
```

### Code Quality & Linting

The project enforces strict code style. Both checkStyle (Java) and ktlint (Kotlin) run automatically on every build.

```bash
# Run checkStyle (Java code style)
./gradlew runCheckstyle

# Run ktlint (Kotlin code style)
./gradlew runKtlint

# Auto-format Kotlin code
./gradlew formatKtlint

# Check dependency order in libs.versions.toml
./gradlew checkDependenciesOrder

# Skip ktlint formatting on build (not recommended)
./gradlew assembleDebug -DskipFormatKtlint
```

**Style Configuration:**
- Java: `checkstyle/checkstyle.xml`
- Kotlin: `.editorconfig` (many standard rules disabled)

### Testing

```bash
# Run unit tests
./gradlew test

# Run specific test class
./gradlew test --tests "org.schabi.newpipe.util.ListHelperTest"

# Run instrumentation tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Run specific instrumented test
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.schabi.newpipe.database.AppDatabaseTest
```

Test locations:
- Unit tests: `app/src/test/java/`
- Instrumented tests: `app/src/androidTest/java/`
- Room schema tests: `app/schemas/` (database migrations)

## Architecture

### High-Level Structure

NewPipe follows a layered architecture with fragments as the primary UI component:

```
UI Layer (Activities/Fragments)
    ↓
Business Logic (Player, Managers, Helpers)
    ↓
Data Layer (Room Database, ExtractorHelper)
    ↓
External APIs (NewPipe Extractor, ExoPlayer)
```

### Core Components

**1. Player System (`app/src/main/java/org/schabi/newpipe/player/`)**

The most complex component in the codebase:
- `Player.java`: Core playback engine built on ExoPlayer 2
- `PlayerService.java`: Background service for playback continuation
- `player/ui/`: Multiple player UI implementations (main, popup, video)
  - Uses pluggable `PlayerUiList` architecture
  - `MainPlayerUi`: Full-screen player in VideoDetailFragment
  - `PopupPlayerUi`: Floating/PIP mode
- `player/playqueue/`: Playlist management with RxJava event system
- `player/playback/`: Media source management and pre-loading
- `player/gesture/`: Brightness/volume/seek gesture handling
- `player/notification/`: Playback notification UI

**2. Fragment Navigation (`app/src/main/java/org/schabi/newpipe/fragments/`)**

- `MainActivity`: Fragment container with drawer navigation
- `MainFragment`: Home screen with customizable tabs
- `VideoDetailFragment`: Video viewing screen with embedded player
- `fragments/list/`: List-based screens (search, channel, playlist, comments)
- `BaseStateFragment`: State machine for loading/error/content states

**3. Database Layer (`app/src/main/java/org/schabi/newpipe/database/`)**

Room-based SQLite database with reactive queries:
- `AppDatabase.kt`: Main database with migration system
- DAOs: `SubscriptionDAO`, `PlaylistDAO`, `StreamDAO`, `StreamHistoryDAO`, etc.
- Entities track: subscriptions, playlists, watch history, feed, stream states

**4. Local Content (`app/src/main/java/org/schabi/newpipe/local/`)**

- `local/subscription/`: Subscription management
- `local/playlist/`: Local and remote playlist handling
- `local/history/`: Watch history and statistics
- `local/feed/`: Aggregated subscription feed
- `local/bookmark/`: Saved channels and playlists

**5. Data Extraction (`ExtractorHelper` + NewPipe Extractor)**

- `util/ExtractorHelper.java`: RxJava wrapper around NewPipe Extractor library
- Handles multi-service abstraction (YouTube, PeerTube, etc.)
- All network operations are asynchronous via RxJava3

### Key Design Patterns

- **Reactive Programming**: Heavy use of RxJava3 for all async operations
- **Observer Pattern**: PlayQueue and PlaybackListener for event broadcasting
- **State Preservation**: Automatic state saving via Bridge library
- **Plugin Architecture**: Multiple PlayerUi implementations in PlayerUiList
- **Repository Pattern**: DAOs provide reactive data access

## Important Development Guidelines

### When Modifying the Player

The player is the most critical and complex component:
- Changes to `Player.java` affect all playback modes (background, popup, main)
- Test all three player UIs: MainPlayerUi, PopupPlayerUi, VideoPlayerUi
- Player state is managed through `PlayerUiList` - understand the event flow
- MediaSession integration affects lock screen and external controls

### Working with NewPipe Extractor

When changes require modifications to the extractor library:
1. Clone/modify NewPipe Extractor separately
2. Edit `app/build.gradle.kts` under "NewPipe libraries" section
3. Comment out the published dependency and use local path:
   ```kotlin
   // implementation(libs.newpipe.extractor)
   implementation("com.github.teamnewpipe:NewPipeExtractor:LOCAL")
   implementation(project(":extractor"))
   ```
4. Test thoroughly before submitting PR

### Database Migrations

Room database schema is versioned in `app/schemas/`:
- Never modify existing migrations
- Add new migrations in `AppDatabase.kt`
- Test migrations with instrumented tests in `app/src/androidTest/`

### Fragment State Management

- Use `StateSaver` for preserving state across configuration changes
- Fragments inherit from `BaseFragment` or `BaseStateFragment`
- Override `writeTo()` and implement state restoration

### Navigation

- Use `NavigationHelper` for all navigation between screens
- Deep linking is handled by `RouterActivity`
- Fragment back stack is managed by `MainActivity`

## Code Style Requirements

- **Java**: Follows checkstyle configuration in `checkstyle/checkstyle.xml`
- **Kotlin**: Many standard ktlint rules are disabled (see `.editorconfig`)
- Code style checks run automatically on every build
- PRs must pass all style checks to be merged

**Common Issues:**
- Missing Javadoc on public methods
- Incorrect indentation
- Line length violations (though somewhat relaxed)
- Unused imports

## Testing Requirements

- Unit tests should cover utility functions and data processing
- Instrumented tests are required for database operations
- Player functionality should be manually tested in all three modes
- Test on both phone and tablet layouts when changing UI

## Key Files to Know

- `app/src/main/java/org/schabi/newpipe/App.java`: Application initialization
- `app/src/main/java/org/schabi/newpipe/MainActivity.java`: Main activity container
- `app/src/main/java/org/schabi/newpipe/util/NavigationHelper.java`: Navigation logic
- `app/src/main/java/org/schabi/newpipe/util/ExtractorHelper.java`: Extractor wrapper
- `app/src/main/java/org/schabi/newpipe/player/Player.java`: Core player (2000+ lines)
- `app/build.gradle.kts`: Build configuration, dependencies
- `gradle/libs.versions.toml`: Centralized dependency versions

## Dependencies

Key third-party libraries:
- **ExoPlayer**: Media playback engine
- **RxJava3 + RxAndroid**: Reactive programming
- **Room**: SQLite database
- **Groupie**: RecyclerView adapter management
- **Picasso**: Image loading
- **Markwon**: Markdown rendering
- **OkHttp**: HTTP client
- **ACRA**: Crash reporting

## F-Droid Compliance

NewPipe is distributed via F-Droid and must comply with their policies:
- No closed-source libraries (especially Google libraries)
- No tracking or proprietary services
- Reproducible builds (resource shrinking disabled in release builds)
- All dependencies must be open source

When adding dependencies, verify they are F-Droid compatible.
