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
import androidx.compose.ui.res.stringResource
import io.timelimit.android.R

@Composable
fun SimpleErrorDialog(title: String?, message: String, close: () -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        title = if (title != null) ({ Text(title) }) else null,
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = close) {
                Text(stringResource(R.string.generic_ok))
            }
        }
    )
}