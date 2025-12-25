package com.summer.notifai.baselineprofile

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
 * The generated profile will be copied to app/src/main/baseline-prof.txt
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generateProfile() {
        rule.collect(
            packageName = "com.summer.notifai",
            includeInStartupProfile = true,
            profileBlock = {
                // Start the app
                pressHome()
                startActivityAndWait()

                // Wait for splash to complete and content to load
                device.wait(Until.hasObject(By.res("com.summer.notifai:id/rv_frag_contact_list")), 10_000)

                // Find and click on the first contact in the list
                val contactList = device.findObject(By.res("com.summer.notifai:id/rv_frag_contact_list"))
                if (contactList != null) {
                    // Wait a bit for list to populate
                    device.waitForIdle()
                    
                    // Try to click on a recycler view item
                    val items = contactList.children
                    if (items.isNotEmpty()) {
                        items[0].click()
                        
                        // Wait for inbox to load
                        device.wait(Until.hasObject(By.res("com.summer.notifai:id/rv_sms_messages")), 10_000)
                        device.waitForIdle()
                        
                        // Go back to contact list
                        device.pressBack()
                        device.wait(Until.hasObject(By.res("com.summer.notifai:id/rv_frag_contact_list")), 5_000)
                    }
                }
            }
        )
    }
}
