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
package io.timelimit.android.integration.platform.android

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.UserManager
import io.timelimit.android.R
import io.timelimit.android.integration.platform.PlatformFeature

object AndroidFeatures {
    private const val FEATURE_ADB = "adb"
    private const val FEATURE_CONFIG_PRIVATE_DNS = "dns"
    const val FEATURE_ADD_USER = "add_user"
    const val FEATURE_USER_SWITCH = "user_switch"
    private const val FEATURE_VPN = "vpn"
    private const val FEATURE_UNKNOWN_SOURCES = "unknown_sources"

    fun applyBlockedFeatures(features: Set<String>, policyManager: DevicePolicyManager, admin: ComponentName): Boolean {
        fun apply(feature: String, restriction: String) {
            if (features.contains(feature)) policyManager.addUserRestriction(admin, restriction)
            else policyManager.clearUserRestriction(admin, restriction)
        }

        apply(FEATURE_ADB, UserManager.DISALLOW_DEBUGGING_FEATURES)

        if (VERSION.SDK_INT >= VERSION_CODES.Q) {
            apply(FEATURE_CONFIG_PRIVATE_DNS, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
        }

        apply(FEATURE_ADD_USER, UserManager.DISALLOW_ADD_USER)

        if (VERSION.SDK_INT >= VERSION_CODES.P) {
            apply(FEATURE_USER_SWITCH, UserManager.DISALLOW_USER_SWITCH)
        }

        apply(FEATURE_VPN, UserManager.DISALLOW_CONFIG_VPN)

        if (VERSION.SDK_INT >= VERSION_CODES.Q) {
            apply(FEATURE_UNKNOWN_SOURCES, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY)
        }

        return true
    }

    fun getFeaturesAssumingDeviceOwnerGranted(context: Context): List<PlatformFeature> {
        val result = mutableListOf<PlatformFeature>()

        result.add(PlatformFeature(
            id = FEATURE_ADB,
            title = context.getString(R.string.dummy_app_feature_adb)
        ))

        if (VERSION.SDK_INT >= VERSION_CODES.Q) {
            result.add(
                PlatformFeature(
                    id = FEATURE_CONFIG_PRIVATE_DNS,
                    title = context.getString(R.string.dummy_app_feature_dns)
                )
            )
        }

        result.add(PlatformFeature(
            id = FEATURE_ADD_USER,
            title = context.getString(R.string.dummy_app_feature_add_user)
        ))

        if (VERSION.SDK_INT >= VERSION_CODES.P) {
            result.add(PlatformFeature(
                id = FEATURE_USER_SWITCH,
                title = context.getString(R.string.dummy_app_feature_switch_user)
            ))
        }

        result.add(PlatformFeature(
            id = FEATURE_VPN,
            title = context.getString(R.string.dummy_app_feature_vpn)
        ))

        if (VERSION.SDK_INT >= VERSION_CODES.Q) {
            result.add(PlatformFeature(
                id = FEATURE_UNKNOWN_SOURCES,
                title = context.getString(R.string.dummy_app_feature_unknown_sources)
            ))
        }

        return result
    }
}