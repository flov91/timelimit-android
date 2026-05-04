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
package io.timelimit.android.proto

import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import io.timelimit.android.encoding.DerReader
import io.timelimit.android.encoding.DerReaderUnexpectedFurtherData
import io.timelimit.android.encoding.DerWriter
import io.timelimit.android.encoding.InputStreamDerReader
import io.timelimit.android.encoding.OutputStreamDerWriter
import okio.Buffer
import okio.use
import java.io.ByteArrayInputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

fun <A : Message<A, B>, B : Message.Builder<A, B>> Message<A, B>.encodeDeflated(): ByteArray = Buffer().also { buffer ->
    DeflaterOutputStream(buffer.outputStream()).use { this.encode(it) }
}.readByteArray()

fun <T> ProtoAdapter<T>.decodeInflated(input: ByteArray): T = InflaterInputStream(ByteArrayInputStream(input)).use {
    this.decode(it)
}

fun encodeDeflatedDer(serialize: (DerWriter) -> Unit): ByteArray = Buffer().also { buffer ->
    DeflaterOutputStream(buffer.outputStream()).use {
        OutputStreamDerWriter(it).also { serialize(it) }
    }
}.readByteArray()

fun <T> ByteArray.decodeInflatedDer(parser: (DerReader) -> T): T =
    InflaterInputStream(ByteArrayInputStream(this)).use {
        val reader = InputStreamDerReader.fromStream(it)

        parser(reader).also {
            if (!reader.isEof()) {
                throw DerReaderUnexpectedFurtherData()
            }
        }
    }