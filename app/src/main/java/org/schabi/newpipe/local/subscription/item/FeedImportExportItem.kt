package org.schabi.newpipe.local.subscription.item

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.xwray.groupie.viewbinding.BindableItem
import com.xwray.groupie.viewbinding.GroupieViewHolder
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FeedImportExportGroupBinding
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.ktx.animateRotation
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.views.CollapsibleView

class FeedImportExportItem(
    private val onImportPreviousSelected: () -> Unit,
    private val onImportFromServiceSelected: (Int) -> Unit,
    private val onBackupSelected: () -> Unit,
    private val onExportSelected: () -> Unit,
    var isExpanded: Boolean = false
) : BindableItem<FeedImportExportGroupBinding>() {

    private var expandIconListener: CollapsibleView.StateListener? = null

    override fun getLayout(): Int = R.layout.feed_import_export_group

    override fun getSpanSize(spanCount: Int, position: Int): Int = spanCount

    override fun bind(viewBinding: FeedImportExportGroupBinding, position: Int) {
        if (viewBinding.importFromOptions.childCount == 0) {
            setupImportFromItems(viewBinding.importFromOptions)
        }
        if (viewBinding.exportToOptions.childCount == 0) {
            setupExportToItems(viewBinding.exportToOptions)
        }

        expandIconListener?.let { viewBinding.importExportOptions.removeListener(it) }
        expandIconListener = CollapsibleView.StateListener { newState ->
            viewBinding.importExportExpandIcon.animateRotation(
                250,
                if (newState == CollapsibleView.COLLAPSED) 0 else 180
            )
        }

        viewBinding.importExportOptions.currentState = if (isExpanded) {
            CollapsibleView.EXPANDED
        } else {
            CollapsibleView.COLLAPSED
        }
        viewBinding.importExportExpandIcon.rotation = if (isExpanded) 180F else 0F
        viewBinding.importExportOptions.ready()

        viewBinding.importExportOptions.addListener(expandIconListener)
        viewBinding.importExport.setOnClickListener {
            viewBinding.importExportOptions.switchState()
            isExpanded =
                viewBinding.importExportOptions.currentState == CollapsibleView.EXPANDED
        }
    }

    override fun unbind(viewHolder: GroupieViewHolder<FeedImportExportGroupBinding>) {
        super.unbind(viewHolder)
        expandIconListener?.let { viewHolder.binding.importExportOptions.removeListener(it) }
        expandIconListener = null
    }

    override fun initializeViewBinding(view: View): FeedImportExportGroupBinding {
        return FeedImportExportGroupBinding.bind(view)
    }

    private fun addItemView(
        title: String,
        icon: Int,
        container: ViewGroup
    ): View {
        val itemRoot = View.inflate(
            container.context,
            R.layout.subscription_import_export_item,
            null
        )
        val titleView = itemRoot.findViewById<TextView>(android.R.id.text1)
        val iconView = itemRoot.findViewById<ImageView>(android.R.id.icon1)

        titleView.text = title
        iconView.setImageResource(icon)

        container.addView(itemRoot)
        return itemRoot
    }

    private fun setupImportFromItems(listHolder: ViewGroup) {
        addItemView(
            listHolder.context.getString(R.string.previous_export),
            R.drawable.ic_backup,
            listHolder
        ).setOnClickListener { onImportPreviousSelected() }

        ServiceList.all().forEach { service ->
            val subscriptionExtractor = service.subscriptionExtractor ?: return@forEach
            if (subscriptionExtractor.supportedSources.isEmpty()) {
                return@forEach
            }

            addItemView(
                service.serviceInfo.name,
                ServiceHelper.getIcon(service.serviceId),
                listHolder
            ).setOnClickListener {
                onImportFromServiceSelected(service.serviceId)
            }
        }
    }

    private fun setupExportToItems(listHolder: ViewGroup) {
        addItemView(
            listHolder.context.getString(R.string.file),
            R.drawable.ic_save,
            listHolder
        ).setOnClickListener { onExportSelected() }

        addItemView(
            listHolder.context.getString(R.string.settings_category_backup_restore_title),
            R.drawable.ic_settings_backup_restore,
            listHolder
        ).setOnClickListener { onBackupSelected() }
    }
}
