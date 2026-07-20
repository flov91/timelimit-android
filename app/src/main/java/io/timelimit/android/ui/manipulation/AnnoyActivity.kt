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
package io.timelimit.android.ui.manipulation

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.map
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.data.model.UserType
import io.timelimit.android.databinding.AnnoyActivityBinding
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.integration.platform.android.AndroidIntegrationApps
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.u2f.U2fManager
import io.timelimit.android.u2f.protocol.U2FDevice
import io.timelimit.android.ui.ScreenScaffold
import io.timelimit.android.ui.Theme
import io.timelimit.android.ui.backdoor.BackdoorDialogFragment
import io.timelimit.android.ui.login.AuthTokenLoginProcessor
import io.timelimit.android.ui.login.NewLoginFragment
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.manage.device.manage.ManipulationWarnings
import io.timelimit.android.util.TimeTextUtil

class AnnoyActivity : AppCompatActivity(), ActivityViewModelHolder, U2fManager.DeviceFoundListener {
    companion object {
        private const val LOG_TAG = "AnnoyActivity"

        fun start(context: Context) {
            context.startActivity(
                Intent(context, AnnoyActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        }
    }

    private val model: ActivityViewModel by viewModels()
    override var ignoreStop: Boolean = false
    override val showPasswordRecovery: Boolean = false
    override fun getActivityViewModel() = model
    override fun showAuthenticationScreen() { NewLoginFragment.newInstance(showOnLockscreen = true).showSafe(supportFragmentManager, "nlf") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val logic = DefaultAppLogic.with(this)

        val isNightMode =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                if (isNightMode) android.graphics.Color.TRANSPARENT
                else resources.getColor(R.color.colorPrimaryDark)
            )
        )

        setContent {
            Theme {
                ScreenScaffold(
                    screen = null,
                    title = getString(R.string.app_name),
                    subtitle = null,
                    backStack = emptyList(),
                    snackbarHostState = null,
                    content = { padding ->
                        AndroidView(
                            factory = {
                                val binding = AnnoyActivityBinding.inflate(LayoutInflater.from(it))

                                logic.annoyLogic.nextManualUnblockCountdown.observe(this) { countdown ->
                                    binding.canRequestUnlock = countdown == 0L
                                    binding.countdownText = getString(R.string.annoy_timer, TimeTextUtil.seconds((countdown / 1000).toInt(), this@AnnoyActivity))
                                }

                                logic.deviceEntry.map {
                                    val reasonItems = (it?.let { ManipulationWarnings.getFromDevice(it) } ?: ManipulationWarnings.empty)
                                        .current
                                        .map { getString(it.labelResourceId) }

                                    if (reasonItems.isEmpty()) {
                                        null
                                    } else {
                                        getString(R.string.annoy_reason, reasonItems.joinToString(separator = ", "))
                                    }
                                }.observe(this) { binding.reasonText = it }

                                binding.unlockTemporarilyButton.setOnClickListener {
                                    AnnoyUnlockDialogFragment.newInstance(AnnoyUnlockDialogFragment.UnlockDuration.Short)
                                        .show(supportFragmentManager)
                                }

                                binding.parentUnlockButton.setOnClickListener {
                                    AnnoyUnlockDialogFragment.newInstance(AnnoyUnlockDialogFragment.UnlockDuration.Long)
                                        .show(supportFragmentManager)
                                }

                                binding.useBackdoorButton.setOnClickListener { BackdoorDialogFragment().show(supportFragmentManager) }

                                binding.root
                            },
                            modifier = Modifier.fillMaxSize().padding(padding)
                        )
                    },
                    executeCommand = {},
                    showAuthenticationDialog = null
                )
            }
        }

        U2fManager.setupActivity(this)

        val systemImageApps = packageManager.getInstalledApplications(0)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == ApplicationInfo.FLAG_SYSTEM }
            .map { it.packageName }.toSet()

        val lockTaskPackages = AndroidIntegrationApps.appsToIncludeInLockTasks + setOf(packageName) + systemImageApps

        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "setLockTaskPackages: $lockTaskPackages")
        }

        if (logic.platformIntegration.setLockTaskPackages(lockTaskPackages.toList())) {
            startLockTask()
        }

        logic.annoyLogic.shouldAnnoyRightNow.observe(this) { shouldRun ->
            if (!shouldRun) shutdown()
        }

        model.authenticatedUser.observe(this) { user ->
            if (user?.second?.type == UserType.Parent) {
                logic.annoyLogic.doParentTempUnlock()
            }
        }

        onBackPressedDispatcher.addCallback(object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {/* nothing to do */}
        })
    }

    private fun shutdown() {
        stopLockTask()
        finish()
    }

    override fun onResume() {
        super.onResume()

        U2fManager.with(this).registerListener(this)
    }

    override fun onPause() {
        super.onPause()

        U2fManager.with(this).unregisterListener(this)
    }

    override fun onDeviceFound(device: U2FDevice) = AuthTokenLoginProcessor.process(device, getActivityViewModel())
}
