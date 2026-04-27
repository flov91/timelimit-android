/*
 * TimeLimit Copyright <C> 2019 - 2026 Jonas Lochmann
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

package io.timelimit.android.ui.manage.child.primarydevice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.timelimit.android.R
import io.timelimit.android.ui.model.Screen
import io.timelimit.android.ui.model.managechild.ManageChildCurrentDevice
import io.timelimit.android.ui.view.IntroCard
import io.timelimit.android.ui.view.SwitchRow

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CurrentDeviceScreen(
    screen: Screen.ManageChildCurrentDeviceScreen,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IntroCard(screen.intro, padding = false) {
            Text(stringResource(R.string.current_device_description))
        }

        CurrentDeviceContent(screen.content)
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CurrentDeviceContent(content: ManageChildCurrentDevice.Content, shortVersion: Boolean = false) {
    when (content) {
        is ManageChildCurrentDevice.Content.LocalMode -> {
            Text(
                stringResource(R.string.current_device_status_local_mode),
                Modifier.fillMaxWidth().padding(8.dp),
                textAlign = TextAlign.Center
            )
        }
        is ManageChildCurrentDevice.Content.InactiveUser -> {
            SwitchRow(
                label = stringResource(R.string.current_device_checkbox_relax),
                checked = content.relaxed,
                onCheckedChange = content.toggle
            )
        }
        is ManageChildCurrentDevice.Content.ActiveUser -> {
            val actions = content.actions
            val mode = content.mode
            val transition = content.transition

            Card(
                onClick = actions.makePrimary,
                backgroundColor =
                    if (mode == ManageChildCurrentDevice.Content.ActiveUser.Mode.PrimaryDevice) MaterialTheme.colors.primary
                    else MaterialTheme.colors.surface
            ) {
                Column (
                    Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.current_device_role_primary_title),
                        style = MaterialTheme.typography.h5
                    )

                    Text(stringResource(R.string.current_device_role_primary_description))

                    if (transition is ManageChildCurrentDevice.Content.ActiveUser.Transition.ConvertToPrimary) {
                        Text(
                            when (transition) {
                                is ManageChildCurrentDevice.Content.ActiveUser.Transition.ConvertToPrimary.SendingRequest -> stringResource(
                                    R.string.current_device_status_sending_request
                                )
                                is ManageChildCurrentDevice.Content.ActiveUser.Transition.ConvertToPrimary.WaitingForSync -> stringResource(
                                    R.string.current_device_status_waiting_for_sync
                                )
                                is ManageChildCurrentDevice.Content.ActiveUser.Transition.ConvertToPrimary.SendingSignOutRequest -> stringResource(
                                    R.string.current_device_status_sending_request
                                )
                                is ManageChildCurrentDevice.Content.ActiveUser.Transition.ConvertToPrimary.WaitingForSignOutAtOtherDevice -> stringResource(
                                    R.string.current_device_status_waiting_for_device,
                                    transition.deviceName
                                )
                            }
                        )

                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }

            Card(
                onClick = actions.makeSecondary,
                backgroundColor =
                    if (mode == ManageChildCurrentDevice.Content.ActiveUser.Mode.SecondaryDevice) MaterialTheme.colors.primary
                    else MaterialTheme.colors.surface
            ) {
                Column (Modifier.padding(8.dp)) {
                    Text(stringResource(
                        R.string.current_device_role_secondary_title),
                        style = MaterialTheme.typography.h5
                    )

                    Text(stringResource(R.string.current_device_role_secondary_description))

                    if (transition is ManageChildCurrentDevice.Content.ActiveUser.Transition.ConvertToSecondary) {
                        Text(
                            when (transition) {
                                is ManageChildCurrentDevice.Content.ActiveUser.Transition.ConvertToSecondary.SendingPing -> stringResource(
                                    R.string.current_device_status_sending_request
                                )
                                is ManageChildCurrentDevice.Content.ActiveUser.Transition.ConvertToSecondary.WaitingForReply -> stringResource(
                                    R.string.current_device_status_waiting_for_device,
                                    transition.deviceName
                                )
                            }
                        )

                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }

            if (!shortVersion) {
                Card(
                    onClick = actions.makeOther,
                    backgroundColor =
                        if (mode == ManageChildCurrentDevice.Content.ActiveUser.Mode.OtherDevice) MaterialTheme.colors.primary
                        else MaterialTheme.colors.surface
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text(
                            stringResource(
                                R.string.current_device_role_other_title
                            ),
                            style = MaterialTheme.typography.h5
                        )

                        Text(stringResource(R.string.current_device_role_other_description))

                        if (transition is ManageChildCurrentDevice.Content.ActiveUser.Transition.ConvertToOther) {
                            Text(
                                when (transition) {
                                    is ManageChildCurrentDevice.Content.ActiveUser.Transition.ConvertToOther.SendingRequest -> stringResource(
                                        R.string.current_device_status_sending_request
                                    )

                                    is ManageChildCurrentDevice.Content.ActiveUser.Transition.ConvertToOther.WaitingForSync -> stringResource(
                                        R.string.current_device_status_waiting_for_sync
                                    )
                                }
                            )

                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                }

                Card(
                    onClick = actions.makeRelaxed,
                    backgroundColor =
                        if (mode == ManageChildCurrentDevice.Content.ActiveUser.Mode.RelaxedPrimaryDevice) MaterialTheme.colors.primary
                        else MaterialTheme.colors.surface
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text(
                            stringResource(R.string.current_device_role_relaxed_title),
                            style = MaterialTheme.typography.h5
                        )

                        Text(stringResource(R.string.current_device_role_relaxed_description))
                    }
                }
            }
        }
    }
}