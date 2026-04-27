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

import okio.ByteString.Companion.toByteString
import java.io.OutputStream
import java.nio.ByteBuffer
import kotlin.experimental.or

// Overview: https://www.itu.int/en/ITU-T/asn1/pages/introduction.aspx
// DER: https://www.itu.int/rec/recommendation.asp?lang=en&parent=T-REC-X.690-202102-I
interface DerWriter {
    fun write(data: ByteArray)
}

class LengthDerWriter: DerWriter {
    private var counter = 0L

    val length: Long get() = counter

    override fun write(data: ByteArray) {
        counter += data.size
    }
}

class OutputStreamDerWriter(private val stream: OutputStream): DerWriter {
    override fun write(data: ByteArray) {
        stream.write(data)
    }
}

fun DerWriter.writeLength(length: Long) {
    if (length < 128) {
        write(byteArrayOf(length.toByte()))
    } else {
        val encodedLength = ByteBuffer.allocate(Long.SIZE_BYTES).also {
            it.putLong(length)
            it.rewind()
        }.toByteString().toByteArray()

        val firstTakenByte = encodedLength.lastIndexOf(0) + 1
        val takenBytesLength = Long.SIZE_BYTES - firstTakenByte

        write(byteArrayOf(128.toByte() or takenBytesLength.toByte()))
        write(encodedLength.sliceArray(firstTakenByte until encodedLength.size))
    }
}

fun DerWriter.writeBoolean(value: Boolean) {
    DerIdentifier.BOOLEAN.derEncode(this)
    writeLength(1)
    write(byteArrayOf(if (value) -1 else 0)) // 255 or 0
}

fun DerWriter.writeIntegerLengthAndValue(encodedValue: ByteArray) {
    if (encodedValue.size == 0) {
        throw IllegalStateException()
    }

    val valueNegative = encodedValue[0] < 0

    val firstTakenByte = encodedValue.lastIndexOf(
        if (valueNegative) -1 else 0
    ) + 1

    // avoid making numbers negative incorrectly
    val takeExtraByte = !valueNegative && firstTakenByte < encodedValue.size && encodedValue[firstTakenByte] < 0

    val takenBytesLength =
        (encodedValue.size - firstTakenByte) + (if (takeExtraByte) 1 else 0)

    writeLength(takenBytesLength.toLong())

    if (takeExtraByte) {
        write(byteArrayOf(0))
    }

    write(encodedValue.sliceArray(firstTakenByte until encodedValue.size))
}

fun DerWriter.writeInteger(value: Int) {
    DerIdentifier.INTEGER.derEncode(this)

    writeIntegerLengthAndValue(ByteBuffer.allocate(Int.SIZE_BYTES).also {
        it.putInt(value)
        it.rewind()
    }.toByteString().toByteArray())
}

fun DerWriter.writeInteger(value: Long) {
    DerIdentifier.INTEGER.derEncode(this)

    writeIntegerLengthAndValue(ByteBuffer.allocate(Long.SIZE_BYTES).also {
        it.putLong(value)
        it.rewind()
    }.toByteString().toByteArray())
}

fun DerWriter.writeEnumerated(value: Int) {
    DerIdentifier.ENUMERATED.derEncode(this)

    writeIntegerLengthAndValue(ByteBuffer.allocate(Int.SIZE_BYTES).also {
        it.putInt(value)
        it.rewind()
    }.toByteString().toByteArray())
}

fun DerWriter.writeUtf8String(value: String) {
    val encodedValue = value.encodeToByteArray()

    DerIdentifier.UTF8STRING.derEncode(this)
    writeLength(encodedValue.size.toLong())
    write(encodedValue)
}

fun DerWriter.writeSequence(nested: (DerWriter) -> Unit) {
    val length = LengthDerWriter().also { nested(it) }.length

    DerIdentifier.SEQUENCE.derEncode(this)
    writeLength(length)
    nested(this)
}

fun <T> DerWriter.writeSequenceOf(items: Iterable<T>, serializer: ((DerWriter, T) -> Unit)) = writeSequence { writer ->
    items.forEach { item ->
        serializer(writer, item)
    }
}