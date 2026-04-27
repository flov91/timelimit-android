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

package io.timelimit.android.ui.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import io.timelimit.android.data.model.UserType
import io.timelimit.android.ui.main.ActivityViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

class ApiModel(application: Application): AndroidViewModel(application) {
    val activityModel = ActivityViewModel(application)

    val activityCommand = Channel<ActivityCommand>()
    private val authenticationScreenClosed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val permissionsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val authentication = object: AuthenticationModelApi {
        override val authenticatedParentOnly: Flow<AuthenticationModelApi.Parent?> =
            activityModel.authenticatedUser.asFlow().map { pair ->
                if (pair != null) AuthenticationModelApi.Parent(pair.second, pair.first)
                else null
            }

        override val authenticatedParentOrCurrentChild: Flow<AuthenticationModelApi.ParentOrChild?> =
            activityModel.authenticatedUserOrChild.asFlow().map { pair ->
                if (pair != null) AuthenticationModelApi.ParentOrChild(pair.second, pair.first)
                else null
            }

        override suspend fun doParentAuthentication(): AuthenticationModelApi.Parent? {
            authenticatedParentOnly.firstOrNull()?.let { return it }

            triggerAuthenticationScreen()

            authenticationScreenClosed.firstOrNull()

            return authenticatedParentOnly.firstOrNull()
        }

        override suspend fun doParentOrChildAuthentication(childId: String): AuthenticationModelApi.ParentOrChild? {
            authenticatedParentOrCurrentChild.firstOrNull()?.let {
                if (it.user.type == UserType.Parent || it.user.id == childId) return it
            }

            triggerAuthenticationScreen()

            authenticationScreenClosed.firstOrNull()

            return authenticatedParentOrCurrentChild.firstOrNull()
        }

        override fun triggerAuthenticationScreen() {
            activityCommand.trySend(ActivityCommand.ShowAuthenticationScreen)
        }
    }

    fun reportAuthenticationScreenClosed() {
        authenticationScreenClosed.tryEmit(Unit)
    }

    fun reportPermissionsChanged() {
        permissionsChanged.tryEmit(Unit)
    }
}