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

package io.timelimit.android.ui.fragment

import androidx.fragment.app.Fragment
import io.timelimit.android.ui.model.UpdateStateCommand
import io.timelimit.android.ui.model.execute
import io.timelimit.android.ui.overview.about.AboutFragment
import io.timelimit.android.ui.overview.about.AboutFragmentParentHandlers

class AboutFragmentWrapped: SingleFragmentWrapper(), AboutFragmentParentHandlers {
    override val showAuthButton: Boolean = false
    override fun createChildFragment(): Fragment = AboutFragment()

    override fun onShowDiagnoseScreen() {
        requireActivity().execute(UpdateStateCommand.About.Diagnose)
    }

    override fun onShowPurchaseScreen() {
        requireActivity().execute(UpdateStateCommand.About.Purchase)
    }

    override fun onShowStayAwesomeScreen() {
        requireActivity().execute(UpdateStateCommand.About.StayAwesome)
    }
}