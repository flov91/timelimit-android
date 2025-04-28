/*
 * TimeLimit Copyright <C> 2019 - 2025 Jonas Lochmann
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.timelimit.android.R
import io.timelimit.android.data.model.UserType
import io.timelimit.android.ui.model.main.OverviewHandling

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.userItems(screen: OverviewHandling.OverviewScreen) {
    item (key = Pair("users", "header")) {
        ListCommon.SectionHeader(stringResource(R.string.overview_header_users), Modifier.animateItem())
    }

    items(screen.users.list, key = { Pair("user", it.id) }) { UserItem(it, screen.actions) }

    if (screen.users.canAdd) item (key = Pair("users", "create")) {
        ListCommon.ActionListItem(
            icon = Icons.Default.Add,
            label = stringResource(R.string.add_user_title),
            action = screen.actions.addUser,
            modifier = Modifier.animateItem()
        )
    }

    if (screen.users.canShowMore) item (key = Pair("users", "more")) {
        ListCommon.ShowMoreItem (
            modifier = Modifier.animateItem(),
            action = screen.actions.showMoreUsers
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyItemScope.UserItem(
    user: OverviewHandling.UserItem,
    actions: OverviewHandling.Actions
) {
    ListCardCommon.Card(
        Modifier
            .animateItem()
            .padding(horizontal = 8.dp)
            .clickable(onClick = { actions.openUser(user) })
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