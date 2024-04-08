/*
 * TimeLimit Copyright <C> 2019 - 2024 Jonas Lochmann
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

import android.util.Log
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.SnackbarResult
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.data.customtypes.ImmutableBitmask
import io.timelimit.android.data.model.TimeLimitRule
import io.timelimit.android.data.model.UserType
import io.timelimit.android.extensions.sorted
import io.timelimit.android.extensions.tryWithLock
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.sync.actions.CreateTimeLimitRuleAction
import io.timelimit.android.sync.actions.DeleteTimeLimitRuleAction
import io.timelimit.android.sync.actions.ParentAction
import io.timelimit.android.sync.actions.UpdateCategoryBlockedTimesAction
import io.timelimit.android.sync.actions.UpdateTimeLimitRuleAction
import io.timelimit.android.sync.actions.apply.ApplyActionChildAddLimitAuthentication
import io.timelimit.android.sync.actions.apply.ApplyActionParentAuthentication
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import io.timelimit.android.ui.manage.category.blocked_times.BlockedTimesData
import io.timelimit.android.ui.model.AuthenticationModelApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.io.Serializable
import kotlin.experimental.or

object ManageCategoryBlockedTimes {
    private const val LOG_TAG = "ManageBlockedTimes"

    sealed class State: Serializable {
        companion object {
            val initial = Editing()
        }

        val editing get() = if (this is Editing) this else initial

        data class Editing(
            val expandedHourOfWeek: Int? = null,
            val selectedMinuteOfWeek: Int? = null
        ): State()

        data class CopyBlockedTimes(
            val from: Int,
            val to: Set<Int> = emptySet()
        ): State() {
            init {
                if (to.contains(from)) throw IllegalStateException()
            }
        }
    }

    sealed class Screen (
        val blockedTimeAreas: BlockedTimesData,
        val onBackPressed: (() -> Unit)?
    ) {
        class Editing(
            blockedTimeAreas: BlockedTimesData,
            val expandedHourOfWeek: Int?,
            val selectedMinuteOfWeek: Int?,
            val onHourClick: (Int) -> Unit,
            val onMinuteClick: (Int) -> Unit,
            val onCopyClicked: (Int) -> Unit,
            onBackPressed: (() -> Unit)?
        ): Screen(blockedTimeAreas, onBackPressed)

        class Copying(
            blockedTimeAreas: BlockedTimesData,
            val from: Int,
            val to: Set<Int>,
            onBackPressed: () -> Unit,
            val onToggleDay: (Int) -> Unit,
            val onConfirmClicked: () -> Unit
        ): Screen(blockedTimeAreas, onBackPressed)
    }

    fun handle(
        childId: String,
        categoryId: String,
        logic: AppLogic,
        scope: CoroutineScope,
        snackbarHostState: SnackbarHostState,
        authentication: AuthenticationModelApi,
        blockedTimeAreasLive: SharedFlow<BlockedTimesData>,
        stateLive: Flow<State>,
        updateState: ((State) -> State) -> Unit
    ): Flow<Screen> = flow {
        val asyncMutex = Mutex()

        fun launch(action: suspend () -> Unit) {
            scope.launch {
                try {
                    action()
                } catch (ex: Exception) {
                    snackbarHostState.showSnackbar(logic.context.getString(R.string.error_general))
                }
            }
        }

        suspend fun setBlockedTimes(
            data: BlockedTimesData,
            authentication: ApplyActionParentAuthentication
        ) {
            suspend fun apply(action: ParentAction) {
                ApplyActionUtil.applyParentAction(action, authentication, logic)
            }

            val isChild = authentication is ApplyActionChildAddLimitAuthentication
            val targetRules = data.toRules(categoryId)
            val currentBlockedTimes = logic.database.category().getCategoryByIdFlow(categoryId).first()?.blockedMinutesInWeek ?: return
            val currentRules = logic.database.timeLimitRules().getTimeLimitRulesByCategoryCoroutine(categoryId)
                .filter { it.likeBlockedTimeArea && it.expiresAt == null }

            val rulesToDelete = mutableListOf<TimeLimitRule>()
            val currentRulesToEditEventually = mutableMapOf<BlockedTimesData.Range, TimeLimitRule>()

            for (rule in currentRules) {
                val range = BlockedTimesData.Range(rule.startMinuteOfDay, rule.endMinuteOfDay)

                if (targetRules.containsKey(range) && !currentRulesToEditEventually.containsKey(range)) currentRulesToEditEventually[range] = rule
                else if (!isChild) rulesToDelete.add(rule)
            }

            val rulesToCreate = mutableListOf<TimeLimitRule>()
            val rulesToUpdate = mutableListOf<TimeLimitRule>()

            for (rule in targetRules) {
                val currentRule = currentRulesToEditEventually[rule.key]

                if (currentRule == null) {
                    val existingRuleId = rulesToDelete.removeLastOrNull()?.id

                    if (existingRuleId == null) rulesToCreate.add(rule.value)
                    else rulesToUpdate.add(rule.value.copy(id = existingRuleId))
                } else {
                    val targetDayMask =
                        if (isChild) currentRule.dayMask or rule.value.dayMask
                        else rule.value.dayMask

                    if (currentRule.dayMask != targetDayMask) rulesToUpdate.add(
                        currentRule.copy(dayMask = targetDayMask)
                    )
                }
            }

            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "isChild: $isChild")
                Log.d(LOG_TAG, "targetRules: $targetRules")
                Log.d(LOG_TAG, "rulesToCreate: $rulesToCreate")
                Log.d(LOG_TAG, "rulesToUpdate: $rulesToUpdate")
                Log.d(LOG_TAG, "rulesToDelete: $rulesToDelete")
            }

            for (rule in rulesToCreate) apply(CreateTimeLimitRuleAction(rule))
            for (rule in rulesToUpdate) apply(UpdateTimeLimitRuleAction(
                rule.id,
                rule.dayMask,
                rule.maximumTimeInMillis,
                rule.applyToExtraTimeUsage,
                rule.startMinuteOfDay,
                rule.endMinuteOfDay,
                rule.sessionDurationMilliseconds,
                rule.sessionPauseMilliseconds,
                rule.perDay,
                rule.expiresAt
            ))
            for (rule in rulesToDelete) apply(DeleteTimeLimitRuleAction(rule.id))

            // remove legacy data
            if (!currentBlockedTimes.dataNotToModify.isEmpty && !isChild) apply(
                UpdateCategoryBlockedTimesAction(categoryId, ImmutableBitmask.empty)
            )
        }

        val onHourClick: (Int) -> Unit = { hour ->
            updateState { oldState ->
                oldState.editing.copy(
                    expandedHourOfWeek =
                    if (oldState.editing.expandedHourOfWeek == hour) null
                    else hour
                )
            }
        }

        val onMinuteClick: (Int) -> Unit = { minute -> launch { asyncMutex.tryWithLock {
            val authenticationData = authentication.doParentOrChildAuthentication(childId) ?: return@tryWithLock
            val isChild = authenticationData.user.type == UserType.Child
            val selectedMinuteOfWeek = stateLive.first().editing.selectedMinuteOfWeek

            if (selectedMinuteOfWeek == null) {
                if (isChild) launch {
                    snackbarHostState.showSnackbar(
                        logic.context.getString(R.string.blocked_time_areas_snackbar_child_hint)
                    )
                }

                updateState { it.editing.copy(selectedMinuteOfWeek = minute) }
            } else {
                val range = Pair(selectedMinuteOfWeek, minute).sorted().let { (from, to) -> BlockedTimesData.Range(from, to) }
                val oldBlockedTimeAreas = blockedTimeAreasLive.first()

                val willBlockRange = isChild || oldBlockedTimeAreas.ranges.readFrom(range.first).countSetBits(range.last - range.first + 1) <= (range.last - range.first) / 2

                val newBlockedTimeAreas = oldBlockedTimeAreas.withUpdatedRange(range, willBlockRange)

                setBlockedTimes(newBlockedTimeAreas, authenticationData.authentication)

                updateState { it.editing.copy(selectedMinuteOfWeek = null) }

                launch {
                    val result = snackbarHostState.showSnackbar(
                        logic.context.getString(R.string.blocked_time_areas_snackbar_modified),
                        if (isChild) null else logic.context.getString(R.string.generic_undo),
                        SnackbarDuration.Short
                    )

                    if (result == SnackbarResult.ActionPerformed) setBlockedTimes(oldBlockedTimeAreas, authenticationData.authentication)
                }
            }
        }}}

        val onCopyClicked: (Int) -> Unit = { fromDay -> launch { asyncMutex.tryWithLock {
            authentication.doParentAuthentication() ?: return@tryWithLock

            updateState { State.CopyBlockedTimes(fromDay) }
        }}}

        val onToggleDay: (Int) -> Unit = { day -> updateState { oldState ->
            if (oldState is State.CopyBlockedTimes) oldState.copy(
                to = if (oldState.to.contains(day)) oldState.to - day
                else oldState.to + day
            )
            else oldState
        }}

        val onConfirmClicked: () -> Unit = { launch { asyncMutex.tryWithLock {
            val authenticationData = authentication.authenticatedParentOnly.first() ?: run {
                authentication.triggerAuthenticationScreen()

                return@tryWithLock
            }

            val oldBlockedTimeAreas = blockedTimeAreasLive.first()
            val state = stateLive.first()

            if (state is State.CopyBlockedTimes && state.to.isNotEmpty()) {
                val newBlockedTimeAreas = oldBlockedTimeAreas.withConfigCopiedToOtherDates(state.from, state.to)

                setBlockedTimes(newBlockedTimeAreas, authenticationData.authentication)

                updateState { it.editing.copy(selectedMinuteOfWeek = null) }

                launch {
                    val result = snackbarHostState.showSnackbar(
                        logic.context.getString(R.string.blocked_time_areas_snackbar_modified),
                        logic.context.getString(R.string.generic_undo),
                        SnackbarDuration.Short
                    )

                    if (result == SnackbarResult.ActionPerformed) setBlockedTimes(oldBlockedTimeAreas, authenticationData.authentication)
                }
            }

            updateState { oldState ->
                if (oldState is State.CopyBlockedTimes) State.initial
                else oldState
            }
        }}}

        emitAll(combine(blockedTimeAreasLive, stateLive) { blockedTimeAreas, state ->
            when (state) {
                is State.Editing -> {
                    val onBackPressed =
                        if (state.expandedHourOfWeek != null) ({ updateState { it.editing.copy(expandedHourOfWeek = null) } })
                        else if (state.selectedMinuteOfWeek != null) ({ updateState { it.editing.copy(selectedMinuteOfWeek = null) } })
                        else null

                    Screen.Editing(
                        blockedTimeAreas = blockedTimeAreas,
                        expandedHourOfWeek = state.expandedHourOfWeek,
                        selectedMinuteOfWeek = state.selectedMinuteOfWeek,
                        onHourClick = onHourClick,
                        onMinuteClick = onMinuteClick,
                        onCopyClicked = onCopyClicked,
                        onBackPressed = onBackPressed
                    )
                }
                is State.CopyBlockedTimes -> {
                    val onBackPressed: () -> Unit = { updateState { oldState ->
                        if (oldState is State.CopyBlockedTimes) State.initial
                        else oldState
                    }}

                    Screen.Copying(
                        blockedTimeAreas = blockedTimeAreas,
                        from = state.from,
                        to = state.to,
                        onBackPressed = onBackPressed,
                        onToggleDay = onToggleDay,
                        onConfirmClicked = onConfirmClicked
                    )
                }
            }
        })
    }
}