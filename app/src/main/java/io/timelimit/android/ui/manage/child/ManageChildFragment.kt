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
package io.timelimit.android.ui.manage.child

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import io.timelimit.android.R
import io.timelimit.android.livedata.map
import io.timelimit.android.ui.fragment.ChildFragmentWrapper
import io.timelimit.android.ui.main.FragmentWithCustomTitle
import io.timelimit.android.ui.manage.child.category.ManageChildCategoriesFragment

class ManageChildFragment : ChildFragmentWrapper(), FragmentWithCustomTitle {
    private val params: ManageChildFragmentArgs by lazy { ManageChildFragmentArgs.fromBundle(arguments!!) }
    override val childId: String get() = params.childId

    override fun createChildFragment(): Fragment = ManageChildCategoriesFragment.newInstance(params)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null && params.fromRedirect) {
            Snackbar.make(binding.coordinator, R.string.manage_child_redirected_toast, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun getCustomTitle() = child.map { "${it?.name} < ${getString(R.string.main_tab_overview)}" as String? }
}
