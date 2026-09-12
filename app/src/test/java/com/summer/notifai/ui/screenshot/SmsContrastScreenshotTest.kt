package com.summer.notifai.ui.screenshot

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.android.material.color.MaterialColors
import com.summer.core.data.local.entities.SenderType
import com.summer.core.ui.model.SmsClassificationType
import com.summer.notifai.R
import com.summer.notifai.databinding.ItemReceivedSmsMessageBinding
import com.summer.notifai.databinding.ItemReceivedSearchSmsMessageBinding
import com.summer.notifai.databinding.ItemSentSmsMessageBinding
import com.summer.notifai.databinding.ItemSentSearchSmsMessageBinding
import com.summer.notifai.databinding.ItemSmsContactBinding
import com.summer.notifai.ui.datamodel.ContactMessageInfoDataModel
import com.summer.notifai.ui.datamodel.SmsClassificationDataModel
import com.summer.notifai.ui.datamodel.SmsMessageDataModel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
class SmsContrastScreenshotTest {
    @Test fun conversationListLight() = captureConversationList()

    @Test
    @Config(qualifiers = "w360dp-h780dp-night-xxhdpi")
    fun conversationListDark() = captureConversationList()

    @Test
    @Config(qualifiers = "w320dp-h700dp-xxhdpi")
    fun conversationListNarrow() = captureConversationList()

    @Test fun messageThreadLight() = captureMessageThread()

    @Test
    @Config(qualifiers = "w360dp-h780dp-night-xxhdpi")
    fun messageThreadDark() = captureMessageThread()

    @Test fun messageThreadLargeFont() = captureMessageThread(fontScale = 1.4f)

    @Test fun searchContactsAndBlockedLight() = captureSecondarySmsSurfaces()

    @Test
    @Config(qualifiers = "w360dp-h780dp-night-xxhdpi")
    fun searchContactsAndBlockedDark() = captureSecondarySmsSurfaces()

    private fun captureConversationList() {
        val activity = ScreenshotTestHost.activity()
        val screen = ScreenshotTestHost.inflate(activity, R.layout.frag_sms_contact_list)
        screen.findViewById<RecyclerView>(R.id.rv_fragContactList).apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = SyntheticConversationAdapter()
        }
        screen.findViewById<View>(R.id.chipImportant).performClick()
        screen.captureRoboImage()
    }

    private fun captureMessageThread(fontScale: Float = 1f) {
        val activity = ScreenshotTestHost.activity(fontScale)
        val root = surfaceColumn(activity)
        root.addView(sectionLabel(activity, "Synthetic Bank"))
        root.addView(sectionLabel(activity, "Today"))
        root.addView(receivedMessage(activity))
        root.addView(sentMessage(activity))
        root.addView(receivedMessage(activity, long = true))
        addComposerAndSelectionControls(activity, root)
        activity.setContentView(root)
        root.captureRoboImage()
    }

    private fun captureSecondarySmsSurfaces() {
        val activity = ScreenshotTestHost.activity()
        val root = surfaceColumn(activity)
        root.addView(sectionLabel(activity, "Search results"))
        root.addView(ItemReceivedSearchSmsMessageBinding.inflate(LayoutInflater.from(activity)).apply {
            model = searchMessage(4, true, "Synthetic Bank", "Synthetic incoming search result", "10:42 AM")
            executePendingBindings()
            tvItemSmsMessageMessage.text = "Synthetic incoming search result"
        }.root)
        root.addView(ItemSentSearchSmsMessageBinding.inflate(LayoutInflater.from(activity)).apply {
            model = searchMessage(5, false, "Synthetic Contact", "Synthetic outgoing search result", "10:45 AM")
            executePendingBindings()
            tvItemSmsMessageMessage.text = "Synthetic outgoing search result"
        }.root)
        root.addView(sectionLabel(activity, "Contacts"))
        root.addView(inflateAndLabel(activity, root, R.layout.item_new_contact, mapOf(
            R.id.tv_itemNewContact_senderName to "Synthetic Contact",
            R.id.tv_itemNewContact_senderAddress to "+00 00000 00000",
        )))
        root.addView(sectionLabel(activity, "Blocked senders"))
        root.addView(inflateAndLabel(activity, root, R.layout.item_blocked_sender, mapOf(
            R.id.tv_itemNewContact_senderName to "Synthetic Blocked Sender",
            R.id.tv_itemNewContact_senderAddress to "LOCAL-ONLY",
        )))
        activity.setContentView(root)
        root.captureRoboImage()
    }

    private fun surfaceColumn(activity: Activity) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(16, 16, 16, 16)
        setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface))
    }

    private fun sectionLabel(activity: Activity, text: String) =
        LayoutInflater.from(activity).inflate(R.layout.item_sms_date_header, null, false).also {
            it.findViewById<TextView>(R.id.tvHeaderDate).text = text
        }

    private fun receivedMessage(activity: Activity, long: Boolean = false): View {
        val binding = ItemReceivedSmsMessageBinding.inflate(LayoutInflater.from(activity))
        binding.model = message(
            id = if (long) 3 else 1,
            incoming = true,
            text = if (long) {
                "A deliberately long synthetic message verifies wrapping and readable contrast without real SMS data."
            } else {
                "INR 425 was debited from synthetic account."
            },
            date = "10:42 AM",
        )
        binding.executePendingBindings()
        return binding.root
    }

    private fun sentMessage(activity: Activity): View {
        val binding = ItemSentSmsMessageBinding.inflate(LayoutInflater.from(activity))
        binding.model = message(2, incoming = false, text = "Synthetic reply", date = "10:45 AM")
        binding.executePendingBindings()
        return binding.root
    }

    private fun message(id: Long, incoming: Boolean, text: String, date: String) = SmsMessageDataModel(
        id = id,
        androidSmsId = null,
        message = text,
        dateInEpoch = 1_781_002_800_000L,
        date = date,
        isIncoming = incoming,
        status = 100,
        smsClassificationDataModel = SmsClassificationDataModel("Transaction", SmsClassificationType.TRANSACTION),
    )

    private fun searchMessage(id: Long, incoming: Boolean, sender: String, text: String, date: String) =
        com.summer.notifai.ui.datamodel.SearchSmsMessageDataModel(
            id = id,
            senderAddressId = id,
            senderAddress = sender,
            message = text,
            dateInEpoch = 1_781_002_800_000L,
            date = date,
            isIncoming = incoming,
            smsClassificationDataModel = SmsClassificationDataModel("Transaction", SmsClassificationType.TRANSACTION),
        )

    private fun addComposerAndSelectionControls(activity: Activity, root: LinearLayout) {
        val inbox = LayoutInflater.from(activity).inflate(R.layout.frag_sms_inbox, root, false)
        val selected = inbox.findViewById<HorizontalScrollView>(R.id.sv_fragSmsInbox_selectedSection)
        (selected.parent as ViewGroup).removeView(selected)
        selected.visibility = View.VISIBLE
        selected.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        selected.findTextViews().firstOrNull { it.text.isNullOrBlank() }?.text = "1 selected"
        root.addView(selected)

        val composer = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val edit = inbox.findViewById<EditText>(R.id.et_fragSmsInbox_message)
        val send = inbox.findViewById<View>(R.id.bt_fragSmsInbox_send)
        (edit.parent as ViewGroup).removeView(edit)
        (send.parent as ViewGroup).removeView(send)
        edit.setText("Synthetic draft")
        edit.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        send.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        composer.addView(edit)
        composer.addView(send)
        root.addView(composer)
    }

    private fun inflateAndLabel(
        activity: Activity,
        parent: ViewGroup,
        layout: Int,
        labels: Map<Int, String>,
    ) = LayoutInflater.from(activity).inflate(layout, parent, false).also { view ->
        labels.forEach { (id, text) -> view.findViewById<TextView>(id).text = text }
    }

    private fun View.findTextViews(): List<TextView> {
        val matches = mutableListOf<TextView>()
        if (this is TextView) matches += this
        if (this is ViewGroup) {
            repeat(childCount) { matches += getChildAt(it).findTextViews() }
        }
        return matches
    }

    private class SyntheticConversationAdapter :
        RecyclerView.Adapter<SyntheticConversationAdapter.Holder>() {
        private val rows = listOf(
            ContactMessageInfoDataModel(
                1, "Synthetic Bank", "LOCAL-BANK", "INR 425 was debited from synthetic account.",
                "10:42 AM", "2", SenderType.BUSINESS,
            ),
            ContactMessageInfoDataModel(
                2, "Synthetic Contact", "+00 00000 00000", "A local-only synthetic conversation preview.",
                "Yesterday", null, SenderType.CONTACT,
            ),
            ContactMessageInfoDataModel(
                3, "A deliberately long synthetic sender name", "LOCAL-LONG",
                "Long synthetic preview text verifies ellipsis and contrast without reading Room.",
                "02 Jun", null, SenderType.BUSINESS,
            ),
        )

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(DataBindingUtil.inflate(LayoutInflater.from(parent.context), R.layout.item_sms_contact, parent, false))

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.binding.model = rows[position]
            holder.binding.executePendingBindings()
        }

        class Holder(val binding: ItemSmsContactBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
