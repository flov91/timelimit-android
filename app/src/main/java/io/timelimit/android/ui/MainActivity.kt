/*
 * TimeLimit Copyright <C> 2019 - 2022 Jonas Lochmann
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

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import io.timelimit.android.Application
import io.timelimit.android.R
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.integration.platform.android.NotificationChannels
import io.timelimit.android.livedata.ignoreUnchanged
import io.timelimit.android.livedata.liveDataFromNullableValue
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.switchMap
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.u2f.U2fManager
import io.timelimit.android.u2f.protocol.U2FDevice
import io.timelimit.android.ui.login.AuthTokenLoginProcessor
import io.timelimit.android.ui.login.NewLoginFragment
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.AuthenticatedUser
import io.timelimit.android.ui.main.FragmentWithCustomTitle
import io.timelimit.android.ui.manage.parent.ManageParentFragmentArgs
import io.timelimit.android.ui.payment.ActivityPurchaseModel
import io.timelimit.android.ui.util.SyncStatusModel
import java.security.SecureRandom

class MainActivity : AppCompatActivity(), ActivityViewModelHolder, U2fManager.DeviceFoundListener {
    companion object {
        private const val AUTH_DIALOG_TAG = "adt"
        const val ACTION_USER_OPTIONS = "OPEN_USER_OPTIONS"
        const val EXTRA_USER_ID = "userId"
        private const val EXTRA_AUTH_HANDOVER = "authHandover"

        private var authHandover: Triple<Long, Long, AuthenticatedUser>? = null

        fun getAuthHandoverIntent(context: Context, user: AuthenticatedUser): Intent {
            val time = SystemClock.uptimeMillis()
            val key = SecureRandom().nextLong()

            authHandover = Triple(time, key, user)

            return Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_AUTH_HANDOVER, key)
        }

        fun getAuthHandoverFromIntent(intent: Intent): AuthenticatedUser? {
            val cachedHandover = authHandover
            val time = SystemClock.uptimeMillis()

            if (cachedHandover == null) return null

            if (cachedHandover.first < time - 2000 || cachedHandover.first - 1000 > time) {
                authHandover = null

                return null
            }

            if (intent.getLongExtra(EXTRA_AUTH_HANDOVER, 0) != cachedHandover.second) return null

            authHandover = null

            return cachedHandover.third
        }
    }

    private val currentNavigatorFragment = MutableLiveData<Fragment?>()
    private val syncModel: SyncStatusModel by lazy {
        ViewModelProviders.of(this).get(SyncStatusModel::class.java)
    }
    val purchaseModel: ActivityPurchaseModel by viewModels()
    override var ignoreStop: Boolean = false
    override val showPasswordRecovery: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        U2fManager.setupActivity(this)

        NotificationChannels.createNotificationChannels(getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager, this)

        if (savedInstanceState == null) {
            NavHostFragment.create(R.navigation.nav_graph).let { navhost ->
                supportFragmentManager.beginTransaction()
                        .replace(R.id.nav_host, navhost)
                        .setPrimaryNavigationFragment(navhost)
                        .commitNow()
            }
        }

        // init the purchaseModel
        purchaseModel.getApplication<Application>()

        // prepare livedata
        val customTitle = currentNavigatorFragment.switchMap {
            if (it != null && it is FragmentWithCustomTitle) {
                it.getCustomTitle()
            } else {
                liveDataFromNullableValue(null as String?)
            }
        }.ignoreUnchanged()

        val title = Transformations.map(customTitle) {
            if (it == null) {
                getString(R.string.app_name)
            } else {
                it
            }
        }

        // up button
        getNavController().addOnDestinationChangedListener(object: NavController.OnDestinationChangedListener {
            override fun onDestinationChanged(controller: NavController, destination: NavDestination, arguments: Bundle?) {
                supportActionBar!!.setDisplayHomeAsUpEnabled(controller.previousBackStackEntry != null)
            }
        })

        // init if not yet done
        DefaultAppLogic.with(this)

        val fragmentContainer = supportFragmentManager.findFragmentById(R.id.nav_host)!!
        val fragmentContainerManager = fragmentContainer.childFragmentManager

        fragmentContainerManager.registerFragmentLifecycleCallbacks(object: FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
                super.onFragmentStarted(fm, f)

                if (!(f is DialogFragment)) {
                    currentNavigatorFragment.value = f
                }
            }

            override fun onFragmentStopped(fm: FragmentManager, f: Fragment) {
                super.onFragmentStopped(fm, f)

                if (currentNavigatorFragment.value === f) {
                    currentNavigatorFragment.value = null
                }
            }
        }, false)

        title.observe(this, Observer { setTitle(it) })
        syncModel.statusText.observe(this, Observer { supportActionBar!!.subtitle = it })

        handleParameters(intent)

        val hasDeviceId = getActivityViewModel().logic.deviceId.map { it != null }.ignoreUnchanged()
        val hasParentKey = getActivityViewModel().logic.database.config().getParentModeKeyLive().map { it != null }.ignoreUnchanged()

        hasDeviceId.observe(this) {
            val rootDestination = getNavController().backQueue.getOrNull(1)?.destination?.id

            if (!it) getActivityViewModel().logOut()

            if (
                it && rootDestination != R.id.overviewFragment ||
                !it && rootDestination == R.id.overviewFragment
            ) {
                restartContent()
            }
        }

        hasParentKey.observe(this) {
            val rootDestination = getNavController().backQueue.getOrNull(1)?.destination?.id

            if (
                it && rootDestination != R.id.parentModeFragment ||
                !it && rootDestination == R.id.parentModeFragment
            ) {
                restartContent()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem) = when {
        item.itemId == android.R.id.home -> {
            onBackPressed()

            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onStart() {
        super.onStart()

        purchaseModel.queryAndProcessPurchasesAsync()

        IsAppInForeground.reportStart()

        syncModel.handleStart()
    }

    override fun onStop() {
        super.onStop()

        if ((!isChangingConfigurations) && (!ignoreStop)) {
            getActivityViewModel().logOut()
        }

        IsAppInForeground.reportStop()
    }

    override fun onDestroy() {
        super.onDestroy()

        purchaseModel.forgetActivityCheckout()
    }

    private fun handleParameters(intent: Intent?): Boolean {
        // do not return true in this case because this should not affect other navigations
        intent?.also {
            getAuthHandoverFromIntent(intent)?.also { auth ->
                getActivityViewModel().setAuthenticatedUser(auth)
            }
        }

        if (intent?.action == ACTION_USER_OPTIONS) {
            val userId = intent.getStringExtra(EXTRA_USER_ID)
            val valid = userId != null && try { IdGenerator.assertIdValid(userId); true } catch (ex: IllegalArgumentException) {false}

            if (userId != null && valid) {
                getNavController().popBackStack(R.id.overviewFragment, true)
                getNavController().handleDeepLink(
                        getNavController().createDeepLink()
                                .setDestination(R.id.manageParentFragment)
                                .setArguments(ManageParentFragmentArgs(userId).toBundle())
                                .createTaskStackBuilder()
                                .intents
                                .first()
                )

                return true
            }
        }

        return false
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        if ((intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT == Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) {
            return
        }

        if (handleParameters(intent)) return

        // at these screens, some users restart the App
        // if they want to continue after opening the mail
        // because they don't understand how to use the list of running Apps ...
        // Due to that, on the relevant screens, the App does not
        // go back to the start when opening it again
        val isImportantScreen = when (getNavController().currentDestination?.id) {
            R.id.setupParentModeFragment -> true
            R.id.restoreParentPasswordFragment -> true
            R.id.linkParentMailFragment -> true
            else -> false
        }

        if (!isImportantScreen) restartContent()
    }

    private fun restartContent() {
        while (getNavController().popBackStack()) {/* do nothing */}

        getNavController().clearBackStack(R.id.launchFragment)
        getNavController().navigate(R.id.launchFragment)
    }

    override fun getActivityViewModel(): ActivityViewModel {
        return ViewModelProviders.of(this).get(ActivityViewModel::class.java)
    }

    private fun getNavHostFragment(): NavHostFragment {
        return supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
    }

    private fun getNavController(): NavController {
        return getNavHostFragment().navController
    }

    override fun showAuthenticationScreen() {
        if (supportFragmentManager.findFragmentByTag(AUTH_DIALOG_TAG) == null) {
            NewLoginFragment().showSafe(supportFragmentManager, AUTH_DIALOG_TAG)
        }
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
