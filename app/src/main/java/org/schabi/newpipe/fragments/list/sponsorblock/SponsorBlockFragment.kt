package org.schabi.newpipe.fragments.list.sponsorblock

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.evernote.android.state.State
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentSponsorBlockBinding
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockAction
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockCategory
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.local.sponsorblock.SponsorBlockDataManager
import org.schabi.newpipe.util.SponsorBlockHelper.convertCategoryToFriendlyName
import org.schabi.newpipe.util.SponsorBlockMode
import org.schabi.newpipe.util.TimeUtils.millisecondsToString

class SponsorBlockFragment :

    BaseFragment, CompoundButton.OnCheckedChangeListener, SponsorBlockSegmentListAdapterListener {
    @State
    var streamInfo: StreamInfo? = null
    var binding: FragmentSponsorBlockBinding? = null

    @State
    @JvmField
    var sponsorBlockMode = SponsorBlockMode.ENABLED

    @State
    @JvmField
    var isWhitelisted = false

    @State
    @JvmField
    var markedStartTime: Int? = null

    @State
    @JvmField
    var markedEndTime: Int? = null
    private var segmentListAdapter: SponsorBlockSegmentListAdapter? = null
    private var currentProgress = -1
    private var sponsorBlockFragmentListener: SponsorBlockFragmentListener? = null
    private var sponsorBlockDataManager: SponsorBlockDataManager? = null
    private var workerAddToWhitelisted: Disposable? = null
    private var workerRemoveFromWhitelisted: Disposable? = null

    constructor()

    constructor(streamInfo: StreamInfo) {
        this.streamInfo = streamInfo
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sponsorBlockDataManager = SponsorBlockDataManager(requireContext())
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        if (streamInfo == null) {
            return
        }

        segmentListAdapter = SponsorBlockSegmentListAdapter(context, this)
        segmentListAdapter!!.setItems(streamInfo!!.sponsorBlockSegments)
    }

    override fun onDetach() {
        super.onDetach()

        if (workerAddToWhitelisted != null) {
            workerAddToWhitelisted!!.dispose()
        }
        if (workerRemoveFromWhitelisted != null) {
            workerRemoveFromWhitelisted!!.dispose()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (sponsorBlockDataManager != null) {
            sponsorBlockDataManager = SponsorBlockDataManager(requireContext())
        }

        binding = FragmentSponsorBlockBinding.inflate(inflater, container, false)

        binding!!.sponsorBlockControlsMarkSegmentStart.setOnClickListener {
            doMarkPendingSegment(
                true
            )
        }
        binding!!.sponsorBlockControlsMarkSegmentEnd.setOnClickListener {
            doMarkPendingSegment(
                false
            )
        }
        binding!!.sponsorBlockControlsSegmentStart.setOnClickListener {
            doPendingSegmentSeek(
                true
            )
        }
        binding!!.sponsorBlockControlsSegmentEnd.setOnClickListener {
            doPendingSegmentSeek(
                false
            )
        }
        binding!!.sponsorBlockControlsClearSegment.setOnClickListener { doClearPendingSegment() }
        binding!!.sponsorBlockControlsSubmitSegment.setOnClickListener { doSubmitPendingSegment() }

        binding!!.segmentList.setAdapter(segmentListAdapter)

        binding!!.skippingIsEnabledSwitch.setOnCheckedChangeListener(this)
        binding!!.channelIsWhitelistedSwitch.setOnCheckedChangeListener(this)

        updateSponsorBlockModeUI()
        updateIsWhitelistedUI()

        return binding!!.getRoot()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        if (buttonView.id == R.id.skipping_is_enabled_switch) {
            if (sponsorBlockFragmentListener != null) {
                sponsorBlockFragmentListener!!.onSkippingEnabledChanged(isChecked)
            }
        } else if (buttonView.id == R.id.channel_is_whitelisted_switch) {
            val context = requireContext()

            val toastText: String

            if (isChecked) {
                toastText = context.getString(
                    R.string.sponsor_block_uploader_added_to_whitelist_toast
                )

                workerAddToWhitelisted =
                    sponsorBlockDataManager!!.addToWhitelist(streamInfo!!.uploaderName)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ result ->
                            Toast.makeText(context, toastText, Toast.LENGTH_LONG).show()
                        }, { error -> })
            } else {
                toastText = context.getString(
                    R.string.sponsor_block_uploader_removed_from_whitelist_toast
                )

                workerRemoveFromWhitelisted =
                    sponsorBlockDataManager!!.removeFromWhitelist(streamInfo!!.uploaderName)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            Toast.makeText(context, toastText, Toast.LENGTH_LONG).show()
                        }, { error -> })
            }

            binding?.skippingIsEnabledSwitch?.isChecked = false
            binding?.skippingIsEnabledSwitch?.isEnabled = !isChecked
        }
    }

    fun setListener(listener: SponsorBlockFragmentListener?) {
        sponsorBlockFragmentListener = listener
    }

    fun setSponsorBlockMode(mode: SponsorBlockMode) {
        sponsorBlockMode = mode
        updateSponsorBlockModeUI()
    }

    fun setIsWhitelisted(value: Boolean) {
        isWhitelisted = value
        updateIsWhitelistedUI()
    }

    private fun updateSponsorBlockModeUI() {
        binding?.skippingIsEnabledSwitch?.let {
            it.setOnCheckedChangeListener(null)
            it.isChecked = sponsorBlockMode == SponsorBlockMode.ENABLED
            it.setOnCheckedChangeListener(this)
        }
    }

    private fun updateIsWhitelistedUI() {
        binding?.channelIsWhitelistedSwitch?.let {
            it.setOnCheckedChangeListener(null)
            it.isChecked = isWhitelisted
            it.setOnCheckedChangeListener(this)
        }
    }

    fun setCurrentProgress(progress: Int) {
        currentProgress = progress
    }

    @SuppressLint("SetTextI18n")
    fun clearPendingSegment() {
        markedStartTime = null
        markedEndTime = null

        binding?.sponsorBlockControlsSegmentStart?.text = "00:00:00"
        binding?.sponsorBlockControlsSegmentEnd?.text = "00:00:00"

        if (sponsorBlockFragmentListener != null) {
            sponsorBlockFragmentListener!!.onRequestClearPendingSegment()
        }
    }

    fun refreshSponsorBlockSegments() {
        if (segmentListAdapter == null) {
            return
        }

        segmentListAdapter!!.setItems(streamInfo!!.sponsorBlockSegments)
    }

    private fun doMarkPendingSegment(isStart: Boolean) {
        if (currentProgress < 0) {
            return
        }

        if (isStart) {
            if (markedEndTime != null && currentProgress > markedEndTime!!) {
                Toast.makeText(
                    context,
                    getString(R.string.sponsor_block_invalid_start_toast),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            markedStartTime = currentProgress
        } else {
            if (markedStartTime != null && currentProgress < markedStartTime!!) {
                Toast.makeText(
                    context,
                    getString(R.string.sponsor_block_invalid_end_toast),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            markedEndTime = currentProgress
        }

        if (markedStartTime != null) {
            binding?.sponsorBlockControlsSegmentStart?.text = millisecondsToString(markedStartTime!!.toDouble())
        }

        if (markedEndTime != null) {
            binding?.sponsorBlockControlsSegmentEnd?.text = millisecondsToString(markedEndTime!!.toDouble())
        }

        if (markedStartTime != null && markedEndTime != null) {
            if (sponsorBlockFragmentListener != null) {
                sponsorBlockFragmentListener!!.onRequestNewPendingSegment(
                    markedStartTime!!,
                    markedEndTime!!
                )
            }
        }

        val message = if (isStart) {
            getString(R.string.sponsor_block_marked_start_toast)
        } else {
            getString(R.string.sponsor_block_marked_end_toast)
        }
        Toast.makeText(
            context,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    @SuppressLint("SetTextI18n")
    private fun doClearPendingSegment() {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.sponsor_block_clear_marked_segment_prompt)
            .setNegativeButton(
                R.string.cancel
            ) { dialog: DialogInterface?, which: Int -> dialog!!.dismiss() }
            .setPositiveButton(
                R.string.yes
            ) { dialog: DialogInterface?, which: Int ->
                clearPendingSegment()
                dialog!!.dismiss()
            }
            .show()
    }

    private fun doPendingSegmentSeek(isStart: Boolean) {
        if (isStart && markedStartTime != null) {
            onSkipToTimestampRequested(markedStartTime!!.toLong())
        } else if (markedEndTime != null) {
            onSkipToTimestampRequested(markedEndTime!!.toLong())
        }
    }

    private fun doSubmitPendingSegment() {
        val context = requireContext()

        if (markedStartTime == null || markedEndTime == null) {
            Toast.makeText(
                context,
                getString(R.string.sponsor_block_missing_times_toast),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val builder = AlertDialog.Builder(context)
        builder.setTitle(R.string.sponsor_block_select_a_category)
        builder.setNegativeButton(
            R.string.cancel
        ) { dialog: DialogInterface?, which: Int -> dialog!!.dismiss() }
        builder.setItems(
            arrayOf(
                convertCategoryToFriendlyName(
                    context,
                    SponsorBlockCategory.SPONSOR
                ),
                convertCategoryToFriendlyName(
                    context,
                    SponsorBlockCategory.INTRO
                ),
                convertCategoryToFriendlyName(
                    context,
                    SponsorBlockCategory.OUTRO
                ),
                convertCategoryToFriendlyName(
                    context,
                    SponsorBlockCategory.INTERACTION
                ),
                convertCategoryToFriendlyName(
                    context,
                    SponsorBlockCategory.HIGHLIGHT
                ),
                convertCategoryToFriendlyName(
                    context,
                    SponsorBlockCategory.SELF_PROMO
                ),
                convertCategoryToFriendlyName(
                    context,
                    SponsorBlockCategory.NON_MUSIC
                ),
                convertCategoryToFriendlyName(
                    context,
                    SponsorBlockCategory.PREVIEW
                ),
                convertCategoryToFriendlyName(
                    context,
                    SponsorBlockCategory.FILLER
                )
            )
        ) { dialog: DialogInterface?, which: Int ->
            val category: SponsorBlockCategory = SponsorBlockCategory.entries[which]
            val action = if (category == SponsorBlockCategory.HIGHLIGHT) {
                SponsorBlockAction.POI
            } else {
                SponsorBlockAction.SKIP
            }
            val newSegment =
                SponsorBlockSegment(
                    "",
                    markedStartTime!!.toDouble(),
                    markedEndTime!!.toDouble(),
                    category,
                    action
                )
            if (sponsorBlockFragmentListener != null) {
                sponsorBlockFragmentListener!!.onRequestSubmitPendingSegment(newSegment)
            }
            dialog!!.dismiss()
        }
        builder.show()
    }

    override fun onSkipToTimestampRequested(positionMillis: Long) {
        if (sponsorBlockFragmentListener != null) {
            sponsorBlockFragmentListener!!.onSeekToRequested(positionMillis)
        }
    }
}
