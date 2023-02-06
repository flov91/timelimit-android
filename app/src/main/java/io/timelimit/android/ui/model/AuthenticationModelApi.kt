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
package io.timelimit.android.ui.model

import io.timelimit.android.data.model.User
import io.timelimit.android.sync.actions.apply.ApplyActionParentAuthentication
import kotlinx.coroutines.flow.Flow

interface AuthenticationModelApi {
    val authenticatedParentOnly: Flow<Parent?>
    val authenticatedParentOrSelfLimitAdding: Flow<ParentOrChild?>

    fun triggerAuthenticationScreen()
    suspend fun doParentAuthentication(): Parent?

    data class Parent(
        val user: User,
        val authentication: ApplyActionParentAuthentication
    )

    data class ParentOrChild(
        val user: User,
        val authentication: ApplyActionParentAuthentication
    )
}