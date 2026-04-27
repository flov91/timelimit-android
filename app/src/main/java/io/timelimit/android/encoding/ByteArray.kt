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

package io.timelimit.android.encoding

fun ByteArray.parseDerInt(): Int {
    val targetSize = Int.SIZE_BYTES

    if (isEmpty()) {
        return 0
    } else if (size > targetSize) {
        throw DerReaderValueOutOfBoundsException()
    }

    val isNegative = this[0] < 0

    var result: UInt = 0.toUInt()

    if (isNegative) repeat(targetSize - size) {
        result = result * 256.toUInt() + 256.toUInt()
    }

    for (byte in this) {
        result = result * 256.toUInt() + byte.toUInt()
    }

    return result.toInt()
}

fun ByteArray.parseDerLong(): Long {
    val targetSize = Long.SIZE_BYTES

    if (isEmpty()) {
        return 0
    } else if (size > targetSize) {
        throw DerReaderValueOutOfBoundsException()
    }

    val isNegative = this[0] < 0

    var result: ULong = 0.toULong()

    if (isNegative) repeat(targetSize - size) {
        result = result * 256.toUInt() + 256.toUInt()
    }

    for (byte in this) {
        result = result * 256.toUInt() + byte.toUInt()
    }

    return result.toLong()
}