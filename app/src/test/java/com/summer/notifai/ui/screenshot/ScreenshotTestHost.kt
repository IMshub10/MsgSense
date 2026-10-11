package com.summer.notifai.ui.screenshot

import android.app.Activity
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import com.summer.notifai.R
import org.robolectric.Robolectric

internal object ScreenshotTestHost {
    fun activity(fontScale: Float = 1f): Activity {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(R.style.Theme_NotifAI)
        if (fontScale != 1f) {
            val configuration = Configuration(activity.resources.configuration).apply {
                this.fontScale = fontScale
            }
            activity.resources.updateConfiguration(configuration, activity.resources.displayMetrics)
        }
        return activity
    }

    fun inflate(activity: Activity, @LayoutRes layout: Int): View =
        LayoutInflater.from(activity).inflate(layout, FrameLayout(activity), false).also {
            activity.setContentView(it)
        }
}
