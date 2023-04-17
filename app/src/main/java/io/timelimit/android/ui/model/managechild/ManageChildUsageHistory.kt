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
package io.timelimit.android.ui.model.managechild

import io.timelimit.android.data.model.UsedTimeListItem
import io.timelimit.android.logic.AppLogic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import java.io.Serializable

object ManageChildUsageHistory {
    data class State(val categoryId: String? = null): Serializable

    data class Screen(
        val categories: List<Category>,
        val currentCategory: Category?,
        val items: List<UsedTimeListItem>,
        val selectCategory: (Category?) -> Unit
    ) {
        data class Category(val id: String, val name: String)
    }

    fun handle(
        logic: AppLogic,
        childId: String,
        stateLive: Flow<State>,
        updateState: ((State) -> State) -> Unit
    ): Flow<Screen> = flow {
        val categoriesLive = logic.database.category()
            .getCategoriesByChildIdFlow(childId)
            .map { categories ->
                categories.map { Screen.Category(it.id, it.title) }
            }

        val stateAndCategories = combine(stateLive, categoriesLive) { a, b -> Pair(a, b) }

        emitAll(stateAndCategories.transformLatest { (state, categories) ->
            val currentCategory = categories.find { it.id == state.categoryId }

            val usedTimeItemsLive =
                if (currentCategory == null) logic.database.usedTimes().getUsedTimeListItemsByUserId(childId)
                else logic.database.usedTimes().getUsedTimeListItemsByCategoryId(currentCategory.id)

            emitAll(usedTimeItemsLive.map { usedTimeItems ->
                Screen(
                    categories = categories,
                    currentCategory = currentCategory,
                    items = usedTimeItems,
                    selectCategory = { category -> updateState { it.copy(categoryId = category?.id) } }
                )
            })
        })
    }
}