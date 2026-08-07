package org.schabi.newpipe.fragments.list.sponsorblock

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.Callable
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil.Companion.showSnackbar
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockCategory
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockExtractorHelper
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment
import org.schabi.newpipe.fragments.list.sponsorblock.SponsorBlockSegmentListAdapter.SponsorBlockSegmentItemViewHolder
import org.schabi.newpipe.util.SponsorBlockHelper
import org.schabi.newpipe.util.TimeUtils

class SponsorBlockSegmentListAdapter(
    private val context: Context?,
    private val listener: SponsorBlockSegmentListAdapterListener?
) : RecyclerView.Adapter<SponsorBlockSegmentItemViewHolder?>() {
    private var sponsorBlockSegments = ArrayList<SponsorBlockSegment>()

    fun setItems(items: Array<SponsorBlockSegment>?) {
        val oldSegments = sponsorBlockSegments
        val newSegments = if (items == null) {
            ArrayList()
        } else {
            val list = ArrayList(items.toList())
            val highlightSegment =
                list
                    .stream()
                    .filter { x: SponsorBlockSegment? -> x!!.category == SponsorBlockCategory.HIGHLIGHT }
                    .findFirst()

            if (highlightSegment.isPresent) {
                list.remove(highlightSegment.get())
                list.add(0, highlightSegment.get())
            }
            list
        }

        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(
            object : androidx.recyclerview.widget.DiffUtil.Callback() {
                override fun getOldListSize(): Int = oldSegments.size
                override fun getNewListSize(): Int = newSegments.size

                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    return oldSegments[oldItemPosition].uuid == newSegments[newItemPosition].uuid
                }

                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    val old = oldSegments[oldItemPosition]
                    val new = newSegments[newItemPosition]
                    return old.uuid == new.uuid &&
                        old.category == new.category &&
                        old.startTime == new.startTime &&
                        old.endTime == new.endTime
                }
            }
        )

        sponsorBlockSegments = newSegments
        diffResult.dispatchUpdatesTo(this)
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
        val sponsorBlockSegment = sponsorBlockSegments[position]
        holder.updateFrom(sponsorBlockSegment)
    }

    override fun getItemCount(): Int {
        return sponsorBlockSegments.size
    }

    class SponsorBlockSegmentItemViewHolder(
        itemView: View,
        listener: SponsorBlockSegmentListAdapterListener?
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
            itemSegmentSkipToHighlight.setOnClickListener { v: View? ->
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
            itemSegmentStartTimeTextView.setOnClickListener { v: View? ->
                if (currentSponsorBlockSegment != null && listener != null) {
                    listener.onSkipToTimestampRequested(
                        currentSponsorBlockSegment!!.startTime.toLong()
                    )
                }
            }
            itemSegmentEndTimeTextView =
                itemView.findViewById(R.id.item_segment_end_time_textview)
            itemSegmentEndTimeTextView.setOnClickListener { v: View? ->
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
            itemSegmentVoteUpImageView.setOnClickListener { v: View? -> vote(1) }
            itemSegmentVoteUpImageView.setOnLongClickListener { v: View? ->
                vote(20)
                true
            }
            itemSegmentVoteDownImageView =
                itemView.findViewById(R.id.item_segment_vote_down_imageview)
            itemSegmentVoteDownImageView.setOnClickListener { v: View? ->
                vote(
                    0
                )
            }
            itemSegmentVoteDownImageView.setOnLongClickListener { v: View? ->
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
                SponsorBlockHelper.convertCategoryToColor(
                    sponsorBlockSegment.category,
                    context
                )
            if (segmentColor != null) {
                itemSegmentColorView.setBackgroundColor(segmentColor)
            }

            // skip to highlight
            if (sponsorBlockSegment.category == SponsorBlockCategory.HIGHLIGHT) {
                itemSegmentColorView.visibility = View.GONE
                itemSegmentSkipToHighlight.visibility = View.VISIBLE
            } else {
                itemSegmentColorView.visibility = View.VISIBLE
                itemSegmentSkipToHighlight.visibility = View.GONE
            }

            // category name
            val friendlyCategoryName =
                SponsorBlockHelper.convertCategoryToFriendlyName(
                    context,
                    sponsorBlockSegment.category
                )
            itemSegmentNameTextView.text = friendlyCategoryName

            // from
            val startText = TimeUtils.millisecondsToString(sponsorBlockSegment.startTime)
            itemSegmentStartTimeTextView.text = startText

            // to
            val endText = TimeUtils.millisecondsToString(sponsorBlockSegment.endTime)
            itemSegmentEndTimeTextView.text = endText

            // Update vote button states
            if (sponsorBlockSegment.category == SponsorBlockCategory.PENDING || sponsorBlockSegment.uuid == "TEMP" ||
                sponsorBlockSegment.uuid == ""
            ) {
                itemSegmentVoteUpImageView.visibility = View.INVISIBLE
                itemSegmentVoteDownImageView.visibility = View.INVISIBLE
            } else {
                itemSegmentVoteUpImageView.visibility = View.VISIBLE
                itemSegmentVoteDownImageView.visibility = View.VISIBLE

                // Update button states based on current vote
                val selectedColor =
                    context.getColor(R.color.sponsor_block_vote_button_selected)
                val defaultColor =
                    context.getColor(android.R.color.darker_gray)

                if (hasUpVoted) {
                    itemSegmentVoteUpImageView.setColorFilter(selectedColor)
                    itemSegmentVoteDownImageView.setColorFilter(defaultColor)
                } else if (hasDownVoted) {
                    itemSegmentVoteUpImageView.setColorFilter(defaultColor)
                    itemSegmentVoteDownImageView.setColorFilter(selectedColor)
                } else {
                    // Reset to default colors when no vote
                    itemSegmentVoteUpImageView.setColorFilter(defaultColor)
                    itemSegmentVoteDownImageView.setColorFilter(defaultColor)
                }
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

            voteSubscriber = Single.fromCallable<Response>(
                Callable {
                    isVoting = true
                    SponsorBlockExtractorHelper.submitSponsorBlockSegmentVote(
                        segmentUuid,
                        apiUrl,
                        value
                    )
                }
            )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    Consumer { response: Response ->
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
                            return@Consumer
                        }
                        Toast.makeText(
                            context,
                            toastMessage,
                            Toast.LENGTH_SHORT
                        ).show()

                        // Update button states after voting
                        if (currentSponsorBlockSegment != null) {
                            updateFrom(currentSponsorBlockSegment!!)
                        }
                    },
                    Consumer { throwable: Throwable? ->
                        if (throwable is NullPointerException) {
                            return@Consumer
                        }
                        showSnackbar(
                            context,
                            ErrorInfo(
                                throwable!!,
                                UserAction.SUBSCRIPTION_UPDATE,
                                "Submit vote for SponsorBlock segment"
                            )
                        )
                    }
                )
        }
    }
}
