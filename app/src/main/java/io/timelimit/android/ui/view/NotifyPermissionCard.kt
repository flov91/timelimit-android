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
package io.timelimit.android.ui.view

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import io.timelimit.android.R
import io.timelimit.android.databinding.NotifyPermissionCardBinding

object NotifyPermissionCard {
    enum class Status {
        Unknown,
        WaitingForInteraction,
        SkipGrant,
        Granted
    }

    @Composable
    fun View(
        status: Status,
        listener: Listener,
        modifier: Modifier = Modifier,
        enabled: Boolean = true
    ) {
        Card { Column(
            modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.notify_permission_title),
                style = MaterialTheme.typography.h5
            )

            Text(
                stringResource(R.string.notify_permission_text)
            )

            if (status == Status.Granted) Text(
                stringResource(R.string.notify_permission_text_granted),
                color = Color(ContextCompat.getColor(LocalContext.current, R.color.text_green)).copy(alpha = 1f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            ) else if (status == Status.WaitingForInteraction) TextButton(
                onClick = { listener.onSkipClicked() },
                modifier = Modifier.align(Alignment.End),
                enabled = enabled
            ) {
                Text(stringResource(R.string.notify_permission_btn_later))
            }

            if (status != Status.Granted) Button(
                onClick = { listener.onGrantClicked() },
                modifier = Modifier.align(Alignment.End),
                enabled = enabled
            ) {
                Text(stringResource(R.string.notify_permission_btn_grant))
            }
        } }
    }

    fun bind(status: Status, view: NotifyPermissionCardBinding) {
        view.showGrantButton = status != Status.Granted
        view.showGrantedMessage = status == Status.Granted
        view.showSkipButton = status == Status.WaitingForInteraction
    }

    fun bind(listener: Listener, view: NotifyPermissionCardBinding) {
        view.grantButton.setOnClickListener { listener.onGrantClicked() }
        view.skipButton.setOnClickListener { listener.onSkipClicked() }
    }

    fun updateStatus(status: Status, context: Context) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (context.getSystemService<NotificationManager>()!!.areNotificationsEnabled()) Status.Granted
        else if (status == Status.SkipGrant) Status.SkipGrant
        else Status.WaitingForInteraction
    } else Status.Granted

    fun canProceed(status: Status) = when (status) {
        Status.Unknown -> false
        Status.WaitingForInteraction -> false
        Status.SkipGrant -> true
        Status.Granted -> true
    }

    interface Listener {
        fun onGrantClicked()
        fun onSkipClicked()
    }
}