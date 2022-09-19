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
package io.timelimit.android.extensions

import okio.ByteString.Companion.toByteString
import java.nio.ByteBuffer

fun Int.toByteArray() = ByteBuffer.allocate(Int.SIZE_BYTES).also {
    it.putInt(this)
    it.rewind()
}.toByteString().toByteArray()

fun Long.toByteArray() = ByteBuffer.allocate(Long.SIZE_BYTES).also {
    it.putLong(this)
    it.rewind()
}.toByteString().toByteArray()