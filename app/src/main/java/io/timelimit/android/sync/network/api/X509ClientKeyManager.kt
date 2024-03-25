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
package de.wivewa.android.network

import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.X509KeyManager

class X509ClientKeyManager(
    private val privateKey: PrivateKey,
    private val certificateChain: List<X509Certificate>
): X509KeyManager {
    companion object {
        private const val ALIAS_NAME = "key"
    }

    override fun chooseClientAlias(
        keyType: Array<out String>,
        principal: Array<out Principal>?,
        socket: Socket?
    ): String = ALIAS_NAME

    override fun chooseServerAlias(
        keyType: String,
        principal: Array<out Principal>?,
        socket: Socket?
    ): String? = null

    override fun getCertificateChain(alias: String): Array<X509Certificate>? = when (alias) {
        ALIAS_NAME -> certificateChain.toTypedArray()
        else -> null
    }

    override fun getClientAliases(
        keyType: String,
        issuers: Array<out Principal>?
    ): Array<String> = arrayOf(ALIAS_NAME)

    override fun getPrivateKey(alias: String): PrivateKey? = when (alias) {
        ALIAS_NAME -> privateKey
        else -> null
    }

    override fun getServerAliases(
        keyType: String,
        issuers: Array<out Principal>?
    ): Array<String> = emptyArray()
}