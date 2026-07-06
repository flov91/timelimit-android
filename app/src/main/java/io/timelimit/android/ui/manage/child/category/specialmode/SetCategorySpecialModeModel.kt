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

package io.timelimit.android.ui.manage.child.category.specialmode

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.*
import io.timelimit.android.R
import io.timelimit.android.livedata.liveDataFromFunction
import io.timelimit.android.livedata.liveDataFromNonNullValue
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.UpdateCategoryDisableLimitsAction
import io.timelimit.android.sync.actions.UpdateCategoryTemporarilyBlockedAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.model.managechild.ManageChildCategoryList
import java.time.LocalDate

class SetCategorySpecialModeModel(application: Application): AndroidViewModel(application) {
    private val logic = DefaultAppLogic.with(application)
    private val requestClose = MutableLiveData<Boolean>().apply { value = false }
    private val durationSelectionLive = MutableLiveData<DurationSelection>().apply { value = DurationSelection.SuggestionList }
    private val typeLive = MutableLiveData<Type?>().apply { value = null }
    private var didInit = false
    private val childAndCategoryId = MutableLiveData<Pair<String, String>>()
    private val specialMode = MutableLiveData<SpecialModeDialogMode>()

    fun now() = logic.realTimeLogic.getCurrentTimeInMillis()

    val nowLive = liveDataFromFunction { now() }
    val hasPremium = logic.fullVersion.shouldProvideFullVersionFunctions

    private val userRelatedData = childAndCategoryId.switchMap { (childId, categoryId) ->
        logic.database.derivedDataDao().getUserRelatedDataLive(childId).map {
            if (it == null) null else it to categoryId
        }
    }

    private val selfLimitAddModeMinTimestamp = userRelatedData.switchMap { data ->
        if (data == null) liveDataFromNonNullValue(Long.MAX_VALUE) else {
            val (userRelatedData, categoryId) = data
            val category = userRelatedData.categoryById.get(categoryId)

            if (category == null)
                liveDataFromNonNullValue(Long.MAX_VALUE)
            else if (category.category.temporarilyBlocked) {
                if (category.category.temporarilyBlockedEndTime == 0L)
                    liveDataFromNonNullValue(Long.MAX_VALUE)
                else
                    nowLive.map { now -> now.coerceAtLeast(category.category.temporarilyBlockedEndTime) }
            } else nowLive
        }
    }

    val minTimestamp = specialMode.switchMap { mode ->
        if (mode == SpecialModeDialogMode.SelfLimitAdd) selfLimitAddModeMinTimestamp else nowLive
    }

    val content: LiveData<Content?> = object: MediatorLiveData<Content>() {
        var didLoadUserRelatedData = false

        init {
            addSource(requestClose) { update() }
            addSource(durationSelectionLive) { update() }
            addSource(typeLive) { update() }
            addSource(userRelatedData) { didLoadUserRelatedData = true; update() }
            addSource(specialMode) { update() }
        }

        fun update() {
            if (requestClose.value == true) {
                value = null; return
            }

            if (!didLoadUserRelatedData) return

            val durationSelection = durationSelectionLive.value!!
            val type = typeLive.value
            val (userRelatedData, categoryId) = userRelatedData.value ?: run { value = null; return }
            val specialMode = specialMode.value ?: return

            val targetCategory = userRelatedData.categoryById[categoryId] ?: run { value = null; return }
            val categoryTitle = targetCategory.category.title

            if (
                targetCategory.category.temporarilyBlocked && targetCategory.category.temporarilyBlockedEndTime == 0L &&
                specialMode == SpecialModeDialogMode.SelfLimitAdd
            ) {
                value = null; return
            }

            val screen = if (type == null) Screen.SelectType else when (durationSelection) {
                DurationSelection.SuggestionList -> when (type) {
                    Type.BlockTemporarily -> {
                        if (specialMode == SpecialModeDialogMode.SelfLimitAdd) SpecialModeDuration.items
                        else listOf(SpecialModeOption.NoEndTimeOption) + SpecialModeDuration.items
                    }
                    Type.DisableLimits -> SpecialModeDuration.items
                }.let { options ->
                    Screen.WithType.SuggestionList(
                            type = type,
                            options = options
                    )
                }
                is DurationSelection.Clock -> Screen.WithType.ClockScreen(type = type, date = durationSelection.date)
                DurationSelection.Calendar -> Screen.WithType.CalendarScreen(type = type)
            }

            value = Content(
                    categoryTitle = categoryTitle,
                    categoryId = categoryId,
                    childTimezone = userRelatedData.user.timeZone,
                    screen = screen
            )
        }
    }

    fun selectType(type: Type) { typeLive.value = type }
    fun openClockScreen(date: LocalDate? = null) { durationSelectionLive.value = DurationSelection.Clock(date) }
    fun openCalendarScreen() { durationSelectionLive.value = DurationSelection.Calendar }

    fun goBack(): Boolean = if (durationSelectionLive.value != DurationSelection.SuggestionList) {
        val v = durationSelectionLive.value

        durationSelectionLive.value =
            if (v is DurationSelection.Clock && v.date != null) DurationSelection.Calendar
            else DurationSelection.SuggestionList

        true
    } else if (
        typeLive.value != null &&
        specialMode.value != SpecialModeDialogMode.DisableLimitsOnly
    ) {
        typeLive.value = null

        true
    } else {
        false
    }

    fun applySelection(selection: SpecialModeOption, auth: ActivityViewModel, callback: (ManageChildCategoryList.CategorySpecialMode.NotNone) -> Unit) {
        val content = content.value
        val screen = content?.screen
        val specialMode = specialMode.value ?: return

        if (screen is Screen.WithType) {
            when (selection) {
                is SpecialModeOption.UntilTimeOption -> openClockScreen()
                is SpecialModeOption.UntilDateOption -> openCalendarScreen()
                is SpecialModeOption.Duration -> when (screen.type) {
                    Type.BlockTemporarily -> {
                        val endTime = selection.getTime(
                                currentTimestamp = now(),
                                timezone = content.childTimezone
                        )

                        if (specialMode == SpecialModeDialogMode.SelfLimitAdd) {
                            val minTime = minTimestamp.value ?: return

                            if (endTime < minTime) {
                                Toast.makeText(getApplication(), R.string.manage_disable_time_limits_toast_time_not_increased_but_child_mode, Toast.LENGTH_SHORT).show()

                                return
                            }
                        }

                        auth.tryDispatchParentAction(
                                action = UpdateCategoryTemporarilyBlockedAction(
                                        categoryId = content.categoryId,
                                        endTime = endTime,
                                        blocked = true
                                ),
                                allowAsChild = specialMode == SpecialModeDialogMode.SelfLimitAdd
                        )

                        callback(ManageChildCategoryList.CategorySpecialMode.TemporarilyBlocked(endTime))

                        requestClose.value = true
                    }
                    Type.DisableLimits -> {
                        val endTime = selection.getTime(
                                currentTimestamp = now(),
                                timezone = content.childTimezone
                        )

                        auth.tryDispatchParentAction(
                                UpdateCategoryDisableLimitsAction(
                                        categoryId = content.categoryId,
                                        endTime = endTime
                                )
                        )

                        callback(ManageChildCategoryList.CategorySpecialMode.TemporarilyAllowed(endTime))

                        requestClose.value = true
                    }
                }.let {/* require handling all paths */ }
                SpecialModeOption.NoEndTimeOption -> when (screen.type) {
                    Type.BlockTemporarily -> {
                        auth.tryDispatchParentAction(
                                action = UpdateCategoryTemporarilyBlockedAction(
                                        categoryId = content.categoryId,
                                        endTime = null,
                                        blocked = true
                                ),
                                allowAsChild = false
                        )

                        callback(ManageChildCategoryList.CategorySpecialMode.TemporarilyBlocked(null))

                        requestClose.value = true
                    }
                    Type.DisableLimits -> throw IllegalArgumentException()
                }.let {/* require handling all paths */ }
            }.let {/* require handling all paths */ }
        }
    }

    fun applySelection(timeInMillis: Long, auth: ActivityViewModel, callback: (ManageChildCategoryList.CategorySpecialMode.NotNone) -> Unit) = applySelection(
        selection = SpecialModeOption.Duration.FixedEndTime(timeInMillis),
        auth = auth,
        callback = callback
    )

    fun init(childId: String, categoryId: String, mode: SpecialModeDialogMode) {
        if (!didInit) {
            didInit = true

            childAndCategoryId.value = childId to categoryId
            specialMode.value = mode

            if (mode == SpecialModeDialogMode.DisableLimitsOnly) {
                selectType(Type.DisableLimits)
            }
        }
    }

    enum class Type {
        BlockTemporarily,
        DisableLimits
    }

    internal sealed class DurationSelection {
        object SuggestionList: DurationSelection()
        data class Clock(val date: LocalDate?): DurationSelection()
        object Calendar: DurationSelection()
    }

    data class Content(
            val categoryTitle: String,
            val categoryId: String,
            val childTimezone: String,
            val screen: Screen
    )

    sealed class Screen {
        object SelectType: Screen()

        sealed class WithType: Screen() {
            abstract val type: Type

            data class ClockScreen(override val type: Type, val date: LocalDate?): WithType()
            data class CalendarScreen(override val type: Type): WithType()

            data class SuggestionList(
                    override val type: Type,
                    val options: List<SpecialModeOption>
            ): WithType()

        }
    }
}