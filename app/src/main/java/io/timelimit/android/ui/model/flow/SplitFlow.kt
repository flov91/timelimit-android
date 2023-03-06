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
package io.timelimit.android.ui.model.flow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CaseScope<LocalStateType>(
    val scope: CoroutineScope,
    val className: Class<LocalStateType>?
) {
    inline fun <SuperStateType, LocalStateType : SuperStateType> updateMethod(
        crossinline parent: ((SuperStateType) -> SuperStateType) -> Unit
    ): ((LocalStateType) -> SuperStateType) -> Unit = { request ->
        parent { oldState ->
            if (!scope.isActive) oldState
            else if (className != null && className.isInstance(oldState)) request(oldState as LocalStateType)
            else oldState
        }
    }

    fun <T> share(flow: Flow<T>): SharedFlow<T> = flow.shareIn(scope, SharingStarted.Lazily, 1)
}

class Case<T, R>(
    val className: Class<out Any>?,
    val key: (T) -> Any?,
    val producer: CaseScope<T>.(Flow<T>, Any?) -> Flow<R>
) {
    companion object {
        fun <T, R> nil(producer: CaseScope<Unit>.(Flow<Unit>) -> Flow<R>) = Case<T, R>(
            className = null,
            key = {}
        ) { flow, _ -> producer(this as CaseScope<Unit>, flow.map { Unit }) }

        inline fun <T, R, reified C : Any> simple(
            crossinline producer: CaseScope<C>.(Flow<C>) -> Flow<R>
        ) = Case<T, R>(
            className = C::class.java,
            key = {}
        ) { flow, _ -> producer(this as CaseScope<C>, flow as Flow<C>) }

        inline fun <T, R, reified C : Any, K> withKey(
            crossinline withKey: (C) -> K,
            crossinline producer: CaseScope<C>.(K, Flow<C>) -> Flow<R>
        ) = Case<T, R>(
            className = C::class.java,
            key = { withKey(it as C) }
        ) { flow, key -> producer(this as CaseScope<C>, key as K, flow as Flow<C>) }
    }

    internal fun doesMatch(value: Any?): Boolean = (className == null && value == null) || (className != null && className.isInstance(value))
}

fun <T, R> Flow<T>.splitConflated(vararg cases: Case<T, R>): Flow<R> {
    val input = this

    return channelFlow<R> {
        val inputChannel = Channel<T>()

        launch { input.collect { inputChannel.send(it) }; inputChannel.close() }

        var value = inputChannel.receive()

        while (true) {
            val case = cases.first { it.doesMatch(value) }
            val key = case.key(value)
            val relayChannel = Channel<T>(Channel.CONFLATED)
            val job = launch {
                val scope = CaseScope<T>(this, case.className as Class<T>?)
                val inputFlow = flow { relayChannel.consumeEach { emit(it) } }

                case.producer(scope, inputFlow, key).collect { send(it) }
            }

            try {
                relayChannel.send(value)

                while (true) {
                    value = inputChannel.receive()

                    val newCase = cases.first { it.doesMatch(value) }

                    if (case !== newCase) break
                    if (case.key(value) != key) break

                    relayChannel.send(value)
                }
            } finally {
                relayChannel.cancel()
                job.cancel()
            }
        }
    }
}