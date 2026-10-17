package org.schabi.newpipe.local.feed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.schabi.newpipe.R;
import org.schabi.newpipe.util.GridLayoutManagerHelper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class FeedCardOrientationTest {
    @Test
    public void rotationAndRefreshDoNotMixLandscapeRowsWithPortraitCards() {
        RuntimeEnvironment.setQualifiers("land");
        final Application context = RuntimeEnvironment.getApplication();
        context.setTheme(R.style.LightTheme);
        final RecyclerView list = new RecyclerView(context);
        final CardAdapter adapter = new CardAdapter();
        list.setLayoutManager(new GridLayoutManager(context, 1));
        list.setItemAnimator(null);
        list.setAdapter(adapter);
        layout(list);
        assertTrue(list.getChildCount() > 0);
        assertTrue(thumbnailWidth(list.getChildAt(0)) > 0);

        RuntimeEnvironment.setQualifiers("port");
        // Changing resources alone leaves the horizontal holder attached with the same view type.
        assertTrue(thumbnailWidth(list.getChildAt(0)) > 0);
        GridLayoutManagerHelper.preserveScrollPosition(list, () -> {
            GridLayoutManagerHelper.reinflateViewHolders(list);
            list.setLayoutManager(new GridLayoutManager(context, 1));
        });
        layout(list);
        assertPortraitCards(list);

        // Simulate a refresh inserting a newly discovered live video into the existing feed.
        adapter.count++;
        adapter.notifyItemInserted(0);
        layout(list);
        assertPortraitCards(list);
        assertEquals(adapter, list.getAdapter());
    }

    private static int thumbnailWidth(final View card) {
        return card.findViewById(R.id.itemThumbnailContainer).getLayoutParams().width;
    }

    private static void assertPortraitCards(final RecyclerView list) {
        assertTrue(list.getChildCount() > 0);
        for (int index = 0; index < list.getChildCount(); index++) {
            // Portrait cards stretch the thumbnail between start/end constraints (0dp).
            assertEquals(0, thumbnailWidth(list.getChildAt(index)));
        }
    }

    private static void layout(final RecyclerView list) {
        list.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 720, 1600);
    }

    private static final class CardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private int count = 12;

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                          final int viewType) {
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.list_stream_card_item, parent, false)) { };
        }

        @Override
        public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder,
                                     final int position) {
            ((TextView) holder.itemView.findViewById(R.id.itemVideoTitleView))
                    .setText("Video " + position);
        }

        @Override
        public int getItemCount() {
            return count;
        }
    }
}
