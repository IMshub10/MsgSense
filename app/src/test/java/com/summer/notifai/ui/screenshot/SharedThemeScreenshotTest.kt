package com.summer.notifai.ui.screenshot

import android.view.LayoutInflater
import android.widget.LinearLayout
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.summer.notifai.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
class SharedThemeScreenshotTest {
    @Test fun sharedComponentsLight() = captureComponents()

    @Test
    @Config(qualifiers = "w360dp-h780dp-night-xxhdpi")
    fun sharedComponentsDark() = captureComponents()

    @Test
    fun settingsRepresentative() {
        val activity = ScreenshotTestHost.activity()
        ScreenshotTestHost.inflate(activity, R.layout.frag_settings).captureRoboImage()
    }

    @Test
    fun smsProcessingRepresentative() {
        val activity = ScreenshotTestHost.activity()
        ScreenshotTestHost.inflate(activity, R.layout.frag_sms_processing).captureRoboImage()
    }

    private fun captureComponents() {
        val activity = ScreenshotTestHost.activity()
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface))
        }
        fun field(hint: String, value: String = "", error: String? = null, enabled: Boolean = true) {
            val layout = TextInputLayout(activity, null, com.google.android.material.R.attr.textInputStyle).apply {
                this.hint = hint
                isEnabled = enabled
            }
            layout.addView(TextInputEditText(activity).apply { setText(value) })
            layout.error = error
            root.addView(layout)
        }
        field("Empty field")
        field("Populated field", "Synthetic value")
        field("Error field", error = "Synthetic validation error")
        field("Disabled field", "Synthetic value", enabled = false)
        root.addView(Chip(activity).apply { text = "Selected chip"; isCheckable = true; isChecked = true })
        root.addView(MaterialButton(activity).apply { text = "Primary action" })
        root.addView(LayoutInflater.from(activity).inflate(R.layout.item_transaction, root, false))
        activity.setContentView(root)
        root.captureRoboImage()
    }
}
