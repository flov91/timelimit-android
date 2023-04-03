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
package io.timelimit.android.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.timelimit.android.R
import io.timelimit.android.ui.model.BackStackItem
import io.timelimit.android.ui.model.Screen
import io.timelimit.android.ui.model.Title
import io.timelimit.android.ui.model.UpdateStateCommand

@Composable
fun ScreenScaffold(
    screen: Screen?,
    title: String,
    subtitle: String?,
    backStack: List<BackStackItem>,
    snackbarHostState: SnackbarHostState?,
    content: @Composable (PaddingValues) -> Unit,
    executeCommand: (UpdateStateCommand) -> Unit,
    showAuthenticationDialog: (() -> Unit)?
) {
    var expandDropdown by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.subtitle1,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = if (screen?.state?.previous != null) ({
                    IconButton(onClick = { executeCommand(UpdateStateCommand.BackToPreviousScreen) }) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.generic_back))
                    }
                }) else null,
                actions = {
                    for (icon in screen?.toolbarIcons ?: emptyList()) {
                        IconButton(
                            onClick = {
                                if (icon.action != null) executeCommand(icon.action)

                                icon.handler()
                            }
                        ) {
                            Icon(icon.icon, stringResource(icon.labelResource))
                        }
                    }

                    if (screen?.toolbarOptions?.isEmpty() == false) {
                        IconButton(onClick = { expandDropdown = true }) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.generic_menu))
                        }

                        DropdownMenu(
                            expanded = expandDropdown,
                            onDismissRequest = { expandDropdown = false }
                        ) {
                            for (option in screen.toolbarOptions) {
                                DropdownMenuItem(onClick = {
                                    if (option.action != null) executeCommand(option.action)

                                    option.handler()

                                    expandDropdown = false
                                }) {
                                    Text(stringResource(option.labelResource))
                                }
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            val backStackColors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colors.onSecondary.copy(alpha = .8f),
                disabledContentColor = MaterialTheme.colors.onSecondary
            )

            val bottomBarColor =
                if (MaterialTheme.colors.isLight) MaterialTheme.colors.secondary
                else MaterialTheme.colors.primarySurface

            if (backStack.isNotEmpty()) BottomAppBar(
                backgroundColor = bottomBarColor,
                content = {
                    Row(
                        modifier = Modifier.horizontalScroll(
                            rememberScrollState(),
                            reverseScrolling = true
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (item in backStack) {
                            TextButton(
                                onClick = item.action,
                                colors = backStackColors
                            ) {
                                Text(when (item.title) {
                                    is Title.Plain -> item.title.text
                                    is Title.StringResource -> stringResource(item.title.id)
                                })
                            }

                            Icon(
                                Icons.Default.ArrowForwardIos,
                                ">"
                            )
                        }

                        TextButton(
                            enabled = false,
                            onClick = {},
                            colors = backStackColors
                        ) {
                            Text(title)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (showAuthenticationDialog != null) {
                FloatingActionButton(onClick = showAuthenticationDialog) {
                    Icon(Icons.Default.LockOpen, stringResource(R.string.authentication_action))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState ?: it) },
        content = content
    )
}