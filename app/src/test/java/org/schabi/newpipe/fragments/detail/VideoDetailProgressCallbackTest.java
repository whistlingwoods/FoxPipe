package org.schabi.newpipe.fragments.detail;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.util.DependentPreferenceHelper;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;

import java.lang.reflect.Field;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
public class VideoDetailProgressCallbackTest {
    private static final String URL = "https://www.youtube.com/watch?v=test";

    @Test
    public void disconnectedPlayerIgnoresLateProgress() throws Exception {
        final Fixture fixture = new Fixture();
        field(fixture.fragment, "player", null);
        fixture.fragment.onProgressUpdate(2_000, 30_000, 50);
        verify(fixture.player, never()).isPlaying();
    }

    @Test
    public void destroyedViewAndDismissedPlayerIgnoreLateProgress() throws Exception {
        final Fixture fixture = new Fixture();
        field(fixture.fragment, "binding", null);
        fixture.fragment.onProgressUpdate(2_000, 30_000, 50);
        field(fixture.fragment, "binding", mock(FragmentVideoDetailBinding.class));
        field(fixture.fragment, "playerDismissed", true);
        fixture.fragment.onProgressUpdate(2_000, 30_000, 50);
        verify(fixture.player, never()).isPlaying();
    }

    @Test
    public void pausedPlayerAndMissingFragmentQueueDoNotReadTheServiceQueue() throws Exception {
        final Fixture fixture = new Fixture();
        when(fixture.player.isPlaying()).thenReturn(false);
        fixture.fragment.onProgressUpdate(2_000, 30_000, 50);
        when(fixture.player.isPlaying()).thenReturn(true);
        fixture.fragment.playQueue = null;
        fixture.fragment.onProgressUpdate(2_000, 30_000, 50);
        verify(fixture.player, never()).getPlayQueue();
    }

    @Test
    public void missingServiceQueueOrCurrentItemDoesNotCrash() throws Exception {
        final Fixture fixture = new Fixture();
        when(fixture.player.getPlayQueue()).thenReturn(null);
        fixture.fragment.onProgressUpdate(2_000, 30_000, 50);
        when(fixture.player.getPlayQueue()).thenReturn(fixture.queue);
        when(fixture.queue.getItem()).thenReturn(null);
        fixture.fragment.onProgressUpdate(2_000, 30_000, 50);
    }

    @Test
    public void activeMatchingItemStillReachesTheProgressUpdater() throws Exception {
        final Fixture fixture = new Fixture();
        try (var helper = mockStatic(DependentPreferenceHelper.class)) {
            // Resume progress disabled: exercise the real callback without inflating its view.
            fixture.fragment.onProgressUpdate(2_000, 30_000, 50);
            helper.verify(() -> DependentPreferenceHelper.getResumePlaybackEnabled(null));
        }
    }

    @Test
    public void anotherQueueItemDoesNotUpdateThisVideosProgress() throws Exception {
        final Fixture fixture = new Fixture();
        when(fixture.item.getUrl()).thenReturn("https://www.youtube.com/watch?v=other");
        try (var helper = mockStatic(DependentPreferenceHelper.class)) {
            fixture.fragment.onProgressUpdate(2_000, 30_000, 50);
            helper.verifyNoInteractions();
        }
    }

    private static void field(final Object target, final String name, final Object value)
            throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                final Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (final NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static final class Fixture {
        private final VideoDetailFragment fragment =
                mock(VideoDetailFragment.class, CALLS_REAL_METHODS);
        private final Player player = mock(Player.class);
        private final PlayQueue queue = mock(PlayQueue.class);
        private final PlayQueueItem item = mock(PlayQueueItem.class);

        private Fixture() throws Exception {
            field(fragment, "player", player);
            field(fragment, "binding", mock(FragmentVideoDetailBinding.class));
            field(fragment, "url", URL);
            fragment.playQueue = queue;
            when(player.isPlaying()).thenReturn(true);
            when(player.getPlayQueue()).thenReturn(queue);
            when(queue.getItem()).thenReturn(item);
            when(item.getUrl()).thenReturn(URL);
        }
    }
}
