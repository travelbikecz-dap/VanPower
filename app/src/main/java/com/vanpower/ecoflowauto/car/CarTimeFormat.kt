package com.vanpower.ecoflowauto.car

import android.content.Context
import com.vanpower.ecoflowauto.R

internal object CarTimeFormat {
    fun formatMinutes(context: Context, minutes: Int?): String {
        if (minutes == null) {
            return context.getString(R.string.car_time_unknown)
        }
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours > 0 && mins > 0 -> context.getString(R.string.car_time_hours_minutes, hours, mins)
            hours > 0 -> context.getString(R.string.car_time_hours, hours)
            else -> context.getString(R.string.car_time_minutes, mins)
        }
    }
}
