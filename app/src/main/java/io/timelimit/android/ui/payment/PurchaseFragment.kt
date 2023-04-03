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
package io.timelimit.android.ui.payment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import io.timelimit.android.R
import io.timelimit.android.databinding.FragmentPurchaseBinding
import io.timelimit.android.livedata.liveDataFromNullableValue
import io.timelimit.android.livedata.mergeLiveData
import io.timelimit.android.ui.MainActivity
import io.timelimit.android.ui.diagnose.exception.DiagnoseExceptionDialogFragment
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.FragmentWithCustomTitle
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.util.Clipboard
import java.lang.RuntimeException

class PurchaseFragment : Fragment(), FragmentWithCustomTitle {
    private val activityModel: ActivityPurchaseModel by lazy { (activity as MainActivity).purchaseModel }
    private val model: PurchaseModel by viewModels()

    companion object {
        private const val PAGE_BUY = 0
        private const val PAGE_ERROR = 1
        private const val PAGE_WAIT = 2
        private const val PAGE_DONE = 3
        private const val PAGE_AUTH = 4
        private const val PAGE_TOKEN = 5
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            activityModel.resetProcessPurchaseSuccess()
        }

        model.init(activityModel, getActivityViewModel(requireActivity()))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentPurchaseBinding.inflate(inflater, container, false)
        var processingPurchaseError = false

        mergeLiveData(activityModel.status, model.status).observe(viewLifecycleOwner) { (activityStatus, fragmentStatus) ->
            if (fragmentStatus != null) {
                when (fragmentStatus) {
                    PurchaseModel.Status.Preparing -> binding.flipper.displayedChild = PAGE_WAIT
                    is PurchaseModel.Status.ReadyRegular -> {
                        when (activityStatus) {
                            null -> binding.flipper.displayedChild = PAGE_WAIT
                            ActivityPurchaseModelStatus.Working -> binding.flipper.displayedChild = PAGE_WAIT
                            ActivityPurchaseModelStatus.Idle -> {
                                binding.flipper.displayedChild = PAGE_BUY
                                binding.priceData = fragmentStatus
                            }
                            ActivityPurchaseModelStatus.Error -> {
                                binding.flipper.displayedChild = PAGE_ERROR

                                binding.errorReason = getString(R.string.error_general)
                                binding.showRetryButton = true
                                processingPurchaseError = true
                            }
                            ActivityPurchaseModelStatus.Done -> binding.flipper.displayedChild = PAGE_DONE
                        }.let {  }
                    }
                    is PurchaseModel.Status.Error -> {
                        binding.flipper.displayedChild = PAGE_ERROR

                        binding.errorReason = when (fragmentStatus) {
                            PurchaseModel.Status.Error.Unrecoverable.BillingNotSupportedByDevice -> getString(R.string.purchase_error_not_supported_by_device)
                            is PurchaseModel.Status.Error.Recoverable.NetworkError -> getString(R.string.error_network)
                            PurchaseModel.Status.Error.Unrecoverable.ExistingPaymentError -> getString(R.string.purchase_error_existing_payment)
                            PurchaseModel.Status.Error.Unrecoverable.ServerRejectedError -> getString(R.string.purchase_error_server_rejected)
                            PurchaseModel.Status.Error.Unrecoverable.ServerClientCombinationUnsupported -> getString(R.string.purchase_error_server_different_key)
                        }

                        binding.showRetryButton = when (fragmentStatus) {
                            is PurchaseModel.Status.Error.Recoverable -> true
                            is PurchaseModel.Status.Error.Unrecoverable -> false
                        }

                        binding.showErrorDetailsButton = when (fragmentStatus) {
                            is PurchaseModel.Status.Error.Recoverable.NetworkError -> true
                            else -> false
                        }

                        processingPurchaseError = false
                    }
                    PurchaseModel.Status.WaitingForAuth -> {
                        binding.flipper.displayedChild = PAGE_AUTH
                    }
                    is PurchaseModel.Status.ReadyToken -> {
                       binding.flipper.displayedChild = PAGE_TOKEN

                       binding.purchaseToken = fragmentStatus.token
                    }
                }.let { }
            } else {
                binding.flipper.displayedChild = PAGE_WAIT
            }
        }

        getActivityViewModel(requireActivity()).authenticatedUser.observe(viewLifecycleOwner) { user ->
            if (user != null && model.status.value is PurchaseModel.Status.WaitingForAuth) {
                model.retry()
            }
        }

        binding.handlers = object: PurchaseFragmentHandlers {
            override fun retryAtErrorScreenClicked() {
                if (processingPurchaseError) {
                    activityModel.queryAndProcessPurchasesAsync()
                } else {
                    model.retry()
                }
            }

            override fun showErrorDetails() {
                val status = model.status.value

                val exception = when (status) {
                    is PurchaseModel.Status.Error.Recoverable.NetworkError -> status.exception
                    else -> RuntimeException("other error")
                }

                DiagnoseExceptionDialogFragment.newInstance(exception).show(parentFragmentManager)
            }

            override fun buyForOneMonth() {
                activityModel.startPurchase(PurchaseIds.SKU_MONTH, checkAtBackend = true, activity = requireActivity())
            }

            override fun buyForOneYear() {
                activityModel.startPurchase(PurchaseIds.SKU_YEAR, checkAtBackend = true, activity = requireActivity())
            }

            override fun showAuthDialog() {
                (requireActivity() as ActivityViewModelHolder).showAuthenticationScreen()
            }

            override fun copyPurchaseTokenToClipboard() {
                model.status.value?.also {
                    if (it is PurchaseModel.Status.ReadyToken) {
                        Clipboard.setAndToast(requireContext(), it.token)
                    }
                }
            }
        }

        return binding.root
    }

    override fun getCustomTitle(): LiveData<String?> = liveDataFromNullableValue("${getString(R.string.about_full_version)} < ${getString(R.string.main_tab_overview)}")
}

interface PurchaseFragmentHandlers {
    fun retryAtErrorScreenClicked()
    fun showErrorDetails()
    fun buyForOneMonth()
    fun buyForOneYear()
    fun showAuthDialog()
    fun copyPurchaseTokenToClipboard()
}
