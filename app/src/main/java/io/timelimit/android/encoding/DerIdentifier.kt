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

data class DerIdentifier(
    val cls: Class,
    val pc: PrimitiveConstructed,
    val number: ULong
) {
    enum class Class {
        Universal,
        Application,
        ContextSpecific,
        Private,
    }

    enum class PrimitiveConstructed {
        Primitive,
        Constructed,
    }

    companion object {
        private const val MAX_SINGLE_BYTE_NUMBER = 30u

        val BOOLEAN = DerIdentifier(Class.Universal, PrimitiveConstructed.Primitive, 1.toULong())
        val INTEGER = DerIdentifier(Class.Universal, PrimitiveConstructed.Primitive, 2.toULong())
        val OID = DerIdentifier(Class.Universal, PrimitiveConstructed.Primitive, 6.toULong())
        val ENUMERATED = DerIdentifier(Class.Universal, PrimitiveConstructed.Primitive, 10.toULong())
        val UTF8STRING = DerIdentifier(Class.Universal, PrimitiveConstructed.Primitive, 12.toULong())
        val SEQUENCE = DerIdentifier(Class.Universal, PrimitiveConstructed.Constructed, 16.toULong())

        fun derDecode(reader: DerReader): DerIdentifier {
            val firstByte = reader.readSafe(1)[0].toUByte()

            val cls = when (firstByte and (128 + 64).toUByte()) {
                0.toUByte() -> Class.Universal
                64.toUByte() -> Class.Application
                128.toUByte() -> Class.ContextSpecific
                128.toUByte() or 64.toUByte() -> Class.Private
                else -> throw IllegalStateException()
            }

            val pc = when (firstByte and 32.toUByte()) {
                0.toUByte() -> PrimitiveConstructed.Primitive
                32.toUByte() -> PrimitiveConstructed.Constructed
                else -> throw IllegalStateException()
            }

            val firstByteNumber = firstByte and 31.toUByte()

            val number = if (firstByteNumber <= MAX_SINGLE_BYTE_NUMBER) {
                firstByteNumber.toULong()
            } else {
                var numberBuffer = 0.toULong()

                while (true) {
                    val numberPart = reader.readSafe(1)[0].toUByte()
                    val hasNext = numberPart and 128.toUByte() == 128.toUByte()
                    val actualNumber = numberPart and 127.toUByte()

                    if (numberBuffer == 0.toULong() && actualNumber == 0.toUByte()) {
                        throw DerReaderNotNormalizedException()
                    }

                    val oldNumberBuffer = numberBuffer; numberBuffer *= 128.toULong()

                    if (numberBuffer / 128.toULong() != oldNumberBuffer) {
                        throw DerReaderValueOutOfBoundsException()
                    }

                    numberBuffer += actualNumber

                    if (!hasNext) break
                }

                if (numberBuffer <= MAX_SINGLE_BYTE_NUMBER) {
                    throw DerReaderNotNormalizedException()
                }

                numberBuffer
            }

            return DerIdentifier(
                cls = cls,
                pc = pc,
                number = number
            )
        }
    }

    init {
        if (number > MAX_SINGLE_BYTE_NUMBER && pc == PrimitiveConstructed.Primitive) {
            throw IllegalStateException()
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun derEncode(writer: DerWriter) {
        val classBits = when (cls) {
            Class.Universal -> 0.toUByte()
            Class.Application -> 64.toUByte()
            Class.ContextSpecific -> 128.toUByte()
            Class.Private -> 128.toUByte() or 64.toUByte()
        }

        val pcBit = when (pc) {
            PrimitiveConstructed.Primitive -> 0.toUByte()
            PrimitiveConstructed.Constructed -> 32.toUByte()
        }

        if (number <= MAX_SINGLE_BYTE_NUMBER) {
            val byte = classBits or pcBit or number.toUByte()

            writer.write(byteArrayOf(byte.toByte()))
        } else {
            val byte = classBits or pcBit or 31.toUByte()

            writer.write(byteArrayOf(byte.toByte()))

            val encodedNumber = UByteArray(Long.SIZE_BYTES * 2)
            var remainingNumberToEncode = number
            var encodedNumberWriteIndex = encodedNumber.size

            while (remainingNumberToEncode != 0.toULong()) {
                val numberPart = (remainingNumberToEncode % 128u).toUByte()

                if (encodedNumberWriteIndex == encodedNumber.size) {
                    encodedNumber[--encodedNumberWriteIndex] = numberPart
                } else {
                    encodedNumber[--encodedNumberWriteIndex] = numberPart or 128.toUByte()
                }

                remainingNumberToEncode /= 128u
            }

            writer.write(
                encodedNumber.sliceArray(
                    encodedNumberWriteIndex..encodedNumber.size
                ).toByteArray()
            )
        }
    }
}