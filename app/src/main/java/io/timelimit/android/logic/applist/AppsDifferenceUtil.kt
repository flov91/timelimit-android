/*
 * TimeLimit Copyright <C> 2019 - 2022 Jonas Lochmann
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

import io.timelimit.android.proto.toAppActivityItem
import io.timelimit.android.proto.toInstalledApp
import io.timelimit.android.sync.actions.AddInstalledAppsAction
import io.timelimit.android.sync.actions.AppLogicAction
import io.timelimit.android.sync.actions.RemoveInstalledAppsAction
import io.timelimit.android.sync.actions.UpdateAppActivitiesAction
import io.timelimit.proto.applist.InstalledAppsDifferenceProto
import io.timelimit.proto.applist.InstalledAppsProto
import io.timelimit.proto.applist.RemovedAppActivityProto

object AppsDifferenceUtil {
    fun calculateAppsDifference(old: InstalledAppsProto, current: InstalledAppsProto): InstalledAppsDifferenceProto {
        val oldAppsByPackageName = old.apps.associateBy { it.package_name }
        val packageNamesToRemove = (oldAppsByPackageName.keys - current.apps.map { it.package_name }.toSet()).toList()
        val appsToAdd = current.apps.filter { app -> oldAppsByPackageName[app.package_name] != app }

        val oldActivitiesIndexed = old.activities.associateBy { Pair(it.package_name, it.class_name) }
        val currentActivitiesIndexed = current.activities.associateBy { Pair(it.package_name, it.class_name) }
        val activitiesToRemove = (oldActivitiesIndexed.keys - currentActivitiesIndexed.keys)
            .map { activity -> RemovedAppActivityProto(package_name = activity.first, class_name = activity.second) }
        val activitiesToAdd = currentActivitiesIndexed.filter { (key, activity) -> oldActivitiesIndexed[key] != activity }
            .values.toList()

        return InstalledAppsDifferenceProto(
            added = InstalledAppsProto(
                apps = appsToAdd,
                activities = activitiesToAdd
            ),
            removed_packages = packageNamesToRemove,
            removed_activities = activitiesToRemove
        )
    }

    fun calculateAppsDifferenceActions(difference: InstalledAppsDifferenceProto, deviceId: String): List<AppLogicAction> {
        val result = mutableListOf<AppLogicAction>()

        if (difference.removed_packages.isNotEmpty()) {
            result.add(RemoveInstalledAppsAction(packageNames = difference.removed_packages))
        }

        if (difference.added != null && difference.added.apps.isNotEmpty()) {
            result.add(AddInstalledAppsAction(apps = difference.added.apps.map { it.toInstalledApp() }))
        }

        val addedActivities = difference.added?.activities ?: emptyList()
        val removedActivities = difference.removed_activities

        if (addedActivities.isNotEmpty() || removedActivities.isNotEmpty()) {
            result.add(UpdateAppActivitiesAction(
                removedActivities = removedActivities.map { it.package_name to it.class_name },
                updatedOrAddedActivities = addedActivities.map { it.toAppActivityItem() }
            ))
        }

        return result
    }
}