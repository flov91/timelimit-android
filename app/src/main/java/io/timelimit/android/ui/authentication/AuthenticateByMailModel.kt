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
package io.timelimit.android.ui.authentication

import android.app.Application
import androidx.compose.material.SnackbarHostState
import androidx.lifecycle.*
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.model.mailauthentication.MailAuthentication
import kotlinx.coroutines.flow.*

class AuthenticateByMailModel(application: Application): AndroidViewModel(application) {
    private val logic = DefaultAppLogic.with(application)
    private val stateLive = MutableStateFlow(emptyMap<String, MailAuthentication.State>())
    private val mailAuthTokenLive = MutableStateFlow<String?>(null)

    val recoveryUserIdLive = MutableStateFlow<String?>(null)
    val snackbarHostState = SnackbarHostState()

    private val forcedMailAddressLive: Flow<String> = recoveryUserIdLive.transformLatest { userId ->
        if (userId == null) emit("")
        else emitAll(logic.database.user().getUserByIdFlow(userId).map { it?.mail ?: "" })
    }

    suspend fun getMailAuthToken(): String = mailAuthTokenLive.filterNotNull().first()

    val screenLive = forcedMailAddressLive.transformLatest { forcedMailAddress ->
        val initialState =
            if (forcedMailAddress == "") MailAuthentication.State.EnterMailAddress()
            else MailAuthentication.State.ConfirmMailSending(forcedMailAddress)

        emitAll(
            MailAuthentication.handle(
                logic,
                viewModelScope,
                snackbarHostState,
                stateLive.map { state ->
                    state[forcedMailAddress] ?: initialState
                },
                updateState = { modifier -> stateLive.update { oldState ->
                    oldState + Pair(forcedMailAddress, modifier(oldState[forcedMailAddress] ?: initialState))
                } },
                processAuthToken = { mailAuthTokenLive.compareAndSet(expect = null, update = it) }
            )
        )
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(1000), 1)
}