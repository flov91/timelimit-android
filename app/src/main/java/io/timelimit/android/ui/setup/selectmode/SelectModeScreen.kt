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
package io.timelimit.android.ui.setup.selectmode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.timelimit.android.BuildConfig
import io.timelimit.android.R

@Composable
fun SelectModeScreen(
    selectLocal: () -> Unit,
    selectConnected: () -> Unit,
    selectUninstall: () -> Unit,
    modifier: Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        OptionCard(
            title = stringResource(R.string.setup_select_mode_local_title),
            description = stringResource(R.string.setup_select_mode_local_text),
            icon = Icons.Outlined.Lock,
            handler = selectLocal
        )

        if (BuildConfig.hasServer) OptionCard(
            title = stringResource(R.string.setup_select_mode_connected_title),
            description = stringResource(R.string.setup_select_mode_connected_text),
            icon = Icons.Default.Wifi,
            handler = selectConnected
        )

        OptionCard(
            title = stringResource(R.string.setup_select_mode_uninstall_title),
            description = stringResource(R.string.setup_select_mode_uninstall_text),
            icon = Icons.Outlined.Delete,
            handler = selectUninstall
        )

        Text(
            stringResource(R.string.setup_select_mode_outro),
            Modifier.padding(8.dp)
        )
    }
}