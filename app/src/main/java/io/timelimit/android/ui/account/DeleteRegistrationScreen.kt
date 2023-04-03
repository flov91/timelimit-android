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
package io.timelimit.android.ui.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.timelimit.android.R
import io.timelimit.android.ui.authentication.AuthenticateByMailScreen
import io.timelimit.android.ui.diagnose.exception.DiagnoseExceptionDialog
import io.timelimit.android.ui.model.account.AccountDeletion
import io.timelimit.android.ui.view.SwitchRow

@Composable
fun DeleteRegistrationScreen(
    content: AccountDeletion.MyScreen,
    modifier: Modifier
) {
    when (content) {
        is AccountDeletion.MyScreen.NoAccount -> DeleteCenteredText(
            stringResource(R.string.account_deletion_text_no_account),
            modifier
        )
        is AccountDeletion.MyScreen.MailConfirmation -> AuthenticateByMailScreen(content.content, modifier)
        is AccountDeletion.MyScreen.FinalConfirmation -> DeleteFinalConfirmationScreen(content, modifier)
        is AccountDeletion.MyScreen.Done -> DeleteCenteredText(
            stringResource(R.string.account_deletion_text_done),
            modifier
        )
    }
}

@Composable
fun DeleteCenteredText(
    text: String,
    modifier: Modifier
) {
    Box(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        )
    }
}

@Composable
fun DeleteFinalConfirmationScreen(
    content: AccountDeletion.MyScreen.FinalConfirmation,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.account_deletion_confirmation_text))

        SwitchRow(
            label = stringResource(R.string.account_deletion_confirmation_premium_toggle),
            checked = content.didConfirmPremiumLoss,
            onCheckedChange = content.actions?.updateConfirmPremiumLoss ?: {},
            enabled = content.actions?.updateConfirmPremiumLoss != null
        )

        Button(
            onClick = content.actions?.finalConfirmation ?: {},
            enabled = content.actions?.finalConfirmation != null,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error)
        ) {
            Text(stringResource(R.string.account_deletion_confirmation_button))
        }
    }

    if (content.errorDialog != null) DiagnoseExceptionDialog(content.errorDialog.message, content.errorDialog.close)
}