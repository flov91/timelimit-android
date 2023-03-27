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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.timelimit.android.R
import io.timelimit.android.integration.platform.RuntimePermissionStatus
import io.timelimit.android.ui.manage.device.manage.permission.PermissionScreen
import io.timelimit.android.ui.manage.device.manage.permission.PermissionScreenContent

@Composable
fun SetupDevicePermissionsScreen(
    content: PermissionScreenContent,
    next: () -> Unit,
    modifier: Modifier
) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.setup_device_permissions_text))

        PermissionScreen(content)

        Button(
            onClick = next,
            modifier = Modifier.align(Alignment.End),
            enabled = content.status.usageStats != RuntimePermissionStatus.NotGranted
        ) {
            Text(stringResource(R.string.wiazrd_next))
        }
    }
}