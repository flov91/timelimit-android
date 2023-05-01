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
package io.timelimit.android.ui.setup.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.ui.diagnose.exception.DiagnoseExceptionDialog
import io.timelimit.android.ui.model.setup.SetupParentHandling
import io.timelimit.android.ui.view.NotifyPermissionCard
import io.timelimit.android.ui.view.SwitchRow
import io.timelimit.android.update.UpdateUtil

@Composable
fun ParentSetupConsent(
    content: SetupParentHandling.ParentSetupConsent,
    errorDialog: Pair<String, () -> Unit>?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ConsentCard(
            stringResource(R.string.device_background_sync_title),
            stringResource(R.string.device_background_sync_text),
            stringResource(R.string.device_background_sync_checkbox),
            checked = content.backgroundSync,
            onCheckedChanged = content.actions?.updateBackgroundSync ?: {/* do nothing */},
            enabled = content.actions?.updateBackgroundSync != null
        )

        if (content.showEnableUpdates) ConsentCard(
            stringResource(R.string.update),
            stringResource(R.string.update_privacy, BuildConfig.updateServer),
            stringResource(R.string.update_enable_switch),
            checked = content.enableUpdates,
            onCheckedChanged = content.actions?.updateEnableUpdates ?: {/* do nothing */},
            enabled = content.actions?.updateEnableUpdates != null
        )

        NotifyPermissionCard.View(
            status = content.notificationAccess,
            listener = object: NotifyPermissionCard.Listener {
                override fun onGrantClicked() { content.actions?.requestNotifyPermission?.invoke() }
                override fun onSkipClicked() { content.actions?.skipNotifyPermission?.invoke() }
            },
            enabled = content.actions != null
        )

        Button(
            onClick = content.actions?.next ?: {},
            enabled = content.actions?.next != null,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(stringResource(R.string.wiazrd_next))
        }
    }

    if (errorDialog != null) DiagnoseExceptionDialog(errorDialog.first, errorDialog.second)
}

@Composable
fun ConsentCard(
    title: String,
    text: String,
    switch: String,
    checked: Boolean,
    onCheckedChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Card {
        Column(
            modifier.fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.h5
            )

            Text(text)

            SwitchRow(
                label = switch,
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChanged,
                reverse = true
            )
        }
    }
}