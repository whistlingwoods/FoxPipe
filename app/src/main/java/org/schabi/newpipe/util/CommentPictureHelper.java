package org.schabi.newpipe.util;

import android.content.Context;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.util.image.CoilHelper;
import org.schabi.newpipe.util.image.ImageStrategy;

import java.util.Collection;
import java.util.Collections;

public final class CommentPictureHelper {
    private static final int MAX_PICTURE_COUNT = 4;

    private CommentPictureHelper() {
    }

    public static void bindCommentPictures(@NonNull final HorizontalScrollView scrollView,
                                           @NonNull final LinearLayout container,
                                           @NonNull final Collection<Image> pictures) {
        container.removeAllViews();

        if (!ImageStrategy.shouldLoadImages() || pictures.isEmpty()) {
            scrollView.setVisibility(View.GONE);
            return;
        }

        final Context context = container.getContext();
        final int size = context.getResources().getDimensionPixelSize(R.dimen.comment_picture_size);
        final int spacing = context.getResources()
                .getDimensionPixelSize(R.dimen.comment_picture_spacing);

        int index = 0;
        for (final Image picture : pictures) {
            container.addView(createImageView(context, picture, size, spacing, index > 0));
            index++;
            if (index >= MAX_PICTURE_COUNT) {
                break;
            }
        }

        scrollView.setVisibility(index > 0 ? View.VISIBLE : View.GONE);
    }

    @NonNull
    private static ImageView createImageView(@NonNull final Context context,
                                             @NonNull final Image picture,
                                             final int size,
                                             final int spacing,
                                             final boolean addStartMargin) {
        final ImageView imageView = new ImageView(context);
        final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(size, size);
        if (addStartMargin) {
            layoutParams.setMarginStart(spacing);
        }
        imageView.setLayoutParams(layoutParams);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setAdjustViewBounds(true);
        imageView.setOnClickListener(view -> ShareUtils.openUrlInBrowser(
                context, picture.getUrl()));
        CoilHelper.INSTANCE.loadThumbnail(imageView, Collections.singletonList(picture));
        return imageView;
    }
}
