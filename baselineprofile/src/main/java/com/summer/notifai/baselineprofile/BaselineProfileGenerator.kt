package com.summer.notifai.baselineprofile

import android.content.Intent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates a Baseline Profile for the app by exercising critical user journeys.
 *
 * Run this with: ./gradlew :baselineprofile:generateBaselineProfile
 *
 * The generated profile will be copied to app/src/release/generated/baselineProfiles/baseline-prof.txt
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generateProfile() {
        rule.collect(
            packageName = PACKAGE_NAME,
            includeInStartupProfile = true,
            profileBlock = {
                // Start the app with explicit intent to avoid launch detection issues
                pressHome()

                // Use startActivityAndWait with custom setup
                startActivityAndWait(
                    Intent(Intent.ACTION_MAIN).apply {
                        setPackage(PACKAGE_NAME)
                        addCategory(Intent.CATEGORY_LAUNCHER)
                    }
                )

                // Wait longer for splash screen and content to load
                // Give extra time for Hilt injection which can take ~1.3s on first load
                device.wait(Until.hasObject(By.res("$PACKAGE_NAME:id/rv_fragContactList")), 15_000)
                device.waitForIdle()

                // Exercise the critical user journey: Contact List -> Inbox
                val contactList = device.findObject(By.res("$PACKAGE_NAME:id/rv_fragContactList"))
                if (contactList != null && contactList.childCount > 0) {
                    // Click on first contact to navigate to inbox
                    contactList.children?.firstOrNull()?.click()

                    // Wait for inbox to fully load (this is the slow path we want to optimize)
                    device.wait(Until.hasObject(By.res("$PACKAGE_NAME:id/rv_sms_messages")), 15_000)
                    device.waitForIdle()

                    // Navigate back
                    device.pressBack()
                    device.wait(
                        Until.hasObject(By.res("$PACKAGE_NAME:id/rv_fragContactList")),
                        5_000
                    )
                    device.waitForIdle()
                }

                // Exercise FAB -> NewContactListFrag path
                val fab =
                    device.findObject(By.res("$PACKAGE_NAME:id/fab_fragSmsContactList_viewContacts"))
                if (fab != null) {
                    fab.click()
                    device.wait(
                        Until.hasObject(By.res("$PACKAGE_NAME:id/rv_fragNewContactList_list")),
                        10_000
                    )
                    device.waitForIdle()
                    device.pressBack()
                    device.waitForIdle()
                }
            }
        )
    }

    companion object {
        private const val PACKAGE_NAME = "com.summer.notifai"
    }
}


