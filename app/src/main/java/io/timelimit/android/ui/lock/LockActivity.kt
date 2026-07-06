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
package io.timelimit.android.ui.lock

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
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
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.map
import androidx.lifecycle.repeatOnLifecycle
import io.timelimit.android.R
import io.timelimit.android.data.model.UserType
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.integration.platform.SystemPermissionConfirmationLevel
import io.timelimit.android.logic.BlockingReason
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.u2f.U2fManager
import io.timelimit.android.u2f.protocol.U2FDevice
import io.timelimit.android.ui.IsAppInForeground
import io.timelimit.android.ui.MainActivity
import io.timelimit.android.ui.ScreenScaffold
import io.timelimit.android.ui.Theme
import io.timelimit.android.ui.consent.SyncAppListConsentDialogFragment
import io.timelimit.android.ui.login.AuthTokenLoginProcessor
import io.timelimit.android.ui.login.NewLoginFragment
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.manage.child.category.create.CreateCategoryDialogFragment
import io.timelimit.android.ui.manage.child.category.specialmode.SetCategorySpecialModeFragment
import io.timelimit.android.ui.manage.child.primarydevice.CurrentDeviceContent
import io.timelimit.android.ui.manage.child.primarydevice.CurrentDeviceContentMode
import io.timelimit.android.ui.manage.device.add.AddDeviceFragment
import io.timelimit.android.ui.model.ActivityCommand
import io.timelimit.android.ui.overview.overview.CanNotAddDevicesInLocalModeDialogFragment
import io.timelimit.android.ui.payment.RequiresPurchaseDialogFragment
import io.timelimit.android.ui.util.SyncStatusModel
import kotlinx.coroutines.launch

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

    private val requestNotifyPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.api.reportPermissionsChanged()
    }

    @OptIn(ExperimentalMaterialApi::class)
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

        U2fManager.setupActivity(this)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                for (message in model.api.activityCommand) when (message) {
                    ActivityCommand.ShowAddDeviceFragment -> AddDeviceFragment().show(supportFragmentManager)
                    ActivityCommand.ShowCanNotAddDevicesInLocalModeDialogFragment -> CanNotAddDevicesInLocalModeDialogFragment().show(supportFragmentManager)
                    ActivityCommand.ShowAuthenticationScreen -> showAuthenticationScreen()
                    ActivityCommand.ShowMissingPremiumDialog -> RequiresPurchaseDialogFragment().show(supportFragmentManager)
                    is ActivityCommand.LaunchSystemSettings -> model.logic.platformIntegration.openSystemPermissionScren(
                        this@LockActivity, message.permission, SystemPermissionConfirmationLevel.Suggestion
                    )
                    is ActivityCommand.TriggerUninstall -> try {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${message.packageName}")
                        ).addCategory(Intent.CATEGORY_DEFAULT)
                        else Intent(
                            Intent.ACTION_UNINSTALL_PACKAGE,
                            Uri.parse("package:${message.packageName}")
                        )

                        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (ex: Exception) {
                        message.errorHandler()
                    }
                    ActivityCommand.RequestNotifyPermission -> requestNotifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    ActivityCommand.ShowSyncConsentDialog -> SyncAppListConsentDialogFragment.newInstance().show(supportFragmentManager)
                    is ActivityCommand.ShowCreateCategoryDialog -> CreateCategoryDialogFragment.newInstance(childId = message.childId)
                        .show(supportFragmentManager)
                    is ActivityCommand.ShowCategorySpecialModeDialog -> SetCategorySpecialModeFragment.newInstance(
                        childId = message.childId,
                        categoryId = message.categoryId,
                        mode = message.mode
                    ).show(supportFragmentManager)
                }
            }
        }

        supportFragmentManager.registerFragmentLifecycleCallbacks(object: FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentStopped(fm: FragmentManager, f: Fragment) {
                super.onFragmentStopped(fm, f)

                if (f is NewLoginFragment) model.api.reportAuthenticationScreenClosed()
            }
        }, false)

        val subtitleLive = syncModel.statusText.asFlow()
        val showTasksLive = model.content.map {
            val isTimeOver = it is LockscreenContent.Blocked.BlockedCategory && it.blockingHandling.activityBlockingReason == BlockingReason.TimeOver

            isTimeOver
        }.asFlow()

        model.content.observe(this) {
            if (it is LockscreenContent.Blocked && it.reason == BlockingReason.RequiresCurrentDevice) {
                model.applyRememberedCurrentDeviceSelection()
            }

            if (it is LockscreenContent.Close) {
                finish()
            }
        }

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
                    snackbarHostState = model.snackbarHostState,
                    extraBars = {
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
                    },
                    content = { padding ->
                        HorizontalPager(
                            pager,
                            Modifier
                                .fillMaxSize()
                                .padding(padding),
                            pageContent = { index ->
                                when (index) {
                                    0 -> AndroidFragment<LockReasonFragment>(Modifier.fillMaxSize())
                                    1 -> {
                                        val valueLive by model.content.asFlow().collectAsState(null)

                                        val value = valueLive

                                        if (value is LockscreenContent.Blocked && value.reason == BlockingReason.RequiresCurrentDevice) {
                                            val currentDevice by model.manageCurrentDeviceContent.collectAsState(null)

                                            Column(
                                                Modifier
                                                    .fillMaxSize()
                                                    .verticalScroll(rememberScrollState())
                                                    .padding(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                currentDevice?.let { CurrentDeviceContent(it, CurrentDeviceContentMode.Lockscreen) }

                                                Card(
                                                    onClick = {
                                                        val user = getActivityViewModel().getAuthenticatedUser()

                                                        startActivity(
                                                            if (user == null)
                                                                Intent(this@LockActivity, MainActivity::class.java)
                                                            else
                                                                MainActivity.getAuthHandoverIntent(
                                                                    this@LockActivity,
                                                                    user
                                                                )
                                                        )
                                                    },
                                                ) {
                                                    Column(Modifier.padding(8.dp)) {
                                                        Text(
                                                            stringResource(
                                                                R.string.lock_goto_main_title
                                                            ),
                                                            style = MaterialTheme.typography.h5
                                                        )

                                                        Text(stringResource(R.string.lock_goto_main_text))
                                                    }
                                                }
                                            }
                                        } else {
                                            AndroidFragment<LockActionFragment>(Modifier.fillMaxSize())
                                        }
                                    }
                                    2 -> AndroidFragment<LockTaskFragment>(Modifier.fillMaxSize())
                                }
                            }
                        )
                    },
                    executeCommand = {},
                    showAuthenticationDialog =
                    if (pager.currentPage == 1 && !isAuthenticated) ({ showAuthenticationScreen() })
                    else null
                )
            }
        }

        currentInstances.add(this)

        model.init(blockedPackageName, blockedActivityName)

        model.api.activityModel.shouldHighlightAuthenticationButton.observe(this) {
            if (it) {
                model.api.activityModel.shouldHighlightAuthenticationButton.postValue(false)

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

    override fun getActivityViewModel(): ActivityViewModel = model.api.activityModel

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
