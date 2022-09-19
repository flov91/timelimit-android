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
package io.timelimit.android.ui.main

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.crypto.DHHandshake
import io.timelimit.android.data.model.User
import io.timelimit.android.data.model.UserType
import io.timelimit.android.livedata.*
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.ParentAction
import io.timelimit.android.sync.actions.apply.*
import io.timelimit.android.u2f.protocol.U2FResponse

class ActivityViewModel(application: Application): AndroidViewModel(application) {
    companion object {
        private const val LOG_TAG = "ActivityViewModel"

        suspend fun dispatchWithoutCheckOrCatching(
                action: ParentAction,
                authenticatedUser: AuthenticatedUser,
                logic: AppLogic
        ) {
            ApplyActionUtil.applyParentAction(
                    action = action,
                    database = logic.database,
                    authentication = ApplyActionUserAuthentication(authenticatedUser),
                    syncUtil = logic.syncUtil,
                    platformIntegration = logic.platformIntegration
            )
        }
    }

    val logic = DefaultAppLogic.with(application)
    val database = logic.database

    val shouldHighlightAuthenticationButton = MutableLiveData<Boolean>().apply { value = false }

    private val authenticatedUserMetadata = MutableLiveData<AuthenticatedUser?>().apply { value = null }
    private val deviceEntry = logic.deviceEntry
    private val deviceUser = logic.deviceUserEntry
    private val userWhichIsKeptSignedIn = deviceEntry.switchMap { device ->
        if (device?.isUserKeptSignedIn == true) {
            deviceUser.map { user ->
                if (user?.id == device.currentUserId) {
                    user
                } else {
                    null as User?
                }
            }
        } else {
            liveDataFromNullableValue(null as User?)
        }
    }.ignoreUnchanged()

    private val authenticatedUserChecked = authenticatedUserMetadata.switchMap { user ->
        val userEntryLive =
            if (user == null) liveDataFromNullableValue(null)
            else database.user().getUserByIdLive(user.userId)

        userEntryLive.switchMap { userEntry ->
            if (userEntry == null) liveDataFromNullableValue(null)
            else {
                val stillValid = when (user) {
                    is AuthenticatedUser.Password -> liveDataFromNonNullValue( user.firstPasswordHash == userEntry.password)
                    is AuthenticatedUser.U2fSigned -> logic.database.u2f().getByClientKeyIdLive(user.u2fClientKeyId).map { keyEntry ->
                        keyEntry != null && keyEntry.userId == user.userId
                    }
                    is AuthenticatedUser.LocalAuth -> logic.fullVersion.isLocalMode
                    null -> liveDataFromNonNullValue(false)
                }

                stillValid.map { valid ->
                    if (valid && user != null) ApplyActionUserAuthentication(user) to userEntry
                    else null
                }
            }
        }
    }

    private val authenticatedChildChecked: LiveData<Pair<ApplyActionParentAuthentication, User>?> = deviceUser.map { user ->
        if (user?.type == UserType.Child && user.allowSelfLimitAdding) {
            ApplyActionChildAddLimitAuthentication as ApplyActionParentAuthentication to user
        } else null
    }

    val authenticatedUserOrChild: LiveData<Pair<ApplyActionParentAuthentication, User>?> =
        mergeLiveDataWaitForValues(userWhichIsKeptSignedIn, authenticatedChildChecked, authenticatedUserChecked)
            .map { (keptSignedIn, localChild, authenticatedUser) ->
                keptSignedIn?.let {
                    ApplyActionParentDeviceAuthentication to it
                } ?: authenticatedUser ?: localChild
            }
            .ignoreUnchanged()

    val authenticatedUser = authenticatedUserOrChild.map { if (it?.second?.type != UserType.Parent) null else it }

    fun isParentAuthenticated(): Boolean {
        val user = authenticatedUser.value

        return user != null && user.second.type == UserType.Parent
    }

    fun isParentOrChildAuthenticated(childId: String): Boolean {
        val user = authenticatedUserOrChild.value

        return user != null && (user.second.type == UserType.Parent || user.second.id == childId)
    }

    fun requestAuthentication() {
        shouldHighlightAuthenticationButton.value = true
    }

    fun requestAuthenticationOrReturnTrue(): Boolean {
        if (isParentAuthenticated()) {
            return true
        } else {
            requestAuthentication()

            return false
        }
    }

    fun requestAuthenticationOrReturnTrueAllowChild(childId: String): Boolean {
        if (isParentOrChildAuthenticated(childId)) {
            return true
        } else {
            requestAuthentication()

            return false
        }
    }

    fun tryDispatchParentAction(action: ParentAction, allowAsChild: Boolean = false): Boolean = tryDispatchParentActions(listOf(action), allowAsChild)

    fun tryDispatchParentActions(actions: List<ParentAction>, allowAsChild: Boolean = false): Boolean {
        val status = authenticatedUserOrChild.value

        if (status == null || (status.second.type != UserType.Parent && !allowAsChild)) {
            requestAuthentication()
            return false
        }

        runAsync {
            try {
                actions.forEach { action ->
                    ApplyActionUtil.applyParentAction(
                            action = action,
                            database = database,
                            authentication = status.first,
                            syncUtil = logic.syncUtil,
                            platformIntegration = logic.platformIntegration
                    )
                }
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "error dispatching actions", ex)
                }

                Toast.makeText(getApplication(), R.string.error_general, Toast.LENGTH_SHORT).show()
            }
        }

        return true
    }

    fun setAuthenticatedUser(user: AuthenticatedUser) {
        authenticatedUserMetadata.value = user
        shouldHighlightAuthenticationButton.value = false
    }

    fun getAuthenticatedUser() = authenticatedUserMetadata.value

    fun logOut() {
        authenticatedUserMetadata.value = null
    }
}

sealed class AuthenticatedUser {
    abstract val userId: String

    data class Password(
        override val userId: String,
        val firstPasswordHash: String,
        val secondPasswordHash: String
    ): AuthenticatedUser()

    data class U2fSigned(
        override val userId: String,
        val u2fServerKeyId: String,
        val u2fClientKeyId: Long,
        val signature: U2FResponse.Login,
        val dh: DHHandshake
    ): AuthenticatedUser()

    sealed class LocalAuth: AuthenticatedUser() {
        data class U2f(override val userId: String): LocalAuth()
        data class ScanCode(override val userId: String): LocalAuth()
    }
}