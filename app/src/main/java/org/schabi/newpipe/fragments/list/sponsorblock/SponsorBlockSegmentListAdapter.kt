package org.schabi.newpipe.fragments.list.sponsorblock

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil.Companion.showSnackbar
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockCategory
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockExtractorHelper
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment
import org.schabi.newpipe.fragments.list.sponsorblock.SponsorBlockSegmentListAdapter.SponsorBlockSegmentItemViewHolder
import org.schabi.newpipe.util.SponsorBlockHelper.convertCategoryToColor
import org.schabi.newpipe.util.SponsorBlockHelper.convertCategoryToFriendlyName
import org.schabi.newpipe.util.TimeUtils.millisecondsToString

class SponsorBlockSegmentListAdapter(
    private val context: Context?,
    private val listener: SponsorBlockSegmentListAdapterListener?
) : ListAdapter<SponsorBlockSegment, SponsorBlockSegmentItemViewHolder>(
    SponsorBlockSegmentDiffCallback()
) {
    fun setItems(items: Array<SponsorBlockSegment?>?) {
        val list = items?.filterNotNull()?.toMutableList() ?: mutableListOf()

        // find the first "highlight" segment (if it exists) and move it to the top
        if (list.isNotEmpty()) {
            val highlightSegment = list.find { it.category == SponsorBlockCategory.HIGHLIGHT }

            if (highlightSegment != null) {
                list.remove(highlightSegment)
                list.add(0, highlightSegment)
            }
        }

        submitList(list)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SponsorBlockSegmentItemViewHolder {
        val itemView = LayoutInflater
            .from(context)
            .inflate(R.layout.list_segments_item, parent, false)
        return SponsorBlockSegmentItemViewHolder(itemView, listener)
    }

    override fun onBindViewHolder(
        holder: SponsorBlockSegmentItemViewHolder,
        position: Int
    ) {
        val sponsorBlockSegment = getItem(position)
        holder.updateFrom(sponsorBlockSegment)
    }

    class SponsorBlockSegmentItemViewHolder(
        itemView: View,
        private val listener: SponsorBlockSegmentListAdapterListener?
    ) : RecyclerView.ViewHolder(itemView) {
        private val itemSegmentColorView: View = itemView.findViewById(R.id.item_segment_color_view)
        private val itemSegmentSkipToHighlight: ImageView = itemView.findViewById(R.id.item_segment_skip_to_highlight)
        private val itemSegmentNameTextView: TextView
        private val itemSegmentStartTimeTextView: TextView
        private val itemSegmentEndTimeTextView: TextView
        private val itemSegmentVoteUpImageView: ImageView
        private val itemSegmentVoteDownImageView: ImageView
        private var voteSubscriber: Disposable? = null
        private var segmentUuid: String? = null
        private var isVoting = false
        private var hasUpVoted = false
        private var hasDownVoted = false
        private var hasResetVote = false
        private var currentSponsorBlockSegment: SponsorBlockSegment? = null

        init {
            itemSegmentSkipToHighlight.setOnClickListener {
                if (currentSponsorBlockSegment != null && listener != null) {
                    listener.onSkipToTimestampRequested(
                        currentSponsorBlockSegment!!.startTime.toLong()
                    )
                }
            }
            itemSegmentNameTextView = itemView.findViewById(
                R.id.item_segment_category_name_textview
            )
            itemSegmentStartTimeTextView = itemView.findViewById(
                R.id.item_segment_start_time_textview
            )
            itemSegmentStartTimeTextView.setOnClickListener {
                if (currentSponsorBlockSegment != null && listener != null) {
                    listener.onSkipToTimestampRequested(
                        currentSponsorBlockSegment!!.startTime.toLong()
                    )
                }
            }
            itemSegmentEndTimeTextView =
                itemView.findViewById(R.id.item_segment_end_time_textview)
            itemSegmentEndTimeTextView.setOnClickListener {
                if (currentSponsorBlockSegment != null && listener != null) {
                    listener.onSkipToTimestampRequested(currentSponsorBlockSegment!!.endTime.toLong())
                }
            }

            // voting:
            //   1 = up
            //   0 = down
            //   20 = reset
            itemSegmentVoteUpImageView =
                itemView.findViewById(R.id.item_segment_vote_up_imageview)
            itemSegmentVoteUpImageView.setOnClickListener { vote(1) }
            itemSegmentVoteUpImageView.setOnLongClickListener {
                vote(20)
                true
            }
            itemSegmentVoteDownImageView =
                itemView.findViewById(R.id.item_segment_vote_down_imageview)
            itemSegmentVoteDownImageView.setOnClickListener {
                vote(
                    0
                )
            }
            itemSegmentVoteDownImageView.setOnLongClickListener {
                vote(20)
                true
            }
        }

        fun updateFrom(sponsorBlockSegment: SponsorBlockSegment) {
            currentSponsorBlockSegment = sponsorBlockSegment

            val context = itemView.context

            // uuid
            segmentUuid = sponsorBlockSegment.uuid

            // category color
            val segmentColor =
                convertCategoryToColor(
                    sponsorBlockSegment.category,
                    context
                )
            itemSegmentColorView.setBackgroundColor(segmentColor)

            // skip to highlight
            if (sponsorBlockSegment.category == SponsorBlockCategory.HIGHLIGHT) {
                itemSegmentColorView.visibility = View.GONE
                itemSegmentSkipToHighlight.setVisibility(View.VISIBLE)
            } else {
                itemSegmentColorView.visibility = View.VISIBLE
                itemSegmentSkipToHighlight.setVisibility(View.GONE)
            }

            // category name
            val friendlyCategoryName =
                convertCategoryToFriendlyName(
                    context,
                    sponsorBlockSegment.category
                )
            itemSegmentNameTextView.text = friendlyCategoryName

            // from
            val startText = millisecondsToString(sponsorBlockSegment.startTime)
            itemSegmentStartTimeTextView.text = startText

            // to
            val endText = millisecondsToString(sponsorBlockSegment.endTime)
            itemSegmentEndTimeTextView.text = endText

            if (sponsorBlockSegment.category == SponsorBlockCategory.PENDING || sponsorBlockSegment.uuid == "TEMP" ||
                sponsorBlockSegment.uuid == ""
            ) {
                itemSegmentVoteUpImageView.setVisibility(View.INVISIBLE)
                itemSegmentVoteDownImageView.setVisibility(View.INVISIBLE)
            }
        }

        private fun vote(value: Int) {
            if (segmentUuid == null) {
                return
            }

            if (isVoting) {
                return
            }

            if (voteSubscriber != null) {
                voteSubscriber!!.dispose()
            }

            // these 3 checks prevent the user from continuously spamming votes
            // (not entirely sure if we need this)
            if (value == 0 && hasDownVoted) {
                return
            }

            if (value == 1 && hasUpVoted) {
                return
            }

            if (value == 20 && hasResetVote) {
                return
            }

            val context = itemView.context

            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val apiUrl = prefs.getString(
                context
                    .getString(R.string.sponsor_block_api_url_key),
                null
            )
            if (apiUrl.isNullOrEmpty()) {
                return
            }

            voteSubscriber = Single.fromCallable {
                isVoting = true
                SponsorBlockExtractorHelper.submitSponsorBlockSegmentVote(
                    segmentUuid,
                    apiUrl,
                    value
                )
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ response ->
                    isVoting = false
                    var toastMessage: String
                    if (response.responseCode() != 200) {
                        toastMessage = response.responseMessage()
                        if (toastMessage == "") {
                            toastMessage = "Error " + response.responseCode()
                        }
                    } else if (value == 0) {
                        hasDownVoted = true
                        hasUpVoted = false
                        hasResetVote = false
                        toastMessage = context.getString(
                            R.string.sponsor_block_segment_voted_down_toast
                        )
                    } else if (value == 1) {
                        hasDownVoted = false
                        hasUpVoted = true
                        hasResetVote = false
                        toastMessage = context.getString(
                            R.string.sponsor_block_segment_voted_up_toast
                        )
                    } else if (value == 20) {
                        hasDownVoted = false
                        hasUpVoted = false
                        hasResetVote = true
                        toastMessage = context.getString(
                            R.string.sponsor_block_segment_reset_vote_toast
                        )
                    } else {
                        return@subscribe
                    }
                    Toast.makeText(
                        context,
                        toastMessage,
                        Toast.LENGTH_SHORT
                    ).show()
                }, { throwable ->
                    if (throwable is NullPointerException) {
                        return@subscribe
                    }
                    showSnackbar(
                        context,
                        ErrorInfo(
                            throwable,
                            UserAction.SUBSCRIPTION_UPDATE,
                            "Submit vote for SponsorBlock segment"
                        )
                    )
                })
        }
    }
}

private class SponsorBlockSegmentDiffCallback : DiffUtil.ItemCallback<SponsorBlockSegment>() {
    override fun areItemsTheSame(
        oldItem: SponsorBlockSegment,
        newItem: SponsorBlockSegment
    ): Boolean {
        return oldItem.uuid == newItem.uuid
    }

    override fun areContentsTheSame(
        oldItem: SponsorBlockSegment,
        newItem: SponsorBlockSegment
    ): Boolean {
        return oldItem.category == newItem.category &&
            oldItem.startTime == newItem.startTime &&
            oldItem.endTime == newItem.endTime
    }
}
