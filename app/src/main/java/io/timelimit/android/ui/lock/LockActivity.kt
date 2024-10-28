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
package io.timelimit.android.ui.lock

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.asFlow
import androidx.lifecycle.map
import io.timelimit.android.R
import io.timelimit.android.data.model.UserType
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.logic.BlockingReason
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.network.UpdatePrimaryDeviceRequestType
import io.timelimit.android.u2f.U2fManager
import io.timelimit.android.u2f.protocol.U2FDevice
import io.timelimit.android.ui.IsAppInForeground
import io.timelimit.android.ui.ScreenScaffold
import io.timelimit.android.ui.Theme
import io.timelimit.android.ui.login.AuthTokenLoginProcessor
import io.timelimit.android.ui.login.NewLoginFragment
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.manage.child.primarydevice.UpdatePrimaryDeviceDialogFragment
import io.timelimit.android.ui.util.SyncStatusModel

class LockActivity : AppCompatActivity(), ActivityViewModelHolder, U2fManager.DeviceFoundListener {
    companion object {
        private const val EXTRA_PACKAGE_NAME = "pkg"
        private const val EXTRA_ACTIVITY_NAME = "an"
        private const val LOGIN_DIALOG_TAG = "ldt"

        val currentInstances = mutableSetOf<LockActivity>()

        fun start(context: Context, packageName: String, activityName: String?) {
            context.startActivity(
                    Intent(context, LockActivity::class.java)
                            .putExtra(EXTRA_PACKAGE_NAME, packageName)
                            .apply {
                                if (activityName != null) {
                                    putExtra(EXTRA_ACTIVITY_NAME, activityName)
                                }
                            }
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        }
    }

    private val model: LockModel by viewModels()
    private val syncModel: SyncStatusModel by viewModels()
    private val activityModel: ActivityViewModel by viewModels()
    private var isResumed = false

    override var ignoreStop: Boolean = false
    override val showPasswordRecovery: Boolean = true

    private val blockedPackageName: String by lazy {
        intent.getStringExtra(EXTRA_PACKAGE_NAME)!!
    }

    private val blockedActivityName: String? by lazy {
        if (intent.hasExtra(EXTRA_ACTIVITY_NAME))
            intent.getStringExtra(EXTRA_ACTIVITY_NAME)
        else
            null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isNightMode =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                if (isNightMode) android.graphics.Color.TRANSPARENT
                else resources.getColor(R.color.colorPrimaryDark)
            )
        )

        supportActionBar!!.hide()

        U2fManager.setupActivity(this)

        val subtitleLive = syncModel.statusText.asFlow()
        val showTasksLive = model.content.map {
            val isTimeOver = it is LockscreenContent.Blocked.BlockedCategory && it.blockingHandling.activityBlockingReason == BlockingReason.TimeOver

            isTimeOver
        }.asFlow()

        setContent {
            val subtitle by subtitleLive.collectAsState(null)
            val showTasks by showTasksLive.collectAsState(false)
            val pager = rememberPagerState(initialPage = 0, pageCount = {
                if (showTasks) 3
                else 2
            })
            val isAuthenticated by getActivityViewModel().authenticatedUser
                .map { it?.second?.type == UserType.Parent }
                .asFlow().collectAsState(initial = false)

            Theme {
                ScreenScaffold(
                    screen = null,
                    title = getString(R.string.app_name),
                    subtitle = subtitle,
                    backStack = emptyList(),
                    snackbarHostState = null,
                    content = { padding ->
                        Column (Modifier.fillMaxSize().padding(padding)) {
                            TabRow(
                                pager.currentPage,
                                indicator = { tabPositions ->
                                    // workaround for bug
                                    TabRowDefaults.Indicator(
                                        Modifier.tabIndicatorOffset(tabPositions[
                                                pager.currentPage.coerceAtMost(tabPositions.size - 1)
                                        ])
                                    )
                                }
                            ) {
                                Tab(
                                    selected = pager.currentPage == 0,
                                    onClick = { pager.requestScrollToPage(0) }
                                ) {
                                    Text(
                                        stringResource(R.string.lock_tab_reason),
                                        Modifier.padding(16.dp)
                                    )
                                }

                                Tab(
                                    selected = pager.currentPage == 1,
                                    onClick = { pager.requestScrollToPage(1) }
                                ) {
                                    Text(
                                        stringResource(R.string.lock_tab_action),
                                        Modifier.padding(16.dp)
                                    )
                                }

                                if (showTasks) Tab(
                                    selected = pager.currentPage == 2,
                                    onClick = { pager.requestScrollToPage(2) }
                                ) {
                                    Text(
                                        stringResource(R.string.lock_tab_task),
                                        Modifier.padding(16.dp)
                                    )
                                }
                            }

                            HorizontalPager(
                                pager,
                                Modifier.weight(1.0F, fill = true),
                                pageContent = { index ->
                                    when (index) {
                                        0 -> AndroidFragment<LockReasonFragment>(Modifier.fillMaxSize())
                                        1 -> AndroidFragment<LockActionFragment>(Modifier.fillMaxSize())
                                        2 -> AndroidFragment<LockTaskFragment>(Modifier.fillMaxSize())
                                    }
                                }
                            )
                        }
                    },
                    executeCommand = {},
                    showAuthenticationDialog =
                    if (pager.currentPage == 1 && !isAuthenticated) ({ showAuthenticationScreen() })
                    else null
                )
            }
        }

        syncModel.statusText.observe(this) { supportActionBar?.subtitle = it }

        currentInstances.add(this)

        model.init(blockedPackageName, blockedActivityName)

        model.content.observe(this) {
            if (isResumed && it is LockscreenContent.Blocked.BlockedCategory && it.reason == BlockingReason.RequiresCurrentDevice && !model.didOpenSetCurrentDeviceScreen) {
                model.didOpenSetCurrentDeviceScreen = true

                UpdatePrimaryDeviceDialogFragment
                        .newInstance(UpdatePrimaryDeviceRequestType.SetThisDevice)
                        .show(supportFragmentManager)
            }
        }

        activityModel.shouldHighlightAuthenticationButton.observe(this) {
            if (it) {
                activityModel.shouldHighlightAuthenticationButton.postValue(false)

                showAuthenticationScreen()
            }
        }

        onBackPressedDispatcher.addCallback(object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {/* nothing to do */}
        })
    }

    override fun onDestroy() {
        super.onDestroy()

        currentInstances.remove(this)
    }

    override fun getActivityViewModel(): ActivityViewModel = activityModel

    override fun showAuthenticationScreen() {
        NewLoginFragment().showSafe(supportFragmentManager, LOGIN_DIALOG_TAG)
    }

    override fun onResume() {
        super.onResume()

        lockTaskModeWorkaround()
        U2fManager.with(this).registerListener(this)
        isResumed = true
    }

    override fun onPause() {
        super.onPause()

        lockTaskModeWorkaround()
        U2fManager.with(this).unregisterListener(this)
        isResumed = false
    }

    override fun onStart() {
        super.onStart()

        IsAppInForeground.reportStart()
    }

    override fun onStop() {
        super.onStop()

        if ((!isChangingConfigurations) && (!ignoreStop)) {
            getActivityViewModel().logOut()
        }

        IsAppInForeground.reportStop()
    }

    private fun lockTaskModeWorkaround() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val platformIntegration = DefaultAppLogic.with(this).platformIntegration
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

            val isLocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                activityManager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_PINNED
            else
                activityManager.isInLockTaskMode

            if (isLocked) {
                platformIntegration.setSuspendedApps(listOf(blockedPackageName), true)
                platformIntegration.setSuspendedApps(listOf(blockedPackageName), false)
            }
        }
    }

    override fun onDeviceFound(device: U2FDevice) = AuthTokenLoginProcessor.process(device, getActivityViewModel())
}
