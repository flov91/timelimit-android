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
package io.timelimit.android.ui.manage.category

import androidx.fragment.app.Fragment
import io.timelimit.android.ui.fragment.CategoryFragmentWrapper
import io.timelimit.android.ui.main.FragmentWithCustomTitle
import io.timelimit.android.ui.manage.category.appsandrules.CombinedAppsAndRulesFragment

class ManageCategoryFragment : CategoryFragmentWrapper(), FragmentWithCustomTitle {
    private val params: ManageCategoryFragmentArgs by lazy { ManageCategoryFragmentArgs.fromBundle(requireArguments()) }
    override val childId: String get() = params.childId
    override val categoryId: String get() = params.categoryId

    override fun createChildFragment(): Fragment = CombinedAppsAndRulesFragment.newInstance(
            childId = childId,
            categoryId = categoryId
    )
}
