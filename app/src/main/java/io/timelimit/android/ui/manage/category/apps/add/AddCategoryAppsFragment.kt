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
package io.timelimit.android.ui.manage.category.apps.add

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.AppBarLayout
import io.timelimit.android.R
import io.timelimit.android.databinding.FragmentAddCategoryAppsBinding
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.sync.actions.AddCategoryAppsAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.manage.category.apps.AddAppsOrActivitiesModel
import io.timelimit.android.ui.manage.category.apps.addactivity.AddActivitiesParams
import io.timelimit.android.ui.manage.category.apps.addactivity.AddAppActivitiesDialogFragment
import io.timelimit.android.ui.view.AppFilterView

class AddCategoryAppsFragment : DialogFragment() {
    companion object {
        private const val DIALOG_TAG = "x"
        private const val STATUS_PACKAGE_NAMES = "d"
        private const val STATUS_EDUCATED = "e"
        private const val PARAMS = "params"

        fun newInstance(params: AddAppsParams) = AddCategoryAppsFragment().apply {
            arguments = Bundle().apply { putParcelable(PARAMS, params) }
        }
    }

    private val auth: ActivityViewModel by lazy { getActivityViewModel(requireActivity()) }
    private val adapter = AddAppAdapter()
    private var didEducateAboutAddingAssignedApps = false
    private val baseModel: AddAppsOrActivitiesModel by viewModels()
    private val model: AddAppsModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            adapter.selectedApps = savedInstanceState.getStringArrayList(STATUS_PACKAGE_NAMES)!!.toSet()
            didEducateAboutAddingAssignedApps = savedInstanceState.getBoolean(STATUS_EDUCATED)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putStringArrayList(STATUS_PACKAGE_NAMES, ArrayList(adapter.selectedApps))
        outState.putBoolean(STATUS_EDUCATED, didEducateAboutAddingAssignedApps)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = FragmentAddCategoryAppsBinding.inflate(LayoutInflater.from(context))
        val params = requireArguments().getParcelable<AddAppsParams>(PARAMS)!!

        baseModel.init(params)
        baseModel.isAuthValid(auth).observe(this) { if (!it) dismissAllowingStateLoss() }

        model.init(params)

        model.showAppsFromOtherDevicesChecked.value = binding.showAppsFromUnassignedDevices.isChecked
        model.showAppsFromOtherCategories.value = binding.showOtherCategoriesApps.isChecked
        model.assignToThisDeviceOnly.value = binding.assignToThisDeviceOnly.isChecked

        binding.showAppsFromUnassignedDevices.setOnCheckedChangeListener { _, isChecked ->
            model.showAppsFromOtherDevicesChecked.value = isChecked
        }

        binding.assignToThisDeviceOnly.setOnCheckedChangeListener { _, isChecked ->
            model.assignToThisDeviceOnly.value = isChecked
        }

        AppFilterView.getFilterLive(binding.filter).observe(this) { model.filter.value = it }

        model.isLocalMode.observe(this) {
            binding.showAppsFromUnassignedDevices.visibility = if (it) View.GONE else View.VISIBLE
        }

        model.showDeviceSpecificAssignmentOption.observe(this) {
            binding.assignToThisDeviceOnly.visibility = if (it) View.VISIBLE else View.GONE
        }

        binding.showOtherCategoriesApps.setOnCheckedChangeListener { _, isChecked ->
            model.showAppsFromOtherCategories.value = isChecked
        }

        binding.recycler.layoutManager = LinearLayoutManager(context)
        binding.recycler.adapter = adapter

        model.listItems.observe(this, Observer {
            val selectedPackageNames = adapter.selectedApps
            val visiblePackageNames = it.map { it.packageName }.toSet()
            val hiddenSelectedPackageNames = selectedPackageNames.toMutableSet().apply { removeAll(visiblePackageNames) }.size

            adapter.data = it
            binding.hiddenEntries = if (hiddenSelectedPackageNames == 0)
                null
            else
                resources.getQuantityString(R.plurals.category_apps_add_dialog_hidden_entries, hiddenSelectedPackageNames, hiddenSelectedPackageNames)
        })

        model.emptyViewText.observe(this) {
            binding.emptyText = when (it!!) {
                AddAppsModel.EmptyViewText.None -> null
                AddAppsModel.EmptyViewText.EmptyDueToFilter -> getString(R.string.category_apps_add_empty_due_to_filter)
                AddAppsModel.EmptyViewText.EmptyDueToChildMode -> getString(R.string.category_apps_add_empty_due_to_child_mode)
                AddAppsModel.EmptyViewText.EmptyLocalMode -> getString(R.string.category_apps_add_empty_local_mode)
                AddAppsModel.EmptyViewText.EmptyAllDevicesNoAppsNoChildDevices -> getString(R.string.category_apps_add_empty_all_devices_no_apps_no_childs)
                AddAppsModel.EmptyViewText.EmptyAllDevicesNoAppsButChildDevices -> getString(R.string.category_apps_add_empty_all_devices_no_apps_but_child_devices)
                AddAppsModel.EmptyViewText.EmptyChildDevicesHaveNoApps -> getString(R.string.category_apps_add_empty_child_devices_no_apps)
                AddAppsModel.EmptyViewText.EmptyNoChildDevices -> getString(R.string.category_apps_add_empty_no_child_devices)
            }
        }

        binding.someOptionsDisabledDueToChildAuthentication = params.isSelfLimitAddingMode

        model.deviceIdLive.observe(this) {/* keep loaded */}

        binding.addAppsButton.setOnClickListener {
            val packageNames = adapter.selectedApps.toList()

            if (packageNames.isNotEmpty()) {
                val deviceSpecific = binding.assignToThisDeviceOnly.isChecked && !params.isSelfLimitAddingMode
                val deviceId = model.deviceIdLive.value

                if (deviceSpecific && deviceId == null) return@setOnClickListener

                auth.tryDispatchParentAction(
                        action = AddCategoryAppsAction(
                                categoryId = params.categoryId,
                                packageNames = if (deviceSpecific) packageNames.map { "$it@$deviceId" } else packageNames
                        ),
                        allowAsChild = params.isSelfLimitAddingMode
                )
            }

            dismiss()
        }

        binding.cancelButton.setOnClickListener { dismiss() }

        binding.selectAllButton.setOnClickListener {
            adapter.selectedApps = adapter.selectedApps + (adapter.data?.map { it.packageName }?.toSet() ?: emptySet())
        }

        adapter.listener = object: AddAppAdapterListener {
            override fun onAppClicked(app: AddAppListItem) {
                if (adapter.selectedApps.contains(app.packageName)) {
                    adapter.selectedApps = adapter.selectedApps - setOf(app.packageName)
                } else {
                    if (!didEducateAboutAddingAssignedApps) {
                        if (app.currentCategoryName != null) {
                            didEducateAboutAddingAssignedApps = true

                            AddAlreadyAssignedAppsInfoDialog().show(fragmentManager!!)
                        }
                    }

                    adapter.selectedApps = adapter.selectedApps + setOf(app.packageName)
                }
            }

            override fun onAppLongClicked(app: AddAppListItem): Boolean {
                return if (adapter.selectedApps.isEmpty()) {
                    AddAppActivitiesDialogFragment.newInstance(AddActivitiesParams(
                        base = params,
                        packageName = app.packageName
                    )).show(parentFragmentManager)

                    dismissAllowingStateLoss()

                    true
                } else {
                    Toast.makeText(context, R.string.category_apps_add_dialog_cannot_add_activities_already_sth_selected, Toast.LENGTH_LONG).show()

                    false
                }
            }
        }

        // uses the idea from https://stackoverflow.com/a/57854900
        binding.emptyView.layoutParams = CoordinatorLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            behavior = object: CoordinatorLayout.Behavior<TextView>() {
                override fun layoutDependsOn(parent: CoordinatorLayout, child: TextView, dependency: View) = dependency is AppBarLayout

                override fun onDependentViewChanged(parent: CoordinatorLayout, child: TextView, dependency: View): Boolean {
                    dependency as AppBarLayout

                    (child.layoutParams as CoordinatorLayout.LayoutParams).topMargin = (dependency.height + dependency.y).toInt()
                    child.requestLayout()

                    return true
                }
            }
        }

        return AlertDialog.Builder(requireContext(), R.style.AppTheme)
                .setView(binding.root)
                .create()
    }

    fun show(manager: FragmentManager) {
        showSafe(manager, DIALOG_TAG)
    }
}
