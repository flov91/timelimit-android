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
package io.timelimit.android.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.core.content.getSystemService
import io.timelimit.android.R

object Clipboard {
    fun setAndToast(context: Context, content: String) {
        context.getSystemService<ClipboardManager>()?.also { clipboard ->
            clipboard.setPrimaryClip(ClipData.newPlainText("TimeLimit", content))

            Toast.makeText(context, R.string.diagnose_sync_copied_to_clipboard, Toast.LENGTH_SHORT).show()
        }
    }
}