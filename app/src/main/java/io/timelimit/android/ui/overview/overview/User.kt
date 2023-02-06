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
package io.timelimit.android.ui.overview.overview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.timelimit.android.R
import io.timelimit.android.data.model.UserType
import io.timelimit.android.ui.MainActivity
import io.timelimit.android.ui.model.UpdateStateCommand
import io.timelimit.android.ui.model.main.OverviewHandling

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyItemScope.UserItem(
    user: OverviewHandling.UserItem,
    executeCommand: (UpdateStateCommand) -> Unit
) {
    // TODO: implement this without dependency on MainActivity
    val activity = LocalContext.current as MainActivity

    ListCardCommon.Card(
        Modifier
            .animateItemPlacement()
            .padding(horizontal = 8.dp)
            .clickable(
                onClick = {
                    when (user.type) {
                        UserType.Child -> {
                            if (!user.viewingNeedsAuthentication || activity.getActivityViewModel().isParentOrChildAuthenticated(user.id)) {
                                executeCommand(UpdateStateCommand.Overview.ManageChild(user.id))
                            } else {
                                activity.showAuthenticationScreen()
                            }
                        }
                        UserType.Parent -> executeCommand(UpdateStateCommand.Overview.ManageParent(user.id))
                    }
                }
            )
    ) {
        ListCardCommon.TextWithIcon(
            icon = Icons.Default.AccountCircle,
            label = stringResource(R.string.overview_user_item_name),
            value = user.name,
            style = MaterialTheme.typography.h6
        )

        ListCardCommon.TextWithIcon(
            icon = when (user.type) {
                UserType.Child -> Icons.Default.Security
                UserType.Parent -> Icons.Default.Settings
            },
            label = stringResource(R.string.overview_user_item_role),
            value = when (user.type) {
                UserType.Child -> stringResource(R.string.overview_user_item_role_child)
                UserType.Parent -> stringResource(R.string.overview_user_item_role_parent)
            }
        )

        if (user.areLimitsTemporarilyDisabled) {
            ListCardCommon.TextWithIcon(
                icon = Icons.Default.AlarmOff,
                label = stringResource(R.string.overview_user_item_temporarily_disabled),
                value = stringResource(R.string.overview_user_item_temporarily_disabled)
            )
        }
    }
}