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
package io.timelimit.android.ui.diagnose.exception

import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.timelimit.android.R
import io.timelimit.android.util.Clipboard

@Composable
fun DiagnoseExceptionDialog(message: String, close: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = close,
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = close) {
                Text(stringResource(R.string.generic_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                Clipboard.setAndToast(context, message)
            }) {
                Text(stringResource(R.string.diagnose_sync_copy_to_clipboard))
            }
        }
    )
}