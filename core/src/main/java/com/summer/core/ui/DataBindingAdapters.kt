package com.summer.core.ui

import android.text.Html
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.databinding.BindingAdapter
import androidx.databinding.InverseBindingAdapter
import androidx.databinding.InverseBindingListener
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.summer.core.R
import com.summer.core.data.local.entities.SenderType
import com.summer.core.ui.model.SmsClassificationType

object DataBindingAdapters {

    @JvmStatic
    @BindingAdapter("selectedMessageCount")
    fun AppCompatTextView.setSelectedMessageCount(count: Int) {
        isVisible = count > 0
        if (count > 0) {
            text = String.format(context.getString(R.string.d_selected), count)
        }
    }

    @JvmStatic
    @BindingAdapter("htmlText")
    fun AppCompatTextView.setHtmlText(html: String?) {
        html?.let {
            text = Html.fromHtml(it, Html.FROM_HTML_MODE_COMPACT)
        }
    }

    @JvmStatic
    @BindingAdapter("setDrawableRes")
    fun AppCompatImageView.setDrawableRes(@DrawableRes drawable: Int) {
        setImageResource(drawable)
    }

    @JvmStatic
    @BindingAdapter(
        "app:layout_marginAll",
        "app:layout_marginHorizontal",
        "app:layout_marginVertical",
        "app:layout_marginTop",
        "app:layout_marginBottom",
        "app:layout_marginStart",
        "app:layout_marginEnd",
        requireAll = false
    )
    fun View.setLayoutMargins(
        marginAll: Float?,
        marginHorizontal: Float?,
        marginVertical: Float?,
        marginTop: Float?,
        marginBottom: Float?,
        marginStart: Float?,
        marginEnd: Float?
    ) {
        val layoutParams = layoutParams as? ViewGroup.MarginLayoutParams ?: return

        val spacingNone = resources.getDimensionPixelSize(R.dimen.spacing_nothing)

        if (marginAll != null) {
            layoutParams.setMargins(
                marginAll.toInt(), marginAll.toInt(), marginAll.toInt(), marginAll.toInt()
            )
        } else {
            val resolvedStart = marginStart?.toInt() ?: marginHorizontal?.toInt() ?: spacingNone
            val resolvedEnd = marginEnd?.toInt() ?: marginHorizontal?.toInt() ?: spacingNone
            val resolvedTop = marginTop?.toInt() ?: marginVertical?.toInt() ?: spacingNone
            val resolvedBottom = marginBottom?.toInt() ?: marginVertical?.toInt() ?: spacingNone

            layoutParams.setMargins(resolvedStart, resolvedTop, resolvedEnd, resolvedBottom)
        }

        this.layoutParams = layoutParams
    }


    @JvmStatic
    @BindingAdapter("hideOrShowText")
    fun AppCompatTextView.hideOrShowText(string: String?) {
        isVisible = string != null
        string?.let {
            text = it
        }
    }

    @JvmStatic
    @BindingAdapter("selectFirstChip")
    fun ChipGroup.setSelectedChip(isFirst: Boolean?) {
        if (childCount < 2 || isFirst == null) return

        val chipToSelect = if (isFirst) getChildAt(0) else getChildAt(1)

        if (chipToSelect is Chip && chipToSelect.id != checkedChipId) {
            check(chipToSelect.id)
        }
    }

    @JvmStatic
    @InverseBindingAdapter(attribute = "selectedChipId", event = "selectedChipIdAttrChanged")
    fun ChipGroup.getSelectedChipId(): Int {
        return checkedChipId
    }

    @JvmStatic
    @InverseBindingAdapter(attribute = "selectFirstChip", event = "selectFirstChipAttrChanged")
    fun ChipGroup.isFirstChipSelected(): Boolean {
        if (childCount < 2) return true // default to first if not enough chips
        return checkedChipId == (getChildAt(0) as? Chip)?.id
    }

    @JvmStatic
    @BindingAdapter("selectFirstChipAttrChanged")
    fun ChipGroup.setSelectFirstChipAttrChanged(listener: InverseBindingListener?) {
        if (listener != null) {
            setOnCheckedStateChangeListener { _, _ ->
                listener.onChange()
            }
        }
    }

    @JvmStatic
    @BindingAdapter("setSmsMessageTextNBackgroundColor")
    fun AppCompatTextView.setSmsMessageTextNBackgroundColor(classificationType: SmsClassificationType?) {
        if (classificationType == null) return

        // Set common shaped drawable as background
        setBackgroundResource(R.drawable.shape_color_surface_radius_tiny)  // your shape drawable

        val (tintColorRes, textColorRes) = when (classificationType) {
            SmsClassificationType.SCAM -> R.color.scam_light_background to R.color.scam_dark_text
            SmsClassificationType.PROMOTIONAL -> R.color.promotional_light_background to R.color.promotional_dark_text
            SmsClassificationType.IMPORTANT -> R.color.important_light_background to R.color.important_dark_text
            SmsClassificationType.TRANSACTION -> R.color.transaction_light_background to R.color.transaction_dark_text
            SmsClassificationType.OTP -> R.color.otp_light_background to R.color.otp_dark_text
            SmsClassificationType.ALERT -> R.color.alert_light_background to R.color.alert_dark_text
        }

        // Apply tint
        backgroundTintList = ContextCompat.getColorStateList(context, tintColorRes)

        // Apply text color
        setTextColor(ContextCompat.getColor(context, textColorRes))
    }

    @JvmStatic
    @BindingAdapter("senderTypeIcon")
    fun AppCompatImageView.setSenderTypeIcon(senderType: SenderType?) {
        setImageResource(getIconBySenderType(senderType))
    }

    fun getIconBySenderType(senderType: SenderType?): Int {
        return when (senderType) {
            SenderType.BUSINESS -> R.drawable.ic_business_24x24
            SenderType.CONTACT -> R.drawable.ic_contact_24x24
            else -> R.drawable.ic_contact_24x24
        }
    }

}