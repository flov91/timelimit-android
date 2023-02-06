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
package io.timelimit.android.extensions

import io.timelimit.android.util.Option
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

fun <T> Flow<T?>.takeWhileNotNull(): Flow<T> = this.transformWhile { value ->
    if (value != null) {
        emit(value)

        true
    } else false
}

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<Boolean>.whileTrue(producer: suspend () -> Flow<T>): Flow<T> =
    distinctUntilChanged()
        .transformLatest {
            if (it) emitAll(producer().map { Option.Some(it) })
            else emit(null)
        }
        .takeWhileNotNull()
        .map { it.value }