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
import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.HintsToShow
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.ui.manage.category.blocked_times.BlockedTimesData
import io.timelimit.android.ui.model.ActivityCommand
import io.timelimit.android.ui.model.AuthenticationModelApi
import io.timelimit.android.ui.model.BackStackItem
import io.timelimit.android.ui.model.Screen
import io.timelimit.android.ui.model.State
import io.timelimit.android.ui.model.Title
import io.timelimit.android.ui.model.flow.Case
import io.timelimit.android.ui.model.flow.splitConflated
import io.timelimit.android.ui.model.intro.IntroHandling
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.*

object ManageCategoryHandling {
    fun processState(
        logic: AppLogic,
        activityCommand: SendChannel<ActivityCommand>,
        authentication: AuthenticationModelApi,
        stateLive: Flow<State.ManageChild.ManageCategory>,
        parentBackStackLive: Flow<List<BackStackItem>>,
        updateState: ((State.ManageChild.ManageCategory) -> State) -> Unit
    ): Flow<Screen> = stateLive.splitConflated(
        Case.withKey<_, _, State.ManageChild.ManageCategory, _>(
            withKey = { Pair(it.childId, it.categoryId) },
            producer = { (childId, categoryId), state ->
                val categoryLive = logic.database.category().getCategoryByIdFlow(categoryId)

                val hasCategoryLive = categoryLive.map { it?.childId == childId }.distinctUntilChanged()
                val foundCategoryLive = categoryLive.filterNotNull()

                hasCategoryLive.transformLatest { hasCategory ->
                    if (hasCategory) emitAll(
                        state.splitConflated(
                            Case.simple<_, _, State.ManageChild.ManageCategory.Main> { processMainState(it, parentBackStackLive, foundCategoryLive) },
                            Case.simple<_, _, State.ManageChild.ManageCategory.Sub> { processSubState(childId, categoryId, logic, activityCommand, authentication, share(it), parentBackStackLive, foundCategoryLive, updateMethod(updateState)) },
                        )
                    )
                    else updateState { it.previousChild }
                }
            }
        )
    )

    private fun processMainState(
        stateLive: Flow<State.ManageChild.ManageCategory.Main>,
        parentBackStackLive: Flow<List<BackStackItem>>,
        categoryLive: Flow<Category>
    ): Flow<Screen> = combine(stateLive, categoryLive, parentBackStackLive) { state, category, backStack ->
        Screen.ManageCategory(
            state,
            state.toolbarIcons,
            state.toolbarOptions,
            state,
            category.title,
            backStack
        )
    }

    private fun processSubState(
        childId: String,
        categoryId: String,
        logic: AppLogic,
        activityCommand: SendChannel<ActivityCommand>,
        authentication: AuthenticationModelApi,
        stateLive: SharedFlow<State.ManageChild.ManageCategory.Sub>,
        parentBackStackLive: Flow<List<BackStackItem>>,
        categoryLive: Flow<Category>,
        updateState: ((State.ManageChild.ManageCategory.Sub) -> State) -> Unit
    ): Flow<Screen> {
        val subBackStackLive = combine(stateLive, parentBackStackLive, categoryLive) { state, baseBackStack, category ->
            baseBackStack + BackStackItem(Title.Plain(category.title)) { updateState { state.previousCategory } }
        }

        return stateLive.splitConflated(
            Case.simple<_, _, State.ManageChild.ManageCategory.Advanced> { processAdvancedState(it, subBackStackLive) },
            Case.simple<_, _, State.ManageChild.ManageCategory.BlockedTimes> { processBlockedTimesState(childId, categoryId, logic, activityCommand, authentication, scope, categoryLive, share(it), subBackStackLive, updateMethod(updateState)) },
        )
    }

    private fun processAdvancedState(
        stateLive: Flow<State.ManageChild.ManageCategory.Advanced>,
        parentBackStackLive: Flow<List<BackStackItem>>
    ): Flow<Screen> = combine(stateLive, parentBackStackLive) { state, backStack ->
        Screen.ManageCategoryAdvanced(
            state,
            state.toolbarIcons,
            state.toolbarOptions,
            state,
            backStack
        )
    }

    private fun processBlockedTimesState(
        childId: String,
        categoryId: String,
        logic: AppLogic,
        activityCommand: SendChannel<ActivityCommand>,
        authentication: AuthenticationModelApi,
        scope: CoroutineScope,
        categoryLive: Flow<Category>,
        stateLive: SharedFlow<State.ManageChild.ManageCategory.BlockedTimes>,
        parentBackStackLive: Flow<List<BackStackItem>>,
        updateState: ((State.ManageChild.ManageCategory.BlockedTimes) -> State) -> Unit
    ): Flow<Screen> = flow {
        val snackbarHostState = SnackbarHostState()

        val traditionalBlockedTimeAreasLive =
            categoryLive
                .map { BlockedTimesData(BlockedTimesData.RangeList.fromBitmask(it.blockedMinutesInWeek)) }

        val ruleBasedBlockedTimeAreasLive = logic.database.timeLimitRules().getTimeLimitRulesByCategoryFlow(categoryId).map {
            BlockedTimesData.fromRules(it)
        }

        val blockedTimeAreasLive = combine(traditionalBlockedTimeAreasLive, ruleBasedBlockedTimeAreasLive) { a, b ->
            a.union(b)
        }.shareIn(scope, SharingStarted.WhileSubscribed(1000), 1)

        val nestedLive = ManageCategoryBlockedTimes.handle(
            childId = childId,
            categoryId = categoryId,
            logic = logic,
            scope = scope,
            snackbarHostState = snackbarHostState,
            authentication = authentication,
            blockedTimeAreasLive = blockedTimeAreasLive,
            stateLive = stateLive.map { it.details },
            updateState = { modifier -> updateState { parentState ->
                parentState.copy(details = modifier(parentState.details))
            }}
        )

        val introLive = IntroHandling.handle(logic, HintsToShow.BLOCKED_TIME_AREAS)

        emitAll(combine(stateLive, parentBackStackLive, nestedLive, introLive) { state, backStack, nested, intro ->
            Screen.ManageBlockedTimes(
                state,
                IntroHandling.toolbarIcons(intro),
                state.toolbarOptions,
                nested,
                intro,
                backStack,
                snackbarHostState
            )
        })
    }
}