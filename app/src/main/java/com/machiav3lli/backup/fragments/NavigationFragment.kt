/*
 * OAndBackupX: open-source apps backup and restore app.
 * Copyright (C) 2020  Antonios Hazim
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.machiav3lli.backup.fragments

import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.machiav3lli.backup.OABX
import com.machiav3lli.backup.R
import com.machiav3lli.backup.activities.MainActivityX
import com.machiav3lli.backup.handler.LogsHandler
import com.machiav3lli.backup.handler.WorkHandler
import com.machiav3lli.backup.items.Package
import com.machiav3lli.backup.tasks.AppActionWork
import com.machiav3lli.backup.tasks.FinishWork

fun startBatchAction(
    backupBoolean: Boolean,
    selectedPackages: List<String>,
    selectedModes: List<Int>,
    onSuccessfulFinish: Observer<WorkInfo>.(LiveData<WorkInfo>) -> Unit
) {
    val now = System.currentTimeMillis()
    val notificationId = now.toInt()
    val batchType = OABX.context.resources.getString(if (backupBoolean) R.string.backup else R.string.restore)
    val batchName = WorkHandler.getBatchName(batchType, now)

    val selectedItems = selectedPackages
        .mapIndexed { i, packageName ->
            if (packageName.isEmpty())
                null
            else
                Pair(packageName, selectedModes[i])
        }
        .filterNotNull()

    var errors = ""
    var resultsSuccess = true
    var counter = 0
    val worksList: MutableList<OneTimeWorkRequest> = mutableListOf()
    OABX.work.beginBatch(batchName)
    selectedItems.forEach { (packageName, mode) ->

        val oneTimeWorkRequest =
            AppActionWork.Request(packageName, mode, backupBoolean, notificationId, batchName, true)
        worksList.add(oneTimeWorkRequest)

        val oneTimeWorkLiveData = WorkManager.getInstance(OABX.context)
            .getWorkInfoByIdLiveData(oneTimeWorkRequest.id)
        oneTimeWorkLiveData.observeForever(object : Observer<WorkInfo> {
            override fun onChanged(t: WorkInfo?) {
                if (t?.state == WorkInfo.State.SUCCEEDED) {
                    counter += 1

                    val (succeeded, packageLabel, error) = AppActionWork.getOutput(t)
                    if (error.isNotEmpty()) errors = "$errors$packageLabel: ${
                        LogsHandler.handleErrorMessages(
                            OABX.context,
                            error
                        )
                    }\n"

                    resultsSuccess = resultsSuccess and succeeded
                    oneTimeWorkLiveData.removeObserver(this)
                }
            }
        })
    }

    val finishWorkRequest = FinishWork.Request(resultsSuccess, backupBoolean, batchName)

    val finishWorkLiveData = WorkManager.getInstance(OABX.context)
        .getWorkInfoByIdLiveData(finishWorkRequest.id)
    finishWorkLiveData.observeForever(object : Observer<WorkInfo> {
        override fun onChanged(t: WorkInfo?) {
            if (t?.state == WorkInfo.State.SUCCEEDED) {
                onSuccessfulFinish(finishWorkLiveData)
            }
        }
    })

    if (worksList.isNotEmpty()) {
        WorkManager.getInstance(OABX.context)
            .beginWith(worksList)
            .then(finishWorkRequest)
            .enqueue()
    }
}

abstract class NavigationFragment : Fragment(), ProgressViewController {
    protected var sheetSortFilter: SortFilterSheet? = null
    val packageList: MediatorLiveData<MutableList<Package>>
        get() = requireMainActivity().viewModel.packageList

    override fun onResume() {
        super.onResume()
        requireMainActivity().setProgressViewController(this)
    }

    fun startBatchAction(
        backupBoolean: Boolean,
        selectedPackages: List<String?>,
        selectedModes: List<Int>,
        onSuccessfulFinish: Observer<WorkInfo>.(LiveData<WorkInfo>) -> Unit
    ) {
        val now = System.currentTimeMillis()
        val notificationId = now.toInt()
        val batchType = getString(if (backupBoolean) R.string.backup else R.string.restore)
        val batchName = WorkHandler.getBatchName(batchType, now)

        val selectedItems = selectedPackages
            .mapIndexed { i, packageName ->
                if (packageName.isNullOrEmpty()) null
                else Pair(packageName, selectedModes[i])
            }
            .filterNotNull()

        var errors = ""
        var resultsSuccess = true
        var counter = 0
        val worksList: MutableList<OneTimeWorkRequest> = mutableListOf()
        OABX.work.beginBatch(batchName)
        selectedItems.forEach { (packageName, mode) ->

            val oneTimeWorkRequest =
                AppActionWork.Request(
                    packageName,
                    mode,
                    backupBoolean,
                    notificationId,
                    batchName,
                    true
                )
            worksList.add(oneTimeWorkRequest)

            val oneTimeWorkLiveData = WorkManager.getInstance(requireContext())
                .getWorkInfoByIdLiveData(oneTimeWorkRequest.id)
            oneTimeWorkLiveData.observeForever(object : Observer<WorkInfo> {
                override fun onChanged(t: WorkInfo?) {
                    if (t?.state == WorkInfo.State.SUCCEEDED) {
                        counter += 1

                        val (succeeded, packageLabel, error) = AppActionWork.getOutput(t)
                        if (error.isNotEmpty()) errors = "$errors$packageLabel: ${
                            LogsHandler.handleErrorMessages(
                                requireContext(),
                                error
                            )
                        }\n"

                        resultsSuccess = resultsSuccess and succeeded
                        oneTimeWorkLiveData.removeObserver(this)
                    }
                }
            })
        }

        val finishWorkRequest = FinishWork.Request(resultsSuccess, backupBoolean, batchName)

        val finishWorkLiveData = WorkManager.getInstance(requireContext())
            .getWorkInfoByIdLiveData(finishWorkRequest.id)
        finishWorkLiveData.observeForever(object : Observer<WorkInfo> {
            override fun onChanged(t: WorkInfo?) {
                if (t?.state == WorkInfo.State.SUCCEEDED) {
                    onSuccessfulFinish(finishWorkLiveData)
                }
            }
        })

        if (worksList.isNotEmpty()) {
            WorkManager.getInstance(requireContext())
                .beginWith(worksList)
                .then(finishWorkRequest)
                .enqueue()
        }
    }

    fun requireMainActivity(): MainActivityX = super.requireActivity() as MainActivityX
}

// TODO ease relation to refreshing field in the VMs
interface RefreshViewController {
    fun refreshView(list: MutableList<Package>?)
}

interface ProgressViewController {
    fun updateProgress(progress: Int, max: Int)
    fun hideProgress()
}
