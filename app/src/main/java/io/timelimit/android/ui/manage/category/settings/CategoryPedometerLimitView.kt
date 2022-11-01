/*
 * TimeLimit Copyright <C> 2019 - 2020 Jonas Lochmann
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
package io.timelimit.android.ui.manage.category.settings

import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.google.android.material.snackbar.Snackbar
import io.timelimit.android.R
import io.timelimit.android.data.model.Category
import io.timelimit.android.databinding.CategoryPedometerLimitViewBinding
import io.timelimit.android.livedata.ignoreUnchanged
import io.timelimit.android.livedata.map
import io.timelimit.android.sync.actions.UpdateCategoryPedometerLimit
import io.timelimit.android.ui.help.HelpDialogFragment
import io.timelimit.android.ui.main.ActivityViewModel

object CategoryPedometerLimitView {
    fun bind(
            binding: CategoryPedometerLimitViewBinding,
            lifecycleOwner: LifecycleOwner,
            category: LiveData<Category?>,
            auth: ActivityViewModel,
            categoryId: String,
            fragmentManager: FragmentManager
    ) {
        binding.titleView.setOnClickListener {
            HelpDialogFragment.newInstance(
                    title = R.string.category_settings_battery_limit_title,
                    text = R.string.category_settings_battery_limit_description
            ).show(fragmentManager)
        }

        fun updateButtonVisibility() {
            val category = category.value
            val modified = category == null || (category.minPedometerSteps != binding.seekbarPedometerSteps.progress * 250) || (category.pedometerResetTime != binding.pedometerResetTime)

            binding.pedometerConfirmBtn.visibility = if (modified) View.VISIBLE else View.GONE
        }

        binding.seekbarPedometerSteps.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(p0: SeekBar?) = Unit
            override fun onStopTrackingTouch(p0: SeekBar?) = Unit

            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                binding.minPedometerSteps = p1 * 250

                updateButtonVisibility()
            }
        })

        fun updatePedometerStartTime(edittext: TextView): Boolean {
            val regex = Regex("^([0-9]{1,2}):([0-5][0-9])$")
            val matchResult = regex.find(edittext.text)
            if (matchResult != null) {
                if (matchResult.groups[1]!!.value.toLong() <= 23L && matchResult.groups[2]!!.value.toLong()<= 59L) {
                    binding.pedometerResetTime = matchResult.groups[1]!!.value.toLong() * 60 + matchResult.groups[2]!!.value.toLong()
                    updateButtonVisibility()
                    return true;
                }
            } else {
                edittext.text = "%d:%02d".format(binding.pedometerResetTime / 60, binding.pedometerResetTime % 60)
                updateButtonVisibility()
            }
            return false
        }
        
        binding.edittextPedometerStartTime.setOnEditorActionListener { edittext: TextView?, actionId: Int, event: KeyEvent? ->
            if (actionId == EditorInfo.IME_ACTION_DONE && edittext != null) {
                updatePedometerStartTime(edittext)
            }
            false
        }
        binding.edittextPedometerStartTime.setOnFocusChangeListener { view, hasFocus ->
            if (!hasFocus) {
                updatePedometerStartTime(view as TextView)
            }
            false
        }

        category.map {
            it?.run { it.minPedometerSteps / 250 }
        }.ignoreUnchanged().observe(lifecycleOwner, Observer {
            if (it != null) {
                binding.seekbarPedometerSteps.progress = it

                updateButtonVisibility()
            }
        })
        category.map {
            it?.run { it.pedometerResetTime }
        }.ignoreUnchanged().observe(lifecycleOwner, Observer {
            if (it != null) {
                binding.pedometerResetTime = it
                binding.edittextPedometerStartTime.setText("%d:%02d".format(binding.pedometerResetTime / 60, binding.pedometerResetTime % 60))
                updateButtonVisibility()
            }
        })

        binding.pedometerConfirmBtn.setOnClickListener {
            if (
                    auth.tryDispatchParentAction(
                            UpdateCategoryPedometerLimit(
                                    categoryId = categoryId,
                                    pedometerLimit = binding.seekbarPedometerSteps.progress * 250,
                                    pedometerResetTime = binding.pedometerResetTime
                            )
                    )
            ) {
                Snackbar.make(binding.root, R.string.category_settings_battery_limit_confirm_toast, Snackbar.LENGTH_SHORT).show()

                binding.pedometerConfirmBtn.visibility = View.GONE
            }
        }
    }
}


