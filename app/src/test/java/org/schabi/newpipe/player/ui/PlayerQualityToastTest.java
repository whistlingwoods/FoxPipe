package org.schabi.newpipe.player.ui;

import static org.junit.Assert.assertEquals;

import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.view.ContextThemeWrapper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.schabi.newpipe.R;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@SuppressWarnings("deprecation")
public class PlayerQualityToastTest {
    @Test
    @Config(qualifiers = "port")
    public void portraitUsesTopAndDisplaysBothQualities() {
        final Toast toast = show();
        assertEquals(Gravity.TOP | Gravity.CENTER_HORIZONTAL, toast.getGravity());
        assertEquals("Auto quality changed from 1080p to 720p",
                ((TextView) toast.getView()).getText().toString());
        toast.cancel();
    }

    @Test
    @Config(qualifiers = "land")
    public void landscapeUsesBottom() {
        final Toast toast = show();
        assertEquals(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, toast.getGravity());
        toast.cancel();
    }

    private Toast show() {
        return PlayerQualityToast.show(new ContextThemeWrapper(
                        RuntimeEnvironment.getApplication(), R.style.LightTheme),
                "Auto quality changed from 1080p to 720p");
    }
}
