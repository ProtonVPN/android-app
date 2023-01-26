package com.protonvpn.android.utils

import kotlinx.coroutines.*

// Allows scheduling recurring suspending action, can be rescheduled to run another time.
// Not thread safe - schedule calls should run in the same thread that scope will use.
class ReschedulableTask(
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val action: suspend ReschedulableTask.() -> Unit
) {
    private var currentJob: Job? = null
    private var isExecuting: Boolean = false

    private var scheduledTo: Long? = null

    // If action is currently executing it'll let it finish
    fun cancelSchedule() {
        scheduledTo = null
        if (!isExecuting)
            currentJob?.cancel()
    }

    fun scheduleIn(delay: Long) {
        scheduleTo(now() + delay)
    }

    fun scheduleTo(to: Long) {
        scheduledTo = to
        if (!isExecuting) {
            currentJob?.cancel()
            currentJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                delay((to - now()).coerceAtLeast(0))
                try {
                    isExecuting = true
                    scheduledTo = null
                    yield()
                    action()
                    currentJob = null
                } finally {
                    isExecuting = false
                    scheduledTo?.let { scheduleTo(it) }
                }
            }
        }
    }
}
