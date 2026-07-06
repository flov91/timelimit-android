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
package io.timelimit.android.ui.model.managechild

import androidx.compose.material.SnackbarHostState
import androidx.lifecycle.asFlow
import io.timelimit.android.R
import io.timelimit.android.data.extensions.sortedCategories
import io.timelimit.android.data.model.ExperimentalFlags
import io.timelimit.android.data.model.UserType
import io.timelimit.android.date.DateInTimezone
import io.timelimit.android.integration.platform.BatteryStatus
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.RealTime
import io.timelimit.android.logic.blockingreason.CategoryHandlingCache
import io.timelimit.android.sync.actions.UpdateCategoryDisableLimitsAction
import io.timelimit.android.sync.actions.UpdateCategorySortingAction
import io.timelimit.android.sync.actions.UpdateCategoryTemporarilyBlockedAction
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import io.timelimit.android.ui.manage.child.category.specialmode.SpecialModeDialogMode
import io.timelimit.android.ui.model.ActivityCommand
import io.timelimit.android.ui.model.AuthenticationModelApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Serializable
import kotlin.time.Duration.Companion.milliseconds

object ManageChildCategoryList {
    data class State(
        val childId: String
    ): Serializable

    data class Screen(
        val appListSyncConsent: AppListSyncConsent?,
        val hasDeviceManipulation: Boolean,
        val categories: List<CategoryItem>,
        val addCategory: () -> Unit
    ) {
        data class AppListSyncConsent(val open: () -> Unit)
    }

    data class CategoryItem(
        val id: String,
        val name: String,
        val isBlockedTimeNow: Boolean,
        val remainingTimeToday: Long?,
        val usedTimeToday: Long,
        val usedForNotAssignedApps: Boolean,
        val categoryNestingLevel: Int,
        val mode: CategorySpecialMode,
        val open: () -> Unit,
        val modify: () -> Unit,
        val moveTo: (String) -> Unit
    )

    sealed class CategorySpecialMode {
        object None: CategorySpecialMode()
        data class TemporarilyBlocked(val endTime: Long?): CategorySpecialMode()
        data class TemporarilyAllowed(val endTime: Long): CategorySpecialMode()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun handle(
        childId: String,
        logic: AppLogic,
        activityCommand: SendChannel<ActivityCommand>,
        snackbarHostState: SnackbarHostState,
        authentication: AuthenticationModelApi,
        scope: CoroutineScope,
        open: (String) -> Unit
    ): Flow<Screen> = flow {
        val mutex = Mutex()

        fun launch(action: suspend () -> Unit) {
            scope.launch {
                try {
                    mutex.withLock { action() }
                } catch (_: Exception) {
                    snackbarHostState.showSnackbar(logic.context.getString(R.string.error_general))
                }
            }
        }

        val showSyncConsentBannerLive = logic.syncAppsLogic.shouldAskForConsent.asFlow()
        val showManipulationWarningLive = showManipulationWarning(logic, childId)
        val categoryItemsLive = getCategoryItems(logic, authentication, childId, open, ::launch, activityCommand)

        val appListSyncConsent = Screen.AppListSyncConsent(
            open = {
                launch {
                    activityCommand.send(ActivityCommand.ShowSyncConsentDialog)
                }
            }
        )

        emitAll(combine(showManipulationWarningLive, showSyncConsentBannerLive, categoryItemsLive) { showManipulationWarning, showSyncConsentBanner, categoryItems ->
            Screen(
                appListSyncConsent = if (showSyncConsentBanner) appListSyncConsent else null,
                hasDeviceManipulation = showManipulationWarning,
                categories = categoryItems,
                addCategory = {
                    launch {
                        authentication.doParentOrChildAuthentication(childId)?.also {
                            activityCommand.send(ActivityCommand.ShowCreateCategoryDialog(childId))
                        }
                    }
                }
            )
        })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun showManipulationWarning(logic: AppLogic, childId: String): Flow<Boolean> {
        val suppressChildDeviceManipulationLive = logic.database.config().isExperimentalFlagsSetFlow(
            ExperimentalFlags.HIDE_MANIPULATION_WARNING
        )

        val hasChildDeviceWithManipulationLive = logic.database.device().getDevicesByUserIdFlow(childId).map { devices ->
            devices.any { it.hasAnyManipulation }
        }

        return suppressChildDeviceManipulationLive.transformLatest { suppressChildDeviceManipulation ->
            if (suppressChildDeviceManipulation) emit(false)
            else emitAll(hasChildDeviceWithManipulationLive)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun getCategoryItems(
        logic: AppLogic,
        authentication: AuthenticationModelApi,
        childId: String,
        open: (String) -> Unit,
        launch: (suspend () -> Unit) -> Unit,
        activityCommand: SendChannel<ActivityCommand>
    ): Flow<List<CategoryItem>> = flow {
        val categoryHandlingCache = CategoryHandlingCache()
        val realTime = RealTime.newInstance()

        val userRelatedDataLive = logic.database.derivedDataDao().getUserRelatedDataLive(childId).asFlow()

        emitAll(userRelatedDataLive
            .filterNotNull() // something should close the screen otherwise
            .combine(logic.realTimeLogic.timeModificationCounterFlow) { a, _ -> a } // restart after clock changes
            .transformLatest { userRelatedData ->
                while (true) {
                    logic.realTimeLogic.getRealTime(realTime)

                    val timezone = userRelatedData.timeZone

                    val date = DateInTimezone.newInstance(realTime.timeInMillis, timezone)

                    val categories = userRelatedData.sortedCategories()

                    categoryHandlingCache.reportStatus(
                        user = userRelatedData,
                        timeInMillis = realTime.timeInMillis,
                        batteryStatus = BatteryStatus.assumeFull,
                        shouldTrustTimeTemporarily = realTime.shouldTrustTimeTemporarily,
                        assumeCurrentDevice = true,
                        currentNetworkId = null, // not relevant here
                        hasPremiumOrLocalMode = false   // not relevant here
                    )

                    var validUntil = Long.MAX_VALUE

                    emit(categories.map { (nestingLevel, category) ->
                        val handling = categoryHandlingCache.get(category.category.id)
                        val usedForNotAssignedApps =
                            category.category.id == userRelatedData.user.categoryForNotAssignedApps

                        val usedTimeToday = category.usedTimes
                            .filter { it.dayOfEpoch == date.dayOfEpoch }
                            .map { it.usedMillis }
                            .maxOrNull() ?: 0

                        val mode =
                            if (!handling.okByTempBlocking) CategorySpecialMode.TemporarilyBlocked(
                                // TODO: remove unlimited temporarily blocking
                                endTime = category.category.temporarilyBlockedEndTime.let { if (it == 0L) null else it }
                            )
                            else if (handling.areLimitsTemporarilyDisabled)
                                CategorySpecialMode.TemporarilyAllowed(endTime = category.category.disableLimitsUntil)
                            else CategorySpecialMode.None

                        val relatedCategoryIds by lazy {
                            categories
                                .filter {
                                    it.first == nestingLevel &&
                                            (it.first == 0 || it.second.category.parentCategoryId == category.category.parentCategoryId)
                                }
                                .map { it.second.category.id }
                        }

                        validUntil = validUntil.coerceAtMost(handling.dependsOnMaxTime)

                        CategoryItem(
                            id = category.category.id,
                            name = category.category.title,
                            isBlockedTimeNow = !handling.okByBlockedTimeAreas,
                            remainingTimeToday = handling.remainingTime?.includingExtraTime,
                            usedTimeToday = usedTimeToday,
                            usedForNotAssignedApps = usedForNotAssignedApps,
                            categoryNestingLevel = nestingLevel,
                            mode = mode,
                            open = { open(category.category.id) },
                            modify = {
                                launch {
                                    val permitChildAuth = when (mode) {
                                        is CategorySpecialMode.TemporarilyAllowed -> true
                                        is CategorySpecialMode.TemporarilyBlocked -> mode.endTime != null
                                        CategorySpecialMode.None -> true
                                    }

                                    val (user, auth) = if (permitChildAuth) {
                                        authentication.doParentOrChildAuthentication(childId)
                                            ?.let {
                                                Pair(it.user, it.authentication)
                                            } ?: return@launch
                                    } else {
                                        authentication.doParentAuthentication()?.let {
                                            Pair(it.user, it.authentication)
                                        } ?: return@launch
                                    }

                                    if (mode is CategorySpecialMode.None) {
                                        activityCommand.send(ActivityCommand.ShowCategorySpecialModeDialog(
                                            childId = childId,
                                            categoryId = category.category.id,
                                            mode = if (user.type == UserType.Parent) SpecialModeDialogMode.Regular else SpecialModeDialogMode.SelfLimitAdd
                                        ))
                                    } else {
                                        if (user.type == UserType.Parent) {
                                            val disableActions = listOf(
                                                UpdateCategoryTemporarilyBlockedAction(
                                                    categoryId = category.category.id,
                                                    endTime = null,
                                                    blocked = false
                                                ),
                                                UpdateCategoryDisableLimitsAction(
                                                    categoryId = category.category.id,
                                                    endTime = 0
                                                )
                                            )

                                            for (action in disableActions) {
                                                ApplyActionUtil.applyParentAction(action, auth, logic)
                                            }
                                        } else {
                                            activityCommand.send(ActivityCommand.ShowCategorySpecialModeDialog(
                                                childId = childId,
                                                categoryId = category.category.id,
                                                mode = SpecialModeDialogMode.SelfLimitAdd
                                            ))
                                        }
                                    }
                                }
                            },
                            moveTo = { otherCategoryId ->
                                val sourceIndex = relatedCategoryIds.indexOf(category.category.id)
                                val targetIndex = relatedCategoryIds.indexOf(otherCategoryId)

                                if (targetIndex == -1 || sourceIndex == targetIndex) {
                                    return@CategoryItem
                                }

                                if (targetIndex >= relatedCategoryIds.size) return@CategoryItem

                                val updatedRelatedCategories = relatedCategoryIds.toMutableList()

                                updatedRelatedCategories.add(targetIndex, updatedRelatedCategories.removeAt(sourceIndex))

                                launch {
                                    authentication.doParentAuthentication()?.also { auth ->
                                        ApplyActionUtil.applyParentAction(
                                            UpdateCategorySortingAction(
                                                categoryIds = updatedRelatedCategories
                                            ),
                                            auth.authentication,
                                            logic
                                        )
                                    }
                                }
                            }
                        )
                    })

                    if (validUntil == Long.MAX_VALUE) {
                        // no auto refresh by time

                        break
                    }

                    val remainingValidDuration = if (validUntil > realTime.timeInMillis) {
                        (validUntil - realTime.timeInMillis)
                    } else {
                        0
                    }

                    // do not update more than once per second
                    delay(remainingValidDuration.coerceAtLeast(1000).milliseconds)
                }
            }
        )
    }
}