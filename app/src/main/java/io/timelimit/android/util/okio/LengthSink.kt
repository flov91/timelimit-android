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
package io.timelimit.android.util.okio

import okio.Buffer
import okio.Sink
import okio.Timeout

class LengthSink: Sink {
    private var lengthInternal = 0L

    val length get() = lengthInternal

    override fun write(source: Buffer, byteCount: Long) { lengthInternal += byteCount }
    override fun timeout(): Timeout = Timeout.NONE
    override fun flush() = Unit
    override fun close() = Unit
}