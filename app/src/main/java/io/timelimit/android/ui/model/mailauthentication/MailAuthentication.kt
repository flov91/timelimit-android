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
package io.timelimit.android.ui.model.mailauthentication

import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.SnackbarResult
import io.timelimit.android.R
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.sync.network.api.*
import io.timelimit.android.ui.diagnose.exception.ExceptionUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.IOException

object MailAuthentication {
    sealed class State: java.io.Serializable {
        sealed class InitialState: State()

        data class ConfirmMailSending(val mail: String, val error: ErrorDialog? = null): InitialState()

        data class EnterReceivedCode(
            val mail: String,
            val serverToken: String,
            val codeInput: String,
            val error: ErrorDialog?,
            val initialState: InitialState
        ): State()
    }

    sealed class Screen {
        class ConfirmMailSending(
            val mail: String,
            val error: Error?,
            val confirm: (() -> Unit)?
        ): Screen()

        class EnterReceivedCode(
            val mail: String,
            val codeInput: String,
            val error: Error?,
            val actions: Actions?
        ): Screen() {
            class Actions(
                val updateCodeInput: (String) -> Unit,
                val confirmCodeInput: () -> Unit
            )
        }

        data class Error(val dialog: ErrorDialog, val close: () -> Unit)
    }

    sealed class ErrorDialog {
        object RateLimit: ErrorDialog()
        object BlockedMailServer: ErrorDialog()
        object MailAddressNotAllowed: ErrorDialog()
        data class ExceptionDetails(val message: String): ErrorDialog()
    }

    fun handle(
        logic: AppLogic,
        scope: CoroutineScope,
        snackbarHostState: SnackbarHostState,
        stateLive: Flow<State>,
        updateState: ((State) -> State) -> Unit,
        processAuthToken: suspend (String) -> Unit
    ): Flow<Screen> {
        var lastErrorJob: Job? = null
        val isWorkingLive = MutableStateFlow(false)

        return combine(stateLive, isWorkingLive) { state, isWorking ->
            when (state) {
                is State.ConfirmMailSending -> {
                    val update: ((State.ConfirmMailSending) -> State) -> Unit = { modifier ->
                        updateState { oldState ->
                            if (oldState is State.ConfirmMailSending) modifier(oldState)
                            else oldState
                        }
                    }

                    val confirm: () -> Unit = { scope.launch {
                        if (isWorkingLive.compareAndSet(expect = false, update = true)) try {
                            lastErrorJob?.cancel()

                            val serverConfiguration = logic.serverLogic.getServerConfigCoroutine()

                            val serverToken = serverConfiguration.api.sendMailLoginCode(
                                mail = state.mail,
                                locale = logic.context.resources.configuration.locale.language,
                                deviceAuthToken = serverConfiguration.deviceAuthToken.ifEmpty { null }
                            )

                            update { State.EnterReceivedCode(
                                mail = state.mail,
                                serverToken = serverToken,
                                codeInput = "",
                                error = null,
                                initialState = State.ConfirmMailSending(mail = state.mail, error = null)
                            ) }
                        } catch (ex: TooManyRequestsHttpError) {
                            update { it.copy(error = ErrorDialog.RateLimit) }
                        } catch (ex: MailServerBlacklistedException) {
                            update { it.copy(error = ErrorDialog.BlockedMailServer) }
                        } catch (ex: MailAddressNotWhitelistedException) {
                            update { it.copy(error = ErrorDialog.MailAddressNotAllowed) }
                        } catch (ex: Exception) {
                            lastErrorJob = scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    logic.context.getString(
                                        when (ex) {
                                            is HttpError -> R.string.error_server_rejected
                                            is IOException -> R.string.error_network
                                            else -> R.string.error_general
                                        }
                                    ),
                                    actionLabel = logic.context.getString(R.string.generic_show_details),
                                    duration = SnackbarDuration.Short
                                )

                                if (result == SnackbarResult.ActionPerformed) {
                                    val message = ExceptionUtil.format(ex)

                                    update { it.copy(error = ErrorDialog.ExceptionDetails(message)) }
                                }
                            }
                        } finally {
                            isWorkingLive.value = false
                        }
                    } }

                    val error = state.error?.let {
                        Screen.Error(it) { update { it.copy(error = null) } }
                    }

                    Screen.ConfirmMailSending(
                        mail = state.mail,
                        error = error,
                        confirm = if (isWorking) null else confirm
                    )
                }
                is State.EnterReceivedCode -> {
                    val update: ((State.EnterReceivedCode) -> State) -> Unit = { modifier ->
                        updateState { oldState ->
                            if (oldState is State.EnterReceivedCode) modifier(oldState)
                            else oldState
                        }
                    }

                    val actions = Screen.EnterReceivedCode.Actions(
                        updateCodeInput = { code ->
                            if (!isWorkingLive.value) update { it.copy(codeInput = code) }
                        },
                        confirmCodeInput = { scope.launch {
                            if (isWorkingLive.compareAndSet(expect = false, update = true)) try {
                                lastErrorJob?.cancel()

                                val serverConfiguration = logic.serverLogic.getServerConfigCoroutine()

                                val authToken = serverConfiguration.api.signInByMailCode(
                                    mailLoginToken = state.serverToken,
                                    code = state.codeInput
                                )

                                processAuthToken(authToken)
                            } catch (ex: ForbiddenHttpError) {
                                lastErrorJob = scope.launch {
                                    snackbarHostState.showSnackbar(
                                        logic.context.getString(R.string.authenticate_by_mail_snackbar_wrong_code)
                                    )
                                }
                            } catch (ex: GoneHttpError) {
                                snackbarHostState.showSnackbar(
                                    logic.context.getString(R.string.authenticate_by_mail_snackbar_wrong_code)
                                )

                                // go back to first step
                                update { it.initialState }
                            } catch (ex: Exception) {
                                lastErrorJob = scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        logic.context.getString(
                                            when (ex) {
                                                is HttpError -> R.string.error_server_rejected
                                                is IOException -> R.string.error_network
                                                else -> R.string.error_general
                                            }
                                        ),
                                        actionLabel = logic.context.getString(R.string.generic_show_details),
                                        duration = SnackbarDuration.Short
                                    )

                                    if (result == SnackbarResult.ActionPerformed) {
                                        val message = ExceptionUtil.format(ex)

                                        update { it.copy(error = ErrorDialog.ExceptionDetails(message)) }
                                    }
                                }
                            } finally {
                                isWorkingLive.value = false
                            }
                        } }
                    )

                    val error = state.error?.let {
                        Screen.Error(it) { update { it.copy(error = null) } }
                    }

                    Screen.EnterReceivedCode(
                        mail = state.mail,
                        codeInput = state.codeInput,
                        error = error,
                        actions = if (isWorking) null else actions
                    )
                }
            }
        }
    }
}