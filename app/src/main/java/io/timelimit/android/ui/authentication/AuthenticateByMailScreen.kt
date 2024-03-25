/*
 * TimeLimit Copyright <C> 2019 - 2024 Jonas Lochmann
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
package io.timelimit.android.ui.authentication

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.timelimit.android.R
import io.timelimit.android.ui.diagnose.exception.DiagnoseExceptionDialog
import io.timelimit.android.ui.diagnose.exception.SimpleErrorDialog
import io.timelimit.android.ui.model.mailauthentication.MailAuthentication
import io.timelimit.android.ui.view.EnterTextField
import io.timelimit.android.ui.view.RadioButtonRow

@Composable
fun AuthenticateByMailScreen(
    content: MailAuthentication.Screen,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (content) {
            is MailAuthentication.Screen.EnterMailAddress -> {
                Text(stringResource(R.string.authenticate_by_mail_other_text))

                EnterTextField(
                    value = content.mail,
                    onValueChange = content.actions?.updateMailAddress ?: {},
                    enabled = content.actions?.updateMailAddress != null,
                    onConfirmInput = content.actions?.confirm ?: {},
                    label = { Text(stringResource(R.string.authenticate_by_mail_hint_mail)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email
                    )
                )

                Button(
                    onClick = content.actions?.confirm ?: {},
                    enabled = content.actions?.confirm != null,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.wiazrd_next))
                }
            }
            is MailAuthentication.Screen.ConfirmMailSending -> {
                Text(stringResource(R.string.authenticate_by_mail_hardcoded_text, content.mail))

                Button(
                    onClick = content.confirm ?: {},
                    enabled = content.confirm != null,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.generic_go))
                }
            }
            is MailAuthentication.Screen.PickMailAddress -> {
                if (content.actions?.back != null) BackHandler (onBack = content.actions.back)

                for (index in content.options.indices) {
                    val option = content.options[index]

                    RadioButtonRow(
                        label = option,
                        selected = index == content.selectedIndex,
                        onClick = { content.actions?.updateSelectedIndex?.invoke(index) },
                        enabled = content.actions?.updateSelectedIndex != null
                    )
                }

                Button(
                    onClick = content.actions?.confirm ?: {},
                    enabled = content.actions?.confirm != null,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.wiazrd_next))
                }
            }
            is MailAuthentication.Screen.EnterReceivedCode -> {
                if (content.actions?.back != null) BackHandler (onBack = content.actions.back)

                Text(stringResource(R.string.authenticate_by_mail_message_sent, content.mail))

                EnterTextField(
                    label = { Text(stringResource(R.string.authenticate_by_mail_hint_code)) },
                    value = content.codeInput,
                    onValueChange = content.actions?.updateCodeInput ?: {},
                    onConfirmInput = content.actions?.confirmCodeInput ?: {},
                    enabled = content.actions != null,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = content.actions?.confirmCodeInput ?: {},
                    enabled = content.actions != null,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.generic_ok))
                }
            }
        }
    }

    content.error?.let {
        AuthenticateByMailError(it.dialog, it.close)
    }
}

@Composable
fun AuthenticateByMailError(error: MailAuthentication.ErrorDialog, close: () -> Unit) {
    when (error) {
        MailAuthentication.ErrorDialog.RateLimit -> SimpleErrorDialog(
            null,
            stringResource(R.string.authenticate_too_many_requests_text),
            close
        )
        MailAuthentication.ErrorDialog.BlockedMailServer -> SimpleErrorDialog(
            stringResource(R.string.authenticate_blacklisted_mail_server_title),
            stringResource(R.string.authenticate_blacklisted_mail_server_text),
            close
        )
        MailAuthentication.ErrorDialog.MailAddressNotAllowed -> SimpleErrorDialog(
            stringResource(R.string.authenticate_not_whitelisted_address_title),
            stringResource(R.string.authenticate_not_whitelisted_address_text),
            close
        )
        MailAuthentication.ErrorDialog.BlockedForIntegrityReasons -> SimpleErrorDialog(
            stringResource(R.string.authenticate_error_integrity_title),
            stringResource(R.string.authenticate_error_integrity_text),
            close
        )
        MailAuthentication.ErrorDialog.TemporarilyBlockedMailServer -> SimpleErrorDialog(
            stringResource(R.string.authenticate_temporarily_blacklisted_mail_server_title),
            stringResource(R.string.authenticate_temporarily_blacklisted_mail_server_text),
            close
        )
        is MailAuthentication.ErrorDialog.ExceptionDetails -> DiagnoseExceptionDialog(error.message, close)
    }
}