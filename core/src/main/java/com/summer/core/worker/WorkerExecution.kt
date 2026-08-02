package com.summer.core.worker

import android.util.Log
import androidx.work.WorkInfo

/**
 * Worker-type / mode / result / exit-cause vocabularies plus lightweight Logcat logging (tag [TAG])
 * for WorkManager worker runs. Emits one START / FG / END line per `doWork()` invocation, correlated
 * by the work-request UUID. Side-effect free — no persistence.
 */
object WorkerExecution {
    const val TYPE_SMS_BATCH = "SMS_BATCH"
    const val TYPE_NER = "NER"

    const val MODE_REALTIME = "REALTIME"
    const val MODE_BACKFILL = "BACKFILL"

    const val RESULT_RUNNING = "RUNNING"
    const val RESULT_SUCCESS = "SUCCESS"
    const val RESULT_RETRY = "RETRY"
    const val RESULT_FAILURE = "FAILURE"
    const val RESULT_INTERRUPTED = "INTERRUPTED"

    const val CAUSE_COMPLETED = "COMPLETED"
    const val CAUSE_DRAINED = "DRAINED"
    const val CAUSE_CHUNK_CAP = "CHUNK_CAP"
    const val CAUSE_DURATION_CAP = "DURATION_CAP"
    const val CAUSE_REALTIME_PREEMPT = "REALTIME_PREEMPT"
    const val CAUSE_STOPPED = "STOPPED"
    const val CAUSE_FAILURE = "FAILURE"
    const val CAUSE_PROCESS_KILLED = "PROCESS_KILLED"

    private const val TAG = "WorkerExec"

    fun start(
        workerType: String,
        mode: String?,
        workRequestId: String,
        chainId: String,
        chainIndex: Int,
        runAttempt: Int,
    ) {
        Log.i(
            TAG,
            "START type=$workerType mode=${mode ?: "-"} chain=$chainId[$chainIndex] " +
                "attempt=$runAttempt work=$workRequestId",
        )
    }

    fun foreground(workRequestId: String, inForeground: Boolean) {
        Log.i(TAG, "FG    work=$workRequestId executedInForeground=$inForeground")
    }

    fun end(
        workRequestId: String,
        result: String,
        exitCause: String?,
        wasStopped: Boolean,
        stopReason: Int?,
        itemsProcessed: Int,
        itemsTotal: Int? = null,
        failureCode: String? = null,
        failureMessage: String? = null,
    ) {
        Log.i(
            TAG,
            "END   work=$workRequestId result=$result cause=${exitCause ?: "-"} stopped=$wasStopped " +
                "stopReason=${stopReasonName(stopReason)} " +
                "items=$itemsProcessed${itemsTotal?.let { "/$it" } ?: ""}" +
                (failureCode?.let { " failure=$it" } ?: ""),
        )
    }

    fun stopReasonName(reason: Int?): String = when (reason) {
        null -> "NONE"
        WorkInfo.STOP_REASON_NOT_STOPPED -> "NOT_STOPPED"
        WorkInfo.STOP_REASON_UNKNOWN -> "UNKNOWN"
        WorkInfo.STOP_REASON_CANCELLED_BY_APP -> "CANCELLED_BY_APP"
        WorkInfo.STOP_REASON_PREEMPT -> "PREEMPT"
        WorkInfo.STOP_REASON_TIMEOUT -> "TIMEOUT"
        WorkInfo.STOP_REASON_DEVICE_STATE -> "DEVICE_STATE"
        WorkInfo.STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW -> "CONSTRAINT_BATTERY_NOT_LOW"
        WorkInfo.STOP_REASON_CONSTRAINT_CHARGING -> "CONSTRAINT_CHARGING"
        WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY -> "CONSTRAINT_CONNECTIVITY"
        WorkInfo.STOP_REASON_CONSTRAINT_DEVICE_IDLE -> "CONSTRAINT_DEVICE_IDLE"
        WorkInfo.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW -> "CONSTRAINT_STORAGE_NOT_LOW"
        WorkInfo.STOP_REASON_QUOTA -> "QUOTA"
        WorkInfo.STOP_REASON_BACKGROUND_RESTRICTION -> "BACKGROUND_RESTRICTION"
        WorkInfo.STOP_REASON_APP_STANDBY -> "APP_STANDBY"
        WorkInfo.STOP_REASON_USER -> "USER"
        WorkInfo.STOP_REASON_SYSTEM_PROCESSING -> "SYSTEM_PROCESSING"
        WorkInfo.STOP_REASON_ESTIMATED_APP_LAUNCH_TIME_CHANGED -> "ESTIMATED_APP_LAUNCH_TIME_CHANGED"
        else -> "CODE_$reason"
    }
}
