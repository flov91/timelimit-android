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
package io.timelimit.android.ui.model.intro

import androidx.lifecycle.asFlow
import io.timelimit.android.async.Threads
import io.timelimit.android.logic.AppLogic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object IntroHandling {
    sealed class Screen {
        data class Visible(val hide: () -> Unit): Screen()
        data class Hidden(val show: () -> Unit): Screen()
    }

    fun handle(
        logic: AppLogic,
        flag: Long
    ): Flow<Screen> = logic.database.config().wereHintsShown(flag).asFlow().map {
        if (!it) Screen.Visible {
            Threads.database.execute {
                try {
                    logic.database.runInTransaction {
                        logic.database.config().setHintsShownSync(flag)
                    }
                } catch (ex: Exception) {
                    // ignore
                }
            }
        }
        else Screen.Hidden {
            Threads.database.execute {
                try {
                    logic.database.runInTransaction {
                        logic.database.config().setHintsNotShownSync(flag)
                    }
                } catch (ex: Exception) {
                    // ignore
                }
            }
        }
    }
}