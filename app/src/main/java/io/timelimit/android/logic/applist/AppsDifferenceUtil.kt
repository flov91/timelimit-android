/*
 * TimeLimit Copyright <C> 2019 - 2026 Jonas Lochmann
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.timelimit.android.logic.applist

import io.timelimit.android.logic.applist.data.InstalledAppsDer
import io.timelimit.android.logic.applist.data.InstalledAppsDifferenceDer
import io.timelimit.android.logic.applist.data.RemovedAppActivityDer
import io.timelimit.android.sync.actions.AddInstalledAppsAction
import io.timelimit.android.sync.actions.AppLogicAction
import io.timelimit.android.sync.actions.RemoveInstalledAppsAction
import io.timelimit.android.sync.actions.UpdateAppActivitiesAction

object AppsDifferenceUtil {
    fun calculateAppsDifference(old: InstalledAppsDer, current: InstalledAppsDer): InstalledAppsDifferenceDer {
        val oldAppsByPackageName = old.apps.associateBy { it.packageName }
        val packageNamesToRemove = (oldAppsByPackageName.keys - current.apps.map { it.packageName }.toSet()).toList()
        val appsToAdd = current.apps.filter { app -> oldAppsByPackageName[app.packageName] != app }

        val oldActivitiesIndexed = old.activities.associateBy { Pair(it.packageName, it.className) }
        val currentActivitiesIndexed = current.activities.associateBy { Pair(it.packageName, it.className) }
        val activitiesToRemove = (oldActivitiesIndexed.keys - currentActivitiesIndexed.keys)
            .map { activity -> RemovedAppActivityDer(packageName = activity.first, className = activity.second) }
        val activitiesToAdd = currentActivitiesIndexed.filter { (key, activity) -> oldActivitiesIndexed[key] != activity }
            .values.toList()

        return InstalledAppsDifferenceDer(
            added = InstalledAppsDer(
                apps = appsToAdd,
                activities = activitiesToAdd
            ),
            removedPackages = packageNamesToRemove,
            removedActivities = activitiesToRemove
        )
    }

    fun calculateAppsDifferenceActions(difference: InstalledAppsDifferenceDer): List<AppLogicAction> {
        val result = mutableListOf<AppLogicAction>()

        if (difference.removedPackages.isNotEmpty()) {
            result.add(RemoveInstalledAppsAction(packageNames = difference.removedPackages))
        }

        if (difference.added.apps.isNotEmpty()) {
            result.add(AddInstalledAppsAction(apps = difference.added.apps.map { it.toInstalledApp() }))
        }

        val addedActivities = difference.added.activities
        val removedActivities = difference.removedActivities

        if (addedActivities.isNotEmpty() || removedActivities.isNotEmpty()) {
            result.add(UpdateAppActivitiesAction(
                removedActivities = removedActivities.map { it.packageName to it.className },
                updatedOrAddedActivities = addedActivities.map { it.toAppActivityItem() }
            ))
        }

        return result
    }
}