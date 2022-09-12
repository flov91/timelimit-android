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
package io.timelimit.android.ui.diagnose

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import io.timelimit.android.R
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.util.Clipboard
import java.io.PrintWriter
import java.io.StringWriter

class DiagnoseExceptionDialogFragment: DialogFragment() {
    companion object {
        private const val DIALOG_TAG = "DiagnoseExceptionDialogFragment"
        private const val EXCEPTION = "ex"

        fun newInstance(exception: Exception) = DiagnoseExceptionDialogFragment().apply {
            arguments = Bundle().apply {
                putSerializable(EXCEPTION, exception)
            }
        }

        fun getStackTraceString(tr: Throwable): String = StringWriter().let { sw ->
            PrintWriter(sw).let { pw ->
                tr.printStackTrace(pw)
                pw.flush()
            }

            sw.toString()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val message = getStackTraceString(requireArguments().getSerializable(EXCEPTION) as Exception)

        return AlertDialog.Builder(requireContext(), theme)
                .setMessage(message)
                .setNeutralButton(R.string.diagnose_sync_copy_to_clipboard) { _, _ -> Clipboard.setAndToast(requireContext(), message) }
                .setPositiveButton(R.string.generic_ok, null)
                .create()
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}