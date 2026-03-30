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
package io.timelimit.android.sync

import io.timelimit.android.integration.time.TimeApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TokenBucket (
    private val timeApi: TimeApi,
    private val interval: Int,
    private val tokenPerInterval: Int,
    private val maxTokens: Int,
    private val startTokens: Int
) {
    private val mutex = Mutex()

    private var tokens = startTokens
    private var nextRefill = timeApi.getCurrentUptimeInMillis() + interval

    init {
        if (interval <= 0) throw IllegalArgumentException()
        if (tokenPerInterval <= 0) throw IllegalArgumentException()
        if (maxTokens <= 0) throw IllegalArgumentException()
        if (startTokens !in 0..maxTokens) throw IllegalArgumentException()
    }

    private fun refillLocked(now: Long) {
        if (now < nextRefill) return

        val steps = (now - nextRefill) / interval + 1

        tokens = (tokens + steps * tokenPerInterval).coerceAtMost(maxTokens.toLong()).toInt()
        nextRefill += steps * interval
    }

    suspend fun consume() {
        mutex.withLock {
            while (true) {
                val now = timeApi.getCurrentUptimeInMillis()

                refillLocked(now)

                if (tokens == 0) {
                    suspendCancellableCoroutine<Unit> {
                        timeApi.runDelayed({
                            it.resume(Unit) {/* ignore */}
                        }, nextRefill - now)
                    }
                } else if (tokens < 0) {
                    throw IllegalStateException("has $tokens tokens")
                } else {
                    tokens -= 1

                    break
                }
            }
        }
    }
}