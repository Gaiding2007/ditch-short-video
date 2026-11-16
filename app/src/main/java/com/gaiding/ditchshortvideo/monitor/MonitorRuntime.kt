package com.gaiding.ditchshortvideo.monitor

/** Read-only snapshot used by the UI; written once per second by the service. */
object MonitorRuntime {
    data class State(
        val connected: Boolean = false,
        val active: Boolean = false,
        val packageName: String? = null,
        val activityName: String? = null,
        val appLabel: String? = null,
        val reason: String = "无障碍服务未连接",
        val detection: String = "",
        val screenInteractive: Boolean = true,
        val locked: Boolean = false,
        val sessionSeconds: Long = 0L,
        val positiveStreak: Int = 0,
        val updatedAtElapsed: Long = 0L
    )

    @Volatile
    var state: State = State()
        private set

    fun update(value: State) {
        state = value
    }

    fun reset() {
        state = State()
    }
}
