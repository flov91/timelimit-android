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
package io.timelimit.android.ui.model.diagnose

import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.material.SnackbarHostState
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.model.App
import io.timelimit.android.extensions.whileTrue
import io.timelimit.android.integration.platform.DeviceOwnerApi
import io.timelimit.android.integration.platform.PlatformIntegration
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.ui.model.AuthenticationModelApi
import io.timelimit.android.ui.model.Screen
import io.timelimit.android.ui.model.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.Serializable

object DeviceOwnerHandling {
    private const val LOG_TAG = "DeviceOwnerHandling"

    data class OwnerState(
        val appListDialog: AppListDialog? = null,
        val apps: List<String> = emptyList(),
        val organizationName: OrganizationName = OrganizationName.Original
    ): Serializable {
        data class AppListDialog(
            val filter: String = ""
        ): Serializable
    }

    sealed class OrganizationName: Serializable {
        object Original: OrganizationName()
        object Error: OrganizationName()
        class Modified(val value: String): OrganizationName()
    }

    sealed class OwnerScreen {
        object Error: OwnerScreen()

        data class Normal(
            val isParentAuthenticated: Boolean,
            val organizationName: String,
            val appListDialog: AppListDialog?,
            val scopes: List<DeviceOwnerApi.DelegationScope>,
            val apps: List<AppInfo>,
            val actions: Actions
        ): OwnerScreen() {
            data class AppInfo(
                val packageName: String,
                val title: String?,
                val icon: Drawable?,
                val scopes: Set<DeviceOwnerApi.DelegationScope>
            )

            data class AppListDialog(
                val filter: String,
                val apps: List<App>
            )

            data class Actions(
                val updateOrganizationName: ((String) -> Unit)?,
                val showAppListDialog: () -> Unit,
                val dismissAppListDialog: () -> Unit,
                val addApp: (String) -> Unit,
                val updateScopeEnabled: (String, DeviceOwnerApi.DelegationScope, Boolean) -> Unit,
                val updateDialogSearch: (String) -> Unit
            )
        }
    }

    fun processState(
        logic: AppLogic,
        scope: CoroutineScope,
        authentication: AuthenticationModelApi,
        stateLive: MutableStateFlow<State>
    ): Flow<Screen> {
        val snackbarHostState = SnackbarHostState()

        val hasMatchingState = stateLive.map { it is State.DiagnoseScreen.DeviceOwner }
        val ownerStateLive = stateLive.transform { if (it is State.DiagnoseScreen.DeviceOwner) emit (it) }

        val screenLive = getScreen(
            logic,
            ownerStateLive.map { it.details },
            scope,
            authentication,
            snackbarHostState,
            updateState = { transformState ->
                stateLive.update { oldState ->
                    if (oldState is State.DiagnoseScreen.DeviceOwner)
                        oldState.copy(details = transformState(oldState.details))
                    else
                        oldState
                }
            }
        )

        return hasMatchingState.whileTrue {
            ownerStateLive.combine(screenLive) { state, screen ->
                Screen.DeviceOwnerScreen(state, screen, snackbarHostState) as Screen
            }
        }
    }

    private fun getScreen(
        logic: AppLogic,
        state: Flow<OwnerState>,
        scope: CoroutineScope,
        authentication: AuthenticationModelApi,
        snackbarHostState: SnackbarHostState,
        updateState: ((OwnerState) -> OwnerState) -> Unit
    ): Flow<OwnerScreen> = flow<OwnerScreen> {
        val owner = logic.platformIntegration.deviceOwner
        val scopes = owner.delegations

        fun launch(action: suspend () -> Unit) {
            scope.launch {
                try {
                    action()
                } catch (ex: Exception) {
                    snackbarHostState.showSnackbar(logic.context.getString(R.string.error_general))
                }
            }
        }

        val refreshSignal = Channel<Unit>(Channel.CONFLATED)

        val hadUpdateOrganizationNameErrorLive = state.map { it.organizationName == OrganizationName.Error }.distinctUntilChanged()
        val isParentAuthenticatedLive = authentication.authenticatedParentOnly.map { it != null }.distinctUntilChanged()

        val organizationNameLive = state.map { it.organizationName }.distinctUntilChanged().map {
            when (it) {
                is OrganizationName.Modified -> it.value
                is OrganizationName.Error, OrganizationName.Original -> logic.database.config().getCustomOrganizationName() ?: ""
            }
        }

        val actions = OwnerScreen.Normal.Actions(
            updateOrganizationName = { organizationName ->
                updateState { state ->
                    if (state.organizationName == OrganizationName.Error) state
                    else state.copy(organizationName = OrganizationName.Modified(organizationName))
                }

                launch {
                    if (isParentAuthenticatedLive.first()) try {
                        owner.setOrganizationName(organizationName)

                        Threads.database.executeAndWait {
                            logic.database.config().setCustomOrganizationName(organizationName)
                        }
                    } catch (ex: Exception) {
                        updateState { it.copy(organizationName = OrganizationName.Error) }

                        throw ex
                    } else updateState { state ->
                        if (state.organizationName == OrganizationName.Error) state
                        else state.copy(organizationName = OrganizationName.Original)
                    }
                }
            },
            addApp = { packageName ->
                launch {
                    if (authentication.authenticatedParentOnly.first() != null) updateState { state ->
                        state.copy(apps = state.apps + packageName, appListDialog = null)
                    } else updateState { it.copy(appListDialog = null) }
                }
            },
            dismissAppListDialog = {
                updateState { it.copy(appListDialog = null) }
            },
            showAppListDialog = {
                launch {
                    authentication.doParentAuthentication()?.also {
                        updateState { it.copy(appListDialog = OwnerState.AppListDialog()) }
                    }
                }
            },
            updateScopeEnabled = { packageName, scope, enable ->
                launch {
                    if (authentication.authenticatedParentOnly.first() != null) {
                        val current = owner.getDelegations()[packageName] ?: emptySet()
                        val new = if (enable) current + scope else current - scope

                        owner.setDelegations(packageName, new)

                        refreshSignal.send(Unit)
                    } else authentication.doParentAuthentication()
                }
            },
            updateDialogSearch = { filter ->
                updateState {
                    it.copy(appListDialog = it.appListDialog?.copy(filter = filter))
                }
            }
        )

        val appsLive = getApps(
            logic.platformIntegration,
            refreshSignal,
            manualAppsLive = state.map { it.apps },
            extendManualApps = { newApps -> updateState { it.copy(apps = it.apps + newApps) } }
        )

        val dialogLive = getNullableDialog(
            logic.platformIntegration,
            state.map { it.appListDialog }
        )

        emitAll(
            combine(
                appsLive, dialogLive, hadUpdateOrganizationNameErrorLive, isParentAuthenticatedLive, organizationNameLive
            ) { apps, dialog, hadUpdateOrganizationNameError, isParentAuthenticated, organizationName ->
                OwnerScreen.Normal(
                    isParentAuthenticated = isParentAuthenticated,
                    organizationName = organizationName,
                    appListDialog = dialog,
                    scopes = scopes,
                    apps = apps,
                    actions = actions.copy(
                        updateOrganizationName =
                        if (hadUpdateOrganizationNameError || !isParentAuthenticated) null
                        else actions.updateOrganizationName
                    )
                )
            }
        )
    }.catch {
        if (BuildConfig.DEBUG) {
            Log.w(LOG_TAG, "error during generating screen", it)
        }

        emit(OwnerScreen.Error)
    }

    private fun getApps(
        integration: PlatformIntegration,
        refreshSignal: ReceiveChannel<Unit>,
        manualAppsLive: Flow<List<String>>,
        extendManualApps: (List<String>) -> Unit
    ): Flow<List<OwnerScreen.Normal.AppInfo>> {
        val titleAndIconCache = MutableStateFlow(emptyMap<String, Pair<String, Drawable>>())

        fun getTitleAndIcon(packageName: String): Pair<String, Drawable>? {
            return titleAndIconCache.value.get(packageName) ?: run {
                val icon = integration.getAppIcon(packageName)
                val title = integration.getLocalAppTitle(packageName)

                if (icon != null && title != null) titleAndIconCache.updateAndGet {
                    it + Pair(packageName, Pair(title, icon))
                }[packageName]
                else null
            }
        }

        val mapLive = flow {
            while (true) {
                emit(integration.deviceOwner.getDelegations())
                refreshSignal.receive()
            }
        }

        return mapLive.combine(manualAppsLive) { map, manualApps ->
            val newApps = (map.keys - manualApps.toSet()).toList()

            extendManualApps(newApps)

            val allManualApps = manualApps + newApps

            allManualApps.map { packageName ->
                val titleAndIcon = getTitleAndIcon(packageName)

                OwnerScreen.Normal.AppInfo(
                    packageName = packageName,
                    title = titleAndIcon?.first,
                    icon = titleAndIcon?.second,
                    scopes = map[packageName]?.toSet() ?: emptySet()
                )
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun getNullableDialog(
        integration: PlatformIntegration,
        state: Flow<OwnerState.AppListDialog?>
    ): Flow<OwnerScreen.Normal.AppListDialog?> {
        val hasDialog = state.map { it != null }.distinctUntilChanged()

        return hasDialog.transformLatest {
            if (it) emitAll(getDialog(integration, state.filterNotNull()))
            else emit(null)
        }
    }

    private fun getDialog(
        integration: PlatformIntegration,
        state: Flow<OwnerState.AppListDialog>
    ): Flow<OwnerScreen.Normal.AppListDialog> {
        val apps = integration.getLocalApps(IdGenerator.generateId())

        return state.map { dialogState ->
            OwnerScreen.Normal.AppListDialog(
                filter = dialogState.filter,
                apps = apps.filter { app ->
                    dialogState.filter.isEmpty() ||
                            app.packageName.contains(dialogState.filter, ignoreCase = true) ||
                            app.title.contains(dialogState.filter, ignoreCase = true)
                }
            )
        }
    }
}