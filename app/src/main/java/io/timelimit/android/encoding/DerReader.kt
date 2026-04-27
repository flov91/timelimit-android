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

import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream

interface DerReader {
    fun read(length: Long): ByteArray
    fun isEof(): Boolean
}

class InputStreamDerReader private constructor(private val stream: PushbackInputStream): DerReader {
    companion object {
        fun fromStream(stream: InputStream) =
            if (stream is PushbackInputStream) InputStreamDerReader(stream)
            else InputStreamDerReader(PushbackInputStream(stream))
    }

    override fun read(length: Long): ByteArray {
        if (length > Int.MAX_VALUE) {
            throw DerReaderValueOutOfBoundsException()
        }

        val result = ByteArray(length.toInt())

        var writePointer = 0

        while (writePointer < length) {
            val bytesRead = stream.read(result, writePointer, result.size - writePointer)

            if (bytesRead < 0) {
                throw DerReaderEofException()
            } else {
                writePointer += bytesRead
            }
        }

        return result
    }

    override fun isEof(): Boolean {
        val byte = stream.read()

        if (byte < 0) {
            return true
        } else {
            stream.unread(byte)

            return false
        }
    }
}

class LengthLimitDerReader(
    private val nested: DerReader,
    private var remainingBytes: Long
): DerReader {
    override fun read(length: Long): ByteArray {
        if (length > remainingBytes) throw DerReaderEofException()

        return nested.readSafe(length).also { remainingBytes -= length }
    }

    override fun isEof(): Boolean = remainingBytes == 0L
}

fun DerReader.readSafe(length: Long): ByteArray = read(length).also {
    if (it.size < length) throw DerReaderEofException()
    else if (it.size > length) throw IllegalStateException()
}

fun DerReader.readLength(): Long {
    val firstByte = readSafe(1)[0].toUByte()

    if (firstByte < 128u) return firstByte.toLong()

    val lengthBytes = firstByte xor 128u

    if (lengthBytes <= 1.toUByte()) {
        throw DerReaderValueOutOfBoundsException()
    }

    if (lengthBytes.toInt() > Long.SIZE_BYTES) {
        throw DerReaderValueOutOfBoundsException()
    }

    val lengthData = readSafe(lengthBytes.toLong())

    if (lengthData[0] == 0.toByte()) {
        throw DerReaderNotNormalizedException()
    }

    var result = 0L

    for (byte in lengthData) {
        result = result * 256 + byte.toUByte().toLong()
    }

    return result
}

fun DerReader.readBoolean(): Boolean {
    val tag = DerIdentifier.derDecode(this)

    if (tag != DerIdentifier.BOOLEAN) {
        throw DerReaderWrongTagException()
    }

    val length = readLength()

    if (length != 1L) {
        throw DerReaderValueOutOfBoundsException()
    }

    val data = read(1)[0].toUByte()

    return when (data) {
        0.toUByte() -> false
        255.toUByte() -> true
        else -> throw DerReaderValueOutOfBoundsException()
    }
}

fun DerReader.readIntegerLengthAndValue(): ByteArray {
    val length = readLength()

    if (length == 0L) return byteArrayOf(0)

    val data = read(length)

    if (length >= 2) {
        if (data[0].toUByte() == 0.toUByte() || data[0].toUByte() == 255.toUByte()) {
            if (data[0].toUByte() and 128.toUByte() == data[1].toUByte() and 128.toUByte()) {
                throw DerReaderNotNormalizedException()
            }
        }
    }

    return data
}

fun DerReader.readLong(): Long {
    val tag = DerIdentifier.derDecode(this)

    if (tag != DerIdentifier.INTEGER) {
        throw DerReaderWrongTagException()
    }

    return readIntegerLengthAndValue().parseDerLong()
}

fun DerReader.readEnumerated(): Int {
    val tag = DerIdentifier.derDecode(this)

    if (tag != DerIdentifier.ENUMERATED) {
        throw DerReaderWrongTagException()
    }

    return readIntegerLengthAndValue().parseDerInt()
}

fun DerReader.readUtf8String(): String {
    val tag = DerIdentifier.derDecode(this)

    if (tag != DerIdentifier.UTF8STRING) {
        throw DerReaderWrongTagException()
    }

    val length = readLength()
    val data = read(length)

    return data.decodeToString()
}

fun <T> DerReader.readSequence(nested: (DerReader) -> T): T {
    val tag = DerIdentifier.derDecode(this)

    if (tag != DerIdentifier.SEQUENCE) {
        throw DerReaderWrongTagException()
    }

    val length = readLength()

    LengthLimitDerReader(this, length).let { reader ->
        return nested(reader).also {
            if (!reader.isEof()) {
                throw DerReaderUnexpectedFurtherData()
            }
        }
    }
}

fun <T> DerReader.readSequenceOf(item: (DerReader) -> T): List<T> = readSequence { reader ->
    val result = mutableListOf<T>()

    while (!reader.isEof()) {
        result.add(item(reader))
    }

    result
}

sealed class DerReaderException: IOException()
class DerReaderEofException: DerReaderException()
class DerReaderValueOutOfBoundsException: DerReaderException()
class DerReaderNotNormalizedException: DerReaderException()
class DerReaderWrongTagException: DerReaderException()
class DerReaderUnexpectedFurtherData: DerReaderException()