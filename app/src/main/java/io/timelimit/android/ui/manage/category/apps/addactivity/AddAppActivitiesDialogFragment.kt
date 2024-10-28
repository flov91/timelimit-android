/*
 * TimeLimit Copyright <C> 2019 - 2024 Jonas Lochmann
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
package io.timelimit.android.ui.manage.category.apps.addactivity

import android.app.Dialog
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup.MarginLayoutParams
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import io.timelimit.android.R
import io.timelimit.android.databinding.FragmentAddCategoryActivitiesBinding
import io.timelimit.android.extensions.addOnTextChangedListener
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.sync.actions.AddCategoryAppsAction
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.manage.category.apps.AddAppsOrActivitiesModel

class AddAppActivitiesDialogFragment: DialogFragment() {
    companion object {
        private const val DIALOG_TAG = "AddAppActivitiesDialogFragment"
        private const val PARAMS = "params"
        private const val SELECTED_ACTIVITIES = "selectedActivities"

        fun newInstance(params: AddActivitiesParams) = AddAppActivitiesDialogFragment().apply {
            arguments = Bundle().apply { putParcelable(PARAMS, params) }
        }
    }

    private val adapter = AddAppActivityAdapter()
    private val baseModel: AddAppsOrActivitiesModel by viewModels()
    private val model: AddActivitiesModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            adapter.selectedActivities.clear()
            savedInstanceState.getStringArray(SELECTED_ACTIVITIES)!!.forEach { adapter.selectedActivities.add(it) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putStringArray(SELECTED_ACTIVITIES, adapter.selectedActivities.toTypedArray())
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val params = requireArguments().getParcelable<AddActivitiesParams>(PARAMS)!!
        val auth = getActivityViewModel(requireActivity())
        val binding = FragmentAddCategoryActivitiesBinding.inflate(LayoutInflater.from(context))

        baseModel.init(params.base)
        baseModel.isAuthValid(auth).observe(this) { if (!it) dismissAllowingStateLoss() }

        model.init(params)
        model.searchTerm.value = binding.search.text.toString()
        binding.search.addOnTextChangedListener { model.searchTerm.value = binding.search.text.toString() }

        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        model.filteredActivities.observe(this) { list ->
            val selectedActivities = adapter.selectedActivities
            val visibleActivities = list.map { it.className }
            val hiddenSelectedActivities = selectedActivities.toMutableSet().apply { removeAll(visibleActivities) }.size

            adapter.data = list

            binding.hiddenEntries = if (hiddenSelectedActivities == 0)
                null
            else
                resources.getQuantityString(R.plurals.category_apps_add_dialog_hidden_entries, hiddenSelectedActivities, hiddenSelectedActivities)
        }

        model.emptyViewText.observe(this) {
            binding.emptyViewText = when (it!!) {
                AddActivitiesModel.EmptyViewText.None -> null
                AddActivitiesModel.EmptyViewText.EmptyShown -> getString(R.string.category_apps_add_activity_empty_shown)
                AddActivitiesModel.EmptyViewText.EmptyFiltered -> getString(R.string.category_apps_add_activity_empty_filtered)
                AddActivitiesModel.EmptyViewText.EmptyUnfiltered -> getString(R.string.category_apps_add_activity_empty_unfiltered)
            }
        }

        binding.someOptionsDisabledDueToChildAuthentication = params.base.isSelfLimitAddingMode
        binding.cancelButton.setOnClickListener { dismissAllowingStateLoss() }
        binding.addActivitiesButton.setOnClickListener {
            if (adapter.selectedActivities.isNotEmpty()) {
                auth.tryDispatchParentAction(
                        action = AddCategoryAppsAction(
                                categoryId = params.base.categoryId,
                                packageNames = adapter.selectedActivities.toList().map { "${params.packageName}:$it" }
                        ),
                        allowAsChild = params.base.isSelfLimitAddingMode
                )
            }

            dismissAllowingStateLoss()
        }


        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            v.updateLayoutParams<MarginLayoutParams> {
                topMargin = insets.top
                bottomMargin = insets.bottom
                leftMargin = insets.left
                rightMargin = insets.right
            }

            WindowInsetsCompat.CONSUMED
        }

        return AlertDialog.Builder(requireContext(), R.style.AppTheme)
            .setView(binding.root)
            .create()
            .also { dialog ->
                if (VERSION.SDK_INT >= VERSION_CODES.VANILLA_ICE_CREAM) dialog.setOnShowListener {
                    WindowInsetsControllerCompat(dialog.window!!, binding.root).run {
                        isAppearanceLightStatusBars = true
                    }
                }
            }
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}