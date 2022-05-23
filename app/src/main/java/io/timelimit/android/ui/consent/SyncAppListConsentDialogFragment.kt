/*
 * TimeLimit Copyright <C> 2019 - 2022 Jonas Lochmann
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
package io.timelimit.android.ui.consent

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.data.model.ConsentFlags
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.logic.DefaultAppLogic

class SyncAppListConsentDialogFragment: DialogFragment() {
    companion object {
        private const val DIALOG_TAG = "SyncAppListConsentDialogFragment"
        private const val LOG_TAG = "SyncAppListConsent"

        fun newInstance() = SyncAppListConsentDialogFragment()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = AlertDialog.Builder(requireContext(), theme)
        .setTitle(R.string.consent_app_list_sync_dialog_title)
        .setMessage(R.string.consent_app_list_sync_dialog_text)
        .setNegativeButton(R.string.generic_reject, null)
        .setPositiveButton(R.string.generic_accept) { _, _ ->
            val database = DefaultAppLogic.with(requireContext()).database

            Threads.database.execute {
                try {
                    database.config().setConsentFlagSync(ConsentFlags.APP_LIST_SYNC, true)
                } catch (ex: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.w(LOG_TAG, "Could not save consent", ex)
                    }
                }
            }
        }
        .create()

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}