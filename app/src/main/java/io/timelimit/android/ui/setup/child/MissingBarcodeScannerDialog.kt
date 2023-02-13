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

package io.timelimit.android.ui.setup.child

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.timelimit.android.R

data class BarcodeScannerItem(
    val packageName: String,
    val title: String,
    val author: String,
    val license: String
) {
    companion object {
        val ITEMS = listOf(
            BarcodeScannerItem(
                packageName = "de.markusfisch.android.binaryeye",
                title = "Binary Eye",
                author = "Markus Fisch",
                license = "MIT License"
            ),
            BarcodeScannerItem(
                packageName = "com.atharok.barcodescanner",
                title = "Scanner: QR Code and Products",
                author = "Atharok",
                license = "GPLv3"
            ),
            BarcodeScannerItem(
                packageName = "com.google.zxing.client.android",
                title = "Barcode Scanner",
                author = "ZXing Team",
                license = "Apache License 2.0"
            ),
        )
    }
}

@Composable
fun MissingBarcodeScannerDialog(
    onDismissRequest: () -> Unit,
    onOpenStoreEntry: (BarcodeScannerItem) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.scan_missing_title)) },
        text = {
            Column {
                Text(stringResource(R.string.scan_missing_text))

                for (scanner in BarcodeScannerItem.ITEMS) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = {
                                onOpenStoreEntry(scanner)
                                onDismissRequest()
                            })
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(scanner.title, style = MaterialTheme.typography.h6)
                        Text("${scanner.author} - ${scanner.license}")
                    }
                }
            }
        },
        buttons = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.generic_cancel))
            }
        }
    )
}