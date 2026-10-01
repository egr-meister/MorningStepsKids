package com.morningsteps.kids.platform

import android.content.ContentResolver
import android.os.SystemClock
import android.provider.Settings
import com.morningsteps.kids.domain.timers.AppClock

/**
 * Monotonic time comes from [SystemClock.elapsedRealtime] (includes deep sleep, resets on reboot,
 * unaffected by wall-clock changes). The boot reference is the system boot counter, which needs no
 * permission; a changed value means the stored checkpoint belongs to an earlier boot.
 */
class AndroidClock(private val resolver: ContentResolver) : AppClock {
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()

    override fun wallClockMillis(): Long = System.currentTimeMillis()

    override fun bootReference(): String? =
        Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, -1).takeIf { it >= 0 }?.toString()
}
