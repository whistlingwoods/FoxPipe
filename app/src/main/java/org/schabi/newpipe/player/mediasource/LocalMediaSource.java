package org.schabi.newpipe.player.mediasource;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.WrappingMediaSource;

import org.schabi.newpipe.player.playqueue.PlayQueueItem;

public class LocalMediaSource extends WrappingMediaSource implements ManagedMediaSource {

    public LocalMediaSource(@NonNull final MediaSource source) {
        super(source);

    }

    @Override
    public boolean shouldBeReplacedWith(@NonNull final PlayQueueItem newIdentity,
                                        final boolean isInterruptable) {
        return false;
    }

    @Override
    public boolean isStreamEqual(@NonNull final PlayQueueItem otherStream) {
        return otherStream.getUrl() == this.mediaSource.getMediaItem().mediaId;
    }
}