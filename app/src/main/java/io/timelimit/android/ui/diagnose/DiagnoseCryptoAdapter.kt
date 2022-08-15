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
package io.timelimit.android.ui.diagnose

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.R
import io.timelimit.android.data.model.CryptContainerMetadata
import io.timelimit.android.databinding.DiagnoseSyncCardBinding

class DiagnoseCryptoAdapter: PagedListAdapter<CryptContainerMetadata, DiagnoseCryptoAdapter.Holder>(diffCallback) {
    companion object {
        private val diffCallback = object: DiffUtil.ItemCallback<CryptContainerMetadata>() {
            override fun areContentsTheSame(oldItem: CryptContainerMetadata, newItem: CryptContainerMetadata) = oldItem == newItem
            override fun areItemsTheSame(oldItem: CryptContainerMetadata, newItem: CryptContainerMetadata) = oldItem.cryptContainerId == newItem.cryptContainerId
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
            DiagnoseSyncCardBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
            ).apply {
                copyButton.setOnClickListener {
                    val clipboard = it.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("TimeLimit", text))

                    Toast.makeText(it.context, R.string.diagnose_sync_copied_to_clipboard, Toast.LENGTH_SHORT).show()
                }
            }
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)

        holder.binding.text = if (item == null) "null"
        else StringBuilder().also { builder ->
            builder.append("container id: ").append(item.cryptContainerId).append('\n')
            builder.append("device id: ").append(item.deviceId ?: "null").append('\n')
            builder.append("category id: ").append(item.categoryId ?: "null").append('\n')
            builder.append("type: ").append(item.type).append('\n')
            builder.append("current generation: ").append(item.currentGeneration).append('\n')
            builder.append("next counter: ").append(item.nextCounter).append('\n')
            builder.append("server version: ").append(item.serverVersion).append('\n')
            builder.append("status: ").append(
                when (item.status) {
                    CryptContainerMetadata.ProcessingStatus.MissingKey -> "missing key"
                    CryptContainerMetadata.ProcessingStatus.DowngradeDetected -> "downgrade"
                    CryptContainerMetadata.ProcessingStatus.Unprocessed -> "unprocessed"
                    CryptContainerMetadata.ProcessingStatus.CryptoDamage -> "crypto damage"
                    CryptContainerMetadata.ProcessingStatus.ContentDamage -> "content damaged"
                    CryptContainerMetadata.ProcessingStatus.Finished -> "finished"
                }
            ).append('\n')
        }.toString()
    }

    class Holder(val binding: DiagnoseSyncCardBinding): RecyclerView.ViewHolder(binding.root)
}