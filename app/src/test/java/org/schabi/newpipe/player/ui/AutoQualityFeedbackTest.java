package org.schabi.newpipe.player.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class AutoQualityFeedbackTest {
    @Test
    public void onlyActualChangesHaveOldAndNewResolution() {
        final AutoQualityFeedback feedback = new AutoQualityFeedback();
        assertNull(feedback.update("live", true, "1080p"));
        assertNull(feedback.update("live", true, "1080p"));
        final AutoQualityFeedback.Change change = feedback.update("live", true, "720p");
        assertEquals("1080p", change.from());
        assertEquals("720p", change.to());
        assertNull(feedback.update("live", true, "720p"));
        assertEquals(new AutoQualityFeedback.Change("720p", "1080p"),
                feedback.update("live", true, "1080p"));
    }

    @Test
    public void manualSelectionAndNewStreamsDoNotAnnounceChanges() {
        final AutoQualityFeedback feedback = new AutoQualityFeedback();
        feedback.update("live", true, "1080p");
        assertNull(feedback.update("live", false, "480p"));
        assertNull(feedback.update("live", true, "720p"));
        assertNull(feedback.update("other live", true, "1080p"));
        feedback.reset();
        assertNull(feedback.update("other live", true, "480p"));
    }

    @Test
    public void unknownSizesDoNotReplaceLastKnownResolution() {
        final AutoQualityFeedback feedback = new AutoQualityFeedback();
        assertNull(feedback.update("live", true, null));
        assertNull(feedback.update("live", true, "1080p"));
        assertNull(feedback.update("live", true, null));
        assertEquals(new AutoQualityFeedback.Change("1080p", "720p"),
                feedback.update("live", true, "720p"));
        assertNull(feedback.update(null, true, "720p"));
        assertNull(feedback.update("live", true, "480p"));
    }
}
