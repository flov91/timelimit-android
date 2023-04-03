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
package io.timelimit.android.ui.setup

import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.timelimit.android.R

@Composable
fun SetupParentKeyModeDialogFragment(
    confirm: () -> Unit,
    cancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = cancel,
        title = { Text(stringResource(R.string.setup_select_mode_parent_key_title)) },
        text = { Text(stringResource(R.string.setup_select_mode_parent_key_text)) },
        confirmButton = {
            TextButton(onClick = confirm) {
                Text(stringResource(R.string.wiazrd_next))
            }
        },
        dismissButton = {
            TextButton(onClick = cancel) {
                Text(stringResource(R.string.generic_cancel))
            }
        }
    )
}