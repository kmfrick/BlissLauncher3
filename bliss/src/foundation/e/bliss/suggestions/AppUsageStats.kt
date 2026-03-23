/*
 * Copyright © MURENA SAS 2023.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package foundation.e.bliss.suggestions

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.android.launcher3.R
import foundation.e.bliss.utils.Logger
import java.util.Calendar

class AppUsageStats(private val mContext: Context) {
    private val mUsageStatsManager
        get() = mContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    val usageStats: List<UsageStats>
        get() {
            val usageStats = mutableListOf<UsageStats>()
            val cal = Calendar.getInstance()
            cal.add(Calendar.YEAR, -1)

            val stats =
                mUsageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_BEST,
                    cal.timeInMillis,
                    System.currentTimeMillis()
                )

            val aggregatedStats = mutableMapOf<String, UsageStats>()
            val statCount = stats.size

            for (i in 0 until statCount) {
                val newStat = stats[i]
                val existingStat = aggregatedStats[newStat.packageName]

                if (existingStat == null) {
                    aggregatedStats[newStat.packageName] = newStat
                } else {
                    existingStat.add(newStat)
                }
            }

            // PACKAGE_USAGE_STATS is a special permission that requires user to enable
            // it in Settings. We cannot request it like a normal runtime permission.
            // Just return empty stats if not granted - the app will show all apps instead
            // of usage-based suggestions.
            if (
                mContext.checkCallingOrSelfPermission(
                    android.Manifest.permission.PACKAGE_USAGE_STATS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Logger.i(TAG, "Usage stats permission not granted, returning empty stats")
            } else if (aggregatedStats.isNotEmpty()) {
                val statsMap = aggregatedStats.entries

                statsMap
                    .toList()
                    .filter {
                        !mContext.resources
                            .getStringArray(R.array.blacklisted_apps)
                            .contains(it.key) &&
                            mContext.packageManager.getLaunchIntentForPackage(it.key) != null &&
                            it.value.totalTimeInForeground > 0
                    }
                    .apply {
                        sortedWith(
                                Comparator.comparingLong { (_, stat) -> stat.totalTimeInForeground }
                            )
                            .reversed()
                            .forEach { (_, stat) -> usageStats.add(stat) }
                    }
            } else {
                Logger.i(TAG, "The aggregatedStats are empty can't do much")
            }

            return usageStats
        }

    companion object {
        private const val TAG = "AppUsageStats"
    }
}
