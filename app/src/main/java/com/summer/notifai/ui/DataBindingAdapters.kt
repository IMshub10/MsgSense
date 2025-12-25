package com.summer.notifai.ui

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.databinding.BindingAdapter
import com.summer.core.android.sms.util.SmsStatus
import com.summer.notifai.R

object DataBindingAdapters {

    @JvmStatic
    @BindingAdapter(value = ["selectedBackgroundTint", "isIncoming"], requireAll = true)
    fun View.setSelectedBackgroundColorOnlyIfSelected(isSelected: Boolean, isIncoming: Boolean) {
        foreground = if (isSelected) {
            // Optional soft overlay like WhatsApp
            ContextCompat.getDrawable(context, R.drawable.bg_selected_overlay)
        } else {
            // Remove selection effect
            null
        }
    }

    /**
     * Binding adapter to set SMS status icon based on status code.
     * - DELIVERED (100) -> ic_double_tick_24x24
     * - SENT (0) -> ic_tick_24x24
     * - PENDING (32) -> ic_error_24x24 (grey)
     * - FAILED (64) / DELIVERY_FAILED (101) -> ic_error_red_24x24
     */
    @JvmStatic
    @BindingAdapter("smsStatusIcon")
    fun ImageView.setSmsStatusIcon(status: Int?) {
        val smsStatus = SmsStatus.fromCode(status)
        val drawableRes = when (smsStatus) {
            SmsStatus.DELIVERED -> R.drawable.ic_double_tick_24x24
            SmsStatus.SENT -> R.drawable.ic_tick_24x24
            SmsStatus.PENDING -> R.drawable.ic_error_24x24
            SmsStatus.FAILED, SmsStatus.DELIVERY_FAILED -> R.drawable.ic_error_red_24x24
            SmsStatus.NONE -> null
        }
        if (drawableRes != null) {
            setImageResource(drawableRes)
            visibility = View.VISIBLE
        } else {
            visibility = View.GONE
        }
    }

    private fun resolveThemeColor(attrRes: Int, context: Context): Int {
        val typedValue = TypedValue()
        val theme = context.theme
        return if (theme.resolveAttribute(attrRes, typedValue, true)) {
            ContextCompat.getColor(context, typedValue.resourceId)
        } else {
            0 // fallback if not found
        }
    }
}