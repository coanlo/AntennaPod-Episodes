package de.danoeh.antennapod.ui.discovery;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.FitCenter;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import de.danoeh.antennapod.net.discovery.EpisodeSearchResult;
import de.danoeh.antennapod.ui.common.Converter;
import de.danoeh.antennapod.ui.common.DateFormatter;
import de.danoeh.antennapod.ui.common.ImagePlaceholder;

import java.util.Date;
import java.util.List;

public class EpisodeSearchAdapter extends ArrayAdapter<EpisodeSearchResult> {
    private static final long ONE_MINUTE_MS = 60L * 1000L;
    private final Context context;
    private final List<EpisodeSearchResult> data;

    public EpisodeSearchAdapter(Context context, List<EpisodeSearchResult> objects) {
        super(context, 0, objects);
        this.context = context;
        this.data = objects;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        EpisodeSearchResult episode = data.get(position);
        EpisodeViewHolder viewHolder;
        View view;
        if (convertView == null) {
            view = View.inflate(context, R.layout.online_episode_search_listitem, null);
            viewHolder = new EpisodeViewHolder(view);
            view.setTag(viewHolder);
        } else {
            view = convertView;
            viewHolder = (EpisodeViewHolder) view.getTag();
        }

        viewHolder.titleView.setText(episode.title);
        if (episode.podcastTitle != null && !episode.podcastTitle.trim().isEmpty()) {
            viewHolder.podcastView.setText(episode.podcastTitle);
            viewHolder.podcastView.setVisibility(View.VISIBLE);
        } else {
            viewHolder.podcastView.setVisibility(View.GONE);
        }

        Date releaseDate = episode.getReleaseDate();
        String dateText = releaseDate != null ? DateFormatter.formatAbbrev(context, releaseDate) : "";
        String durationText = formatDuration(episode.durationMs);
        String metaText = joinMetadata(dateText, durationText);
        if (!metaText.isEmpty()) {
            viewHolder.dateView.setText(metaText);
            String accessibleDate = releaseDate != null ? DateFormatter.formatForAccessibility(releaseDate) : "";
            viewHolder.dateView.setContentDescription(joinMetadata(accessibleDate, durationText));
            viewHolder.dateView.setVisibility(View.VISIBLE);
        } else {
            viewHolder.dateView.setVisibility(View.GONE);
        }

        float radius = 4 * context.getResources().getDisplayMetrics().density;
        Glide.with(context)
                .load(episode.imageUrl)
                .apply(new RequestOptions()
                        .placeholder(ImagePlaceholder.getDrawable(context, radius))
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .transform(new FitCenter(), new RoundedCorners((int) radius))
                        .dontAnimate())
                .into(viewHolder.coverView);

        return view;
    }

    private String formatDuration(long durationMs) {
        if (durationMs <= 0L) {
            return "";
        }
        if (durationMs < ONE_MINUTE_MS) {
            return Converter.getDurationStringShort((int) durationMs, false);
        }
        return Converter.getDurationStringLocalized(context, durationMs);
    }

    private static String joinMetadata(String first, String second) {
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty()) {
            return first;
        }
        return first + " · " + second;
    }

    static class EpisodeViewHolder {
        final ImageView coverView;
        final TextView titleView;
        final TextView podcastView;
        final TextView dateView;

        EpisodeViewHolder(View view) {
            coverView = view.findViewById(R.id.imgvCover);
            titleView = view.findViewById(R.id.txtvTitle);
            podcastView = view.findViewById(R.id.txtvPodcast);
            dateView = view.findViewById(R.id.txtvDate);
        }
    }
}
