package org.schabi.newpipe.util;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.util.StreamItemAdapter.StreamInfoWrapper;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A list adapter for groups of {@link AudioStream}s (audio tracks).
 */
public class AudioTrackAdapter extends BaseAdapter implements Filterable {
    private final AudioTracksWrapper tracksWrapper;
    @Nullable
    private final Context context;

    public AudioTrackAdapter(final AudioTracksWrapper tracksWrapper) {
        this(tracksWrapper, null);
    }

    public AudioTrackAdapter(final AudioTracksWrapper tracksWrapper,
                             @Nullable final Context context) {
        this.tracksWrapper = tracksWrapper;
        this.context = context;
    }

    @Override
    public int getCount() {
        return tracksWrapper.size();
    }

    @Override
    public List<AudioStream> getItem(final int position) {
        return tracksWrapper.getTracksList().get(position).getStreamsList();
    }

    @Override
    public long getItemId(final int position) {
        return position;
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        final Context parentContext = parent.getContext();
        final View view;
        if (convertView == null) {
            view = LayoutInflater.from(parentContext).inflate(
                    R.layout.stream_quality_item, parent, false);
        } else {
            view = convertView;
        }

        final ImageView woSoundIconView = view.findViewById(R.id.wo_sound_icon);
        final TextView formatNameView = view.findViewById(R.id.stream_format_name);
        final TextView qualityView = view.findViewById(R.id.stream_quality);
        final TextView sizeView = view.findViewById(R.id.stream_size);

        final List<AudioStream> streams = getItem(position);
        final AudioStream stream = streams.get(0);

        woSoundIconView.setVisibility(View.GONE);
        sizeView.setVisibility(View.VISIBLE);

        if (stream.getAudioTrackId() != null) {
            formatNameView.setText(stream.getAudioTrackId());
        }
        qualityView.setText(Localization.audioTrackName(parentContext, stream));

        return view;
    }

    @NonNull
    public CharSequence getDisplayLabel(final int position) {
        if (context == null) {
            return "";
        }

        final List<AudioStream> streams = getItem(position);
        final AudioStream stream = streams.get(0);
        final java.util.ArrayList<String> parts = new java.util.ArrayList<>(2);
        if (stream.getAudioTrackId() != null) {
            parts.add(stream.getAudioTrackId());
        }
        parts.add(Localization.audioTrackName(context, stream));
        return String.join(" • ", parts);
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(final CharSequence constraint) {
                final FilterResults filterResults = new FilterResults();
                filterResults.values = tracksWrapper.getTracksList();
                filterResults.count = getCount();
                return filterResults;
            }

            @Override
            protected void publishResults(final CharSequence constraint,
                                          final FilterResults results) {
                notifyDataSetChanged();
            }

            @Override
            public CharSequence convertResultToString(final Object resultValue) {
                final int index = tracksWrapper.getTracksList().indexOf(resultValue);
                return index >= 0 ? getDisplayLabel(index) : "";
            }
        };
    }

    public static class AudioTracksWrapper implements Serializable {
        private final List<StreamInfoWrapper<AudioStream>> tracksList;

        public AudioTracksWrapper(@NonNull final List<List<AudioStream>> groupedAudioStreams,
                                  @Nullable final Context context) {
            this.tracksList = groupedAudioStreams.stream().map(streams ->
                    new StreamInfoWrapper<>(streams, context)).collect(Collectors.toList());
        }

        public List<StreamInfoWrapper<AudioStream>> getTracksList() {
            return tracksList;
        }

        public int size() {
            return tracksList.size();
        }
    }
}
