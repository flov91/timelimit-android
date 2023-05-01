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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.timelimit.android.R
import io.timelimit.android.ui.model.setup.SetupParentHandling
import io.timelimit.android.ui.view.EnterTextField
import io.timelimit.android.ui.view.SetPassword

@Composable
fun ParentBaseConfiguration(
    content: SetupParentHandling.ParentBaseConfiguration,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (content.newUserDetails != null) {
            run {
                Text(stringResource(R.string.setup_parent_mode_explaination_user_name))

                TextField(
                    value = content.newUserDetails.parentName,
                    onValueChange = content.actions.newUserActions?.updateParentName ?: {},
                    enabled = content.actions.newUserActions?.updateParentName != null,
                    label = {
                        Text(stringResource(R.string.setup_parent_mode_field_name_hint))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
            }

            run {
                Text(stringResource(R.string.setup_parent_mode_explaination_password, content.mail))

                SetPassword(
                    password1 = content.newUserDetails.password,
                    password2 = content.newUserDetails.password2,
                    updatePassword1 = content.actions.newUserActions?.updatePassword ?: {},
                    updatePassword2 = content.actions.newUserActions?.updatePassword2 ?: {},
                    enabled = content.actions.newUserActions != null
                )
            }
        }

        run {
            Text(stringResource(R.string.setup_parent_mode_explaination_device_title))

            EnterTextField(
                value = content.deviceName,
                onValueChange = content.actions.updateDeviceName,
                label = {
                    Text(stringResource(R.string.setup_parent_mode_explaination_device_title_field_hint))
                },
                modifier = Modifier.fillMaxWidth(),
                onConfirmInput = content.actions.next ?: {}
            )
        }

        if (content.showLimitedProInfo) Text(stringResource(R.string.purchase_demo_temporarily_notice))

        Button(
            onClick = content.actions.next ?: {},
            enabled = content.actions.next != null,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(stringResource(R.string.wiazrd_next))
        }
    }
}