/*
 * TimeLimit Copyright <C> 2019 - 2023 Jonas Lochmann
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
package io.timelimit.android.ui.model.launch

import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.data.model.UserType
import io.timelimit.android.livedata.waitUntilValueMatches
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.ui.model.Screen
import io.timelimit.android.ui.model.State
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

object LaunchHandling {
    fun processLaunchState(
        state: MutableStateFlow<State>,
        logic: AppLogic
    ): Flow<Screen> = flow {
        val oldValue = state.value

        if (oldValue is State.LaunchState) {
            state.compareAndSet(oldValue, getInitialState(logic))
        }
    }

    private suspend fun getInitialState(logic: AppLogic): State {
        logic.isInitialized.waitUntilValueMatches { it == true }

        // TODO: readd the obsolete dialog fragment
        return Threads.database.executeAndWait {
            val hasDeviceId = logic.database.config().getOwnDeviceIdSync() != null
            val hasParentKey = logic.database.config().getParentModeKeySync() != null

            if (hasDeviceId) {
                val config = logic.database.derivedDataDao().getUserAndDeviceRelatedDataSync()
                val overview = State.Overview()

                if (config?.userRelatedData?.user?.type == UserType.Child) State.ManageChild.Main(
                    previousOverview = overview,
                    childId = config.userRelatedData.user.id
                )
                else if (config?.userRelatedData == null) State.SetupDevice(overview)
                else overview
            } else if (hasParentKey) State.ParentMode()
            else State.Setup.SetupTerms()
        }
    }
}