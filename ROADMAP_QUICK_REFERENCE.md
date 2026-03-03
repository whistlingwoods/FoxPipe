# NewPipeMP Refactoring Roadmap - Quick Reference

**Full Roadmap:** See [REFACTORING_ROADMAP.md](REFACTORING_ROADMAP.md)

---

## 6-Month Timeline Overview

```
Month 1: Foundation & Testing Setup
Month 2: Testing & Architecture Foundation
Month 3: Architecture Migration & Player Refactor
Month 4: Kotlin Migration & Coroutines
Month 5: Performance & Polish
Month 6: Documentation & Release
```

---

## Phase Summary

### Phase 1: Foundation & Critical Fixes (Weeks 1-3)
**Goal:** Fix all critical bugs
- Fix blocking database operations
- Fix service connection leaks
- Fix resource leaks
- Extract hardcoded values
- Standardize error handling

**Key Deliverables:**
- Zero critical bugs
- Consistent error handling
- All TODO markers documented as GitHub issues

---

### Phase 2: Testing Infrastructure (Weeks 4-6)
**Goal:** 50%+ test coverage
- Setup testing framework
- Unit tests for all utilities
- Database & repository tests
- UI & fragment tests

**Key Deliverables:**
- 50%+ overall coverage
- 80%+ coverage for new features
- 100% coverage for critical utilities

---

### Phase 3: Architecture Migration (Weeks 7-12)
**Goal:** Modern MVVM + Clean Architecture
- Implement Hilt dependency injection
- Create repository layer
- Refactor Player.java (2,792 lines → <500 per module)
- Create ViewModels for all fragments

**Key Deliverables:**
- Scalable, testable architecture
- Modular player architecture
- Full dependency injection

---

### Phase 4: Kotlin Migration (Weeks 13-16)
**Goal:** 100% Kotlin codebase
- Convert all Java to Kotlin
- Migrate to coroutines
- Leverage Kotlin idioms
- Modern null safety

**Key Deliverables:**
- Zero Java files
- Coroutines for all async operations
- Idiomatic Kotlin code

---

### Phase 5: Performance & Polish (Weeks 17-20)
**Goal:** Optimize and polish
- Database optimization with indices
- Memory leak fixes
- UI/UX improvements
- Material Design 3 migration
- Accessibility improvements

**Key Deliverables:**
- <2s cold start time
- 60fps consistent
- Zero memory leaks
- Material Design 3

---

### Phase 6: Documentation & Release (Weeks 21-24)
**Goal:** Production release
- Complete code documentation
- Architecture documentation
- Final testing & bug fixes
- Release v1.0.0

**Key Deliverables:**
- 80%+ documentation coverage
- All tests passing
- Stable v1.0.0 release

---

## Critical Issues to Fix (Phase 1)

### Week 1: Blocking Operations
```java
// BEFORE (BAD)
dao.getMapping().blockingGet()  // Blocks UI thread!

// AFTER (GOOD)
dao.getMapping()
    .timeout(5, TimeUnit.SECONDS)
    .subscribeOn(Schedulers.io())
    .observeOn(AndroidSchedulers.mainThread())
```

### Week 1: Service Connection Leaks
```java
// BEFORE (BAD)
context.bindService(intent, connection, BIND_AUTO_CREATE)
// No timeout, leaks if service never connects

// AFTER (GOOD)
ServiceBindingHelper with 5-second timeout
```

### Week 1: Resource Leaks
```java
// BEFORE (BAD)
} catch (Exception ignored) {}  // Swallows errors!

// AFTER (GOOD)
} catch (RuntimeException e) {
    Log.w(TAG, "Error releasing resource", e)
}
```

---

## Key Metrics Tracking

### Current State → Target State

| Metric | Current | Target |
|--------|---------|--------|
| Test Coverage | 15% | 80%+ |
| Critical Issues | 4 | 0 |
| TODO Markers | 44 | 0 |
| Max File Size | 2,792 lines | <600 lines |
| Language | 60% Java | 100% Kotlin |
| Deprecated APIs | 5 files | 0 |
| Cold Start Time | ~3s | <2s |
| Memory Leaks | Some | 0 |

---

## Tech Stack Evolution

### Current
- Hybrid Java/Kotlin
- RxJava for async
- No dependency injection
- Manual singleton management
- MVP + MVVM mixed

### Target
- 100% Kotlin
- Coroutines + Flow
- Hilt dependency injection
- Repository pattern
- Clean Architecture + MVVM

---

## File Structure After Refactor

```
app/src/main/java/org/schabi/newpipe/
├── di/                          # NEW: Hilt modules
│   ├── DatabaseModule.kt
│   ├── RepositoryModule.kt
│   └── NetworkModule.kt
├── data/                        # NEW: Data layer
│   └── repository/
│       ├── StreamRepository.kt
│       ├── OfflinePlaybackRepository.kt
│       └── impl/
├── domain/                      # NEW: Domain layer
│   └── usecase/
│       ├── GetRatedStreamsUseCase.kt
│       └── UpdateRatingUseCase.kt
├── presentation/                # Renamed from fragments/
│   ├── detail/
│   │   ├── VideoDetailFragment.kt
│   │   └── VideoDetailViewModel.kt
│   └── statistics/
│       ├── RatingStatisticsFragment.kt
│       └── RatingStatisticsViewModel.kt
├── player/                      # REFACTORED: Modular
│   ├── Player.kt (400 lines)
│   ├── core/
│   │   ├── PlayerCore.kt
│   │   └── PlaybackController.kt
│   ├── ui/
│   │   ├── PlayerUI.kt
│   │   └── PlayerControls.kt
│   └── notification/
│       └── PlayerNotificationManager.kt
└── util/                        # All Kotlin
    ├── constants/
    │   ├── AppConstants.kt
    │   └── PlayerConstants.kt
    ├── RatingHelper.kt
    ├── WeightedShuffleHelper.kt
    └── StreamMetadataRepair.kt
```

---

## Weekly Milestones

### Month 1
- **Week 1:** Fix all blocking operations, service leaks
- **Week 2:** Extract constants, fix deprecated APIs
- **Week 3:** Standardize error handling, create TODO issues
- **Week 4:** Setup testing framework, first unit tests

### Month 2
- **Week 5:** Database & repository tests
- **Week 6:** UI tests, achieve 50% coverage
- **Week 7:** Hilt setup and module creation
- **Week 8:** Repository layer implementation

### Month 3
- **Week 9:** Player refactoring part 1
- **Week 10:** Player refactoring part 2
- **Week 11:** ViewModels creation part 1
- **Week 12:** ViewModels creation part 2

### Month 4
- **Week 13:** Java to Kotlin conversion (utilities)
- **Week 14:** Java to Kotlin conversion (fragments)
- **Week 15:** Coroutines migration part 1
- **Week 16:** Coroutines migration part 2

### Month 5
- **Week 17:** Database optimization
- **Week 18:** Memory & performance optimization
- **Week 19:** Material Design 3 migration
- **Week 20:** Accessibility & polish

### Month 6
- **Week 21:** Code documentation
- **Week 22:** Architecture documentation
- **Week 23:** Final testing & bug fixes
- **Week 24:** Release preparation & launch

---

## Quick Start Guide

### Starting Phase 1

1. **Create feature branch:**
   ```bash
   git checkout -b refactor/phase1-critical-fixes
   ```

2. **Day 1-2: Fix OfflinePlaybackHelper.java**
   - File: `app/src/main/java/org/schabi/newpipe/util/OfflinePlaybackHelper.java`
   - Change `hasOfflineFile()` from blocking to async
   - Update all callers
   - Add tests

3. **Day 3-4: Fix BulkDownloadInitiator.java**
   - File: `app/src/main/java/org/schabi/newpipe/download/BulkDownloadInitiator.java`
   - Add timeout handling
   - Implement proper cleanup
   - Add tests

4. **Day 5: Fix StreamMetadataRepair.java**
   - File: `app/src/main/java/org/schabi/newpipe/util/StreamMetadataRepair.java`
   - Improve resource cleanup
   - Remove exception swallowing
   - Add tests

5. **Commit and PR:**
   ```bash
   git add .
   git commit -m "Phase 1 Week 1: Fix critical blocking operations and resource leaks"
   git push origin refactor/phase1-critical-fixes
   ```

---

## Testing Strategy

### Unit Tests (Target: 60% coverage)
- All utility classes
- All repositories
- All ViewModels
- All use cases

### Integration Tests (Target: 15% coverage)
- Database operations
- Repository implementations
- End-to-end flows

### UI Tests (Target: 5% coverage)
- Critical user flows
- Fragment interactions
- Player controls

---

## Risk Mitigation

### High Risk Areas
1. **Player Refactoring**
   - Risk: Breaking playback
   - Mitigation: Extensive testing, feature flags

2. **Database Migrations**
   - Risk: Data loss
   - Mitigation: Backup/restore, thorough testing

3. **Kotlin Migration**
   - Risk: Subtle behavior changes
   - Mitigation: Keep tests, incremental conversion

### Contingency Plans
- Rollback branches for each phase
- Feature flags for major changes
- Beta testing program
- Quick hotfix process

---

## Success Criteria Checklist

### Phase 1 Complete ✓
- [ ] Zero critical bugs
- [ ] All blocking operations fixed
- [ ] All resource leaks fixed
- [ ] Error handling standardized
- [ ] All TODOs documented

### Phase 2 Complete ✓
- [ ] 50%+ test coverage
- [ ] All new features tested
- [ ] CI/CD pipeline setup
- [ ] Coverage reporting enabled

### Phase 3 Complete ✓
- [ ] Hilt fully integrated
- [ ] Repository pattern implemented
- [ ] Player modularized
- [ ] All fragments use ViewModels

### Phase 4 Complete ✓
- [ ] 100% Kotlin codebase
- [ ] Coroutines for all async
- [ ] No RxJava in new code
- [ ] Idiomatic Kotlin

### Phase 5 Complete ✓
- [ ] <2s cold start
- [ ] 60fps consistent
- [ ] Zero memory leaks
- [ ] Material Design 3

### Phase 6 Complete ✓
- [ ] 80%+ documentation
- [ ] All tests passing
- [ ] Release APK signed
- [ ] v1.0.0 published

---

## Tools & Dependencies

### Development
- Android Studio Hedgehog or newer
- Java 17 (for Gradle)
- Git

### Testing
- JUnit 5
- MockK
- Turbine (Flow testing)
- Espresso
- Jacoco (coverage)

### Architecture
- Hilt 2.51
- Room 2.7.2
- Kotlin Coroutines 1.8.0
- Lifecycle 2.7.0

### Quality
- Checkstyle
- Ktlint
- LeakCanary 2.14
- SonarQube

---

## Resources

- **Full Roadmap:** [REFACTORING_ROADMAP.md](REFACTORING_ROADMAP.md)
- **Architecture Docs:** Will be in `docs/` after Phase 6
- **Contributing:** [CONTRIBUTING.md](CONTRIBUTING.md)
- **Issues:** [GitHub Issues](https://github.com/yourusername/NewPipeMP/issues)

---

## Contact & Questions

- **GitHub Discussions:** For questions and discussion
- **Issues:** For bugs and feature requests
- **Pull Requests:** Always welcome!

---

**Let's build something amazing! 🚀**

**Next Action:** Start Phase 1, Day 1 - Fix OfflinePlaybackHelper.java
