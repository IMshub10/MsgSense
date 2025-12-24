package com.summer.core.android.device.util

import android.app.ActivityManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceTierEvaluator @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        private const val HIGH_END_RAM_GB = 8
        private const val HIGH_END_CPU_CORES = 6
        private const val MID_RANGE_RAM_GB = 4
        private const val MID_RANGE_CPU_CORES = 4
    }

    private val deviceTier: DeviceTier by lazy {
        val totalRam = getTotalRamInGB()
        val cores = getCpuCoreCount()

        when {
            totalRam >= HIGH_END_RAM_GB && cores >= HIGH_END_CPU_CORES -> DeviceTier.HIGH_END
            totalRam >= MID_RANGE_RAM_GB && cores >= MID_RANGE_CPU_CORES -> DeviceTier.MID_RANGE
            else -> DeviceTier.LOW_END
        }
    }

    fun getRecommendedBatchSettings(): Pair<Int, Int> {
        return when (deviceTier) {
            DeviceTier.HIGH_END -> 3 to 8
            DeviceTier.MID_RANGE -> 2 to 5
            DeviceTier.LOW_END -> 1 to 2
        }
    }

    private fun getTotalRamInGB(): Int {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            (memInfo.totalMem / (1024L * 1024L * 1024L)).toInt()
        } catch (e: Exception) {
            2
        }
    }

    private fun getCpuCoreCount(): Int {
        return try {
            File("/sys/devices/system/cpu/")
                .listFiles { file -> file.name.matches(Regex("cpu[0-9]+")) }
                ?.size ?: Runtime.getRuntime().availableProcessors()
        } catch (e: Exception) {
            Runtime.getRuntime().availableProcessors()
        }
    }
}