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
package com.machiav3lli.backup.items

import android.app.usage.StorageStats
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.machiav3lli.backup.BACKUP_DATE_TIME_FORMATTER
import com.machiav3lli.backup.BACKUP_DATE_TIME_FORMATTER_OLD
import com.machiav3lli.backup.BACKUP_INSTANCE_PROPERTIES
import com.machiav3lli.backup.OABX
import com.machiav3lli.backup.dbs.entity.AppInfo
import com.machiav3lli.backup.dbs.entity.Backup
import com.machiav3lli.backup.dbs.entity.SpecialInfo
import com.machiav3lli.backup.handler.LogsHandler
import com.machiav3lli.backup.handler.getPackageStorageStats
import com.machiav3lli.backup.preferences.pref_cachePackages
import com.machiav3lli.backup.traceBackups
import com.machiav3lli.backup.utils.FileUtils
import com.machiav3lli.backup.utils.StorageLocationNotConfiguredException
import com.machiav3lli.backup.utils.getBackupDir
import timber.log.Timber
import java.io.File

// TODO need to handle some emergent props with empty backupList constructors
class Package {
    var packageName: String
    var packageInfo: com.machiav3lli.backup.dbs.entity.PackageInfo
    private var packageBackupDir: StorageFile? = null
    var storageStats: StorageStats? = null
    var lastUpdateTime: Long = 0

    private var backupListDirty = true
    private var backupListState = mutableStateOf(listOf<Backup>())
    private var backupList by backupListState

    internal constructor(   // toPackageList
        context: Context,
        appInfo: AppInfo,
        backups: List<Backup>? = emptyList()
    ) {
        packageName = appInfo.packageName
        this.packageInfo = appInfo
        getAppBackupRoot()
        if (appInfo.installed) refreshStorageStats(context)
        updateBackupList(backups ?: emptyList())    //TODO hg42 ???
        OABX.app.packageCache.put(packageName, this)
    }

    constructor(            // special packages
        specialInfo: SpecialInfo,
        backups: List<Backup>? = emptyList()
    ) {
        packageName = specialInfo.packageName
        this.packageInfo = specialInfo
        getAppBackupRoot()
        updateBackupList(backups ?: emptyList())    //TODO hg42 ???
        OABX.app.packageCache.put(packageName, this)
    }

    constructor(            // schedule
        context: Context,
        packageInfo: android.content.pm.PackageInfo
    ) {
        packageName = packageInfo.packageName
        this.packageInfo = AppInfo(context, packageInfo)
        getAppBackupRoot()
        refreshStorageStats(context)
        //updateBackupList(backups ?: emptyList())    //TODO hg42 ???
        //refreshBackupList()                         // currently in schedule, otherwise here
        OABX.app.packageCache.put(packageName, this)
    }

    constructor(
        context: Context,
        packageName: String,
        backupDir: StorageFile?,
    ) {
        this.packageBackupDir = backupDir
        this.packageName = packageName
        //refreshBackupList()                             //TODO hg42 use ensureBackupList() ?
        //ensureBackupList()
        try {
            val pi = context.packageManager.getPackageInfo(
                this.packageName,
                PackageManager.GET_PERMISSIONS
            )
            this.packageInfo = AppInfo(context, pi)
            refreshStorageStats(context)
        } catch (e: PackageManager.NameNotFoundException) {
            try {
                this.packageInfo = SpecialInfo.getSpecialPackages(context)
                    .find { it.packageName == this.packageName }!!
                    .packageInfo
            } catch (e: Throwable) {
                //TODO hg42 Timber.i("$packageName is not installed")
                this.packageInfo = latestBackup?.toAppInfo() ?: run {
                    throw AssertionError(
                        "Backup History is empty and package is not installed. The package is completely unknown?",     //TODO hg42 remove package from database???
                        e
                    )
                }
            }
        }
        OABX.app.packageCache.put(packageName, this)
    }

    constructor(
        context: Context,
        packageInfo: android.content.pm.PackageInfo,
        backupRoot: StorageFile?,
        backups: List<Backup>? = emptyList()
    ) {
        this.packageName = packageInfo.packageName
        this.packageInfo = AppInfo(context, packageInfo)
        this.packageBackupDir = backupRoot?.findFile(packageName)
        refreshStorageStats(context)
        updateBackupList(backups ?: emptyList())
        OABX.app.packageCache.put(packageName, this)
    }

    fun refreshStorageStats(context: Context): Boolean {
        return try {
            storageStats = context.getPackageStorageStats(packageName)
            if (lastUpdateTime == 0L) lastUpdateTime = context.packageManager.getPackageInfo(packageName,0).lastUpdateTime
            true
        } catch (e: PackageManager.NameNotFoundException) {
            LogsHandler.logException(e, "Could not refresh StorageStats. Package was not found")
            false
        }
    }

    fun refreshFromPackageManager(context: Context): Boolean {
        Timber.d("Trying to refresh package information for $packageName from PackageManager")
        try {
            val pi =
                context.packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            packageInfo = AppInfo(context, pi)
            refreshStorageStats(context)
        } catch (e: PackageManager.NameNotFoundException) {
            LogsHandler.logException(e, "$packageName is not installed. Refresh failed")
            return false
        }
        return true
    }

    fun updateBackupList(new: List<Backup>) {
        backupList = new
        backupListDirty = false
    }

    fun updateBackupListAndDatabase(new: List<Backup>) {
        updateBackupList(new)
        OABX.main?.viewModel?.backupsUpdate?.tryEmit(this)
        //val pkg = this
        //CoroutineScope(Dispatchers.Unconfined).launch {
        //    ODatabase.getInstance(OABX.context).backupDao.updateList(pkg)
        //}
    }

    fun refreshBackupList() {
        traceBackups { "refreshbackupList: $packageName" }
        invalidateBackupCacheForPackage(packageName)
        val backups = mutableListOf<Backup>()
        try {
            getAppBackupRoot()?.listFiles()
                ?.filter(StorageFile::isPropertyFile)
                ?.forEach { propFile ->
                    try {
                        Backup.createFrom(propFile)?.let {
                            backups.add(it)
                        }
                    } catch (e: Backup.BrokenBackupException) {
                        val message =
                            "Incomplete backup or wrong structure found in $propFile"
                        Timber.w(message)
                    } catch (e: NullPointerException) {
                        val message =
                            "(Null) Incomplete backup or wrong structure found in $propFile"
                        Timber.w(message)
                    } catch (e: Throwable) {
                        val message =
                            "(catchall) Incomplete backup or wrong structure found in $propFile"
                        LogsHandler.unhandledException(e, message)
                    }
                }
        } catch (e: Throwable) {
            // ignore
        }
        updateBackupListAndDatabase(backups)  //TODO hg42 ???
        //updateBackupList(backups)
    }

    private fun ensureBackupsLoaded(): List<Backup> {
        if (backupListDirty)
            refreshBackupList()
        return backupList
    }

    fun ensureBackupList() {
        ensureBackupsLoaded()
    }

    private fun needBackupList(): List<Backup> {
        return backupList
    }

    @Throws(
        FileUtils.BackupLocationInAccessibleException::class,
        StorageLocationNotConfiguredException::class
    )
    fun getAppBackupRoot(
        create: Boolean = false,
        packageName: String = this.packageName
    ): StorageFile? {
        return try {
            when {
                create -> {
                    packageBackupDir = OABX.context.getBackupDir().ensureDirectory(packageName)
                    packageBackupDir
                }
                packageBackupDir != null && packageBackupDir?.exists() == true -> {
                    packageBackupDir
                }
                else -> {
                    packageBackupDir = OABX.context.getBackupDir().findFile(packageName)
                    packageBackupDir
                }
            }
        } catch (e: Throwable) {
            LogsHandler.unhandledException(e)
            null
        }
    }

    fun addBackup(backup: Backup) {
        traceBackups { "add backup ${backup.packageName} ${backup.backupDate}" }
        //updateBackupListAndDatabase(backupList + backup)  //TODO hg42
        refreshBackupList()
    }

    fun deleteBackup(backup: Backup) {
        traceBackups { "delete backup ${backup.packageName} ${backup.backupDate}" }
        if (backup.packageName != packageName) {
            throw RuntimeException("Asked to delete a backup of ${backup.packageName} but this object is for $packageName")
        }
        Timber.d("[$packageName] Deleting backup revision $backup")
        val propertiesFileName = String.format(
            BACKUP_INSTANCE_PROPERTIES,
            BACKUP_DATE_TIME_FORMATTER.format(backup.backupDate),
            backup.profileId
        )
        val propertiesFileNameOld = String.format(
            BACKUP_INSTANCE_PROPERTIES,
            BACKUP_DATE_TIME_FORMATTER_OLD.format(backup.backupDate),
            backup.profileId
        )
        try {
            backup.getBackupInstanceFolder(packageBackupDir)?.deleteRecursive()
        } catch (e: Throwable) {
            LogsHandler.unhandledException(e, backup.packageName)
        }
        try {
            packageBackupDir?.findFile(propertiesFileName)?.delete() ?: packageBackupDir?.findFile(
                propertiesFileNameOld
            )?.delete()
        } catch (e: Throwable) {
            LogsHandler.unhandledException(e, backup.packageName)
        }
        try {
            //updateBackupListAndDatabase(backupList - backup)  //TODO hg42
            refreshBackupList()
            if (backupList.size == 0) {
                packageBackupDir?.deleteRecursive()
                packageBackupDir = null
            }
        } catch (e: Throwable) {
            LogsHandler.unhandledException(e, backup.packageName)
        }
    }

    fun rewriteBackupProps(newBackup: Backup) {
        traceBackups { "rewrite backup ${newBackup.packageName} ${newBackup.backupDate}" }
        if (newBackup.packageName != packageName) {
            throw RuntimeException("Asked to delete a backup of ${newBackup.packageName} but this object is for $packageName")
        }
        Timber.d("[$packageName] Deleting backup revision $newBackup")
        val propertiesFileName = String.format(
            BACKUP_INSTANCE_PROPERTIES,
            BACKUP_DATE_TIME_FORMATTER.format(newBackup.backupDate),
            newBackup.profileId
        )
        val propertiesFileNameOld = String.format(
            BACKUP_INSTANCE_PROPERTIES,
            BACKUP_DATE_TIME_FORMATTER_OLD.format(newBackup.backupDate),
            newBackup.profileId
        )

        try {
            packageBackupDir?.findFile(propertiesFileName)?.apply {
                overwriteText(newBackup.toJSON())
            } ?: packageBackupDir?.findFile(propertiesFileNameOld)?.apply {
                overwriteText(newBackup.toJSON())
            }
        } catch (e: Throwable) {
            LogsHandler.unhandledException(e, newBackup.packageName)
        }
        try {
            updateBackupListAndDatabase(backupList.filterNot { it.backupDate == newBackup.backupDate } + newBackup) //TODO hg42 ???
        } catch (e: Throwable) {
            LogsHandler.unhandledException(e, newBackup.packageName)
        }
    }

    fun deleteAllBackups() {
        while (backupList.isNotEmpty())
            deleteBackup(backupList.first())
    }

    fun deleteOldestBackups(keep: Int) {
        while (keep < backupList.size && !backupsWithoutNewest.all { it.persistent }) {
            backupsWithoutNewest.forEach { backup ->
                if (!backup.persistent && keep < backupList.size) {
                    Timber.i("[${backup.packageName}] Deleting backup revision ${backup.backupDate}")
                    deleteBackup(backup)
                }
            }
        }
    }

    val backupsNewestFirst: List<Backup>
        get() = needBackupList().sortedByDescending { item -> item.backupDate }

    val backupsWithoutNewest: List<Backup>
        get() = needBackupList().sortedBy { item -> item.backupDate }.dropLast(1)

    val latestBackup: Backup?
        get() = needBackupList().maxByOrNull { it.backupDate }

    val oldestBackup: Backup?
        get() = needBackupList().minByOrNull { it.backupDate }

    val numberOfBackups: Int get() = needBackupList().size

    val isApp: Boolean
        get() = packageInfo is AppInfo && !packageInfo.isSpecial

    val isInstalled: Boolean
        get() = (isApp && (packageInfo as AppInfo).installed) || packageInfo.isSpecial

    val isDisabled: Boolean
        get() = isInstalled && !isSpecial && !(packageInfo is AppInfo && (packageInfo as AppInfo).enabled)

    val isSystem: Boolean
        get() = packageInfo.isSystem || packageInfo.isSpecial

    val isSpecial: Boolean
        get() = packageInfo.isSpecial

    val specialFiles: Array<String>
        get() = (packageInfo as SpecialInfo).specialFiles

    val packageLabel: String
        get() = packageInfo.packageLabel ?: packageName

    val versionCode: Int
        get() = packageInfo.versionCode

    val versionName: String?
        get() = packageInfo.versionName

    val hasBackups: Boolean
        get() = backupList.isNotEmpty()

    val apkPath: String
        get() = if (isApp) (packageInfo as AppInfo).apkDir ?: "" else ""

    val dataPath: String
        get() = if (isApp) (packageInfo as AppInfo).dataDir ?: "" else ""

    val devicesProtectedDataPath: String
        get() = if (isApp) (packageInfo as AppInfo).deDataDir ?: "" else ""

    // - [] 1.Try?
    // Uses the context to get own external data directory
    // e.g. /storage/emulated/0/Android/data/com.machiav3lli.backup/files
    // Goes to the parent two times to the leave own directory
    // e.g. /storage/emulated/0/Android/data
    fun getExternalDataPath(context: Context): String {
        // Uses the context to get own external data directory
        // e.g. /storage/emulated/0/Android/data/com.machiav3lli.backup/files
        // Goes to the parent two times to the leave own directory
        // e.g. /storage/emulated/0/Android/data
        // Add the package name to the path assuming that if the name of dataDir does not equal the
        // package name and has a prefix or a suffix to use it.
        return context.getExternalFilesDir(null)?.parentFile?.parentFile?.absolutePath?.plus("${File.separator}$packageName")
            ?: ""
    }

    // Uses the context to get own obb data directory
    // e.g. /storage/emulated/0/Android/obb/com.machiav3lli.backup
    // Goes to the parent to the leave the app-specific directory
    // e.g. /storage/emulated/0/Android/obb
    fun getObbFilesPath(context: Context): String {
        // Uses the context to get own obb data directory
        // e.g. /storage/emulated/0/Android/obb/com.machiav3lli.backup
        // Goes to the parent two times to the leave own directory
        // e.g. /storage/emulated/0/Android/obb
        // Add the package name to the path assuming that if the name of dataDir does not equal the
        // package name and has a prefix or a suffix to use it.
        return context.obbDir.parentFile?.absolutePath?.plus("${File.separator}$packageName") ?: ""
    }

    // Uses the context to get own media directory
    // e.g. /storage/emulated/0/Android/obb/com.machiav3lli.backup
    // Goes to the parent two times to the leave obb directory
    // e.g. /storage/emulated/0/Android
    // Access the child folder named "media"
    // e.g. /storage/emulated/0/Android/media
    fun getMediaFilesPath(context: Context): String {
        // Uses the context to get own obb data directory
        // e.g. /storage/emulated/0/Android/media/com.machiav3lli.backup
        // Goes to the parent two times to the leave own directory
        // e.g. /storage/emulated/0/Android/media
        // Add the package name to the path assuming that if the name of dataDir does not equal the
        // package name and has a prefix or a suffix to use it.
        return context.obbDir.parentFile?.parentFile?.absolutePath?.plus("${File.separator}media${File.separator}$packageName")
            ?: ""
    }

    /**
     * Returns the list of additional apks (excluding the main apk), if the app is installed
     *
     * @return array of with absolute filepaths pointing to one or more split apks or null if
     * the app is not splitted
     */
    val apkSplits: Array<String>
        get() = packageInfo.splitSourceDirs

    val isUpdated: Boolean
        get() = latestBackup?.let { it.versionCode < versionCode } ?: false

    val isNewOrUpdated: Boolean
        get() = latestBackup?.let { it.versionCode < versionCode } ?: !isSystem

    val hasApk: Boolean
        get() = backupList.any { it.hasApk }

    val hasData: Boolean
        get() = backupList.any {
            it.hasAppData || it.hasExternalData || it.hasDevicesProtectedData ||
                    it.hasObbData || it.hasMediaData
        }

    val hasAppData: Boolean
        get() = backupList.any { it.hasAppData }

    val hasExternalData: Boolean
        get() = backupList.any { it.hasExternalData }

    val hasDevicesProtectedData: Boolean
        get() = backupList.any { it.hasDevicesProtectedData }

    val hasObbData: Boolean
        get() = backupList.any { it.hasObbData }

    val hasMediaData: Boolean
        get() = backupList.any { it.hasMediaData }

    val appBytes: Long
        get() = if (packageInfo.isSpecial) 0 else storageStats?.appBytes ?: 0

    val dataBytes: Long
        get() = if (packageInfo.isSpecial) 0 else storageStats?.dataBytes ?: 0

    val backupBytes: Long
        get() = latestBackup?.size ?: 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val pkg = other as Package
        return packageName == pkg.packageName
                && this.packageInfo == pkg.packageInfo
                && packageBackupDir == pkg.packageBackupDir
                && storageStats == pkg.storageStats
                && backupList == pkg.backupList
    }

    override fun hashCode(): Int {
        var hash = 7
        hash = 31 * hash + packageName.hashCode()
        hash = 31 * hash + packageInfo.hashCode()
        hash = 31 * hash + packageBackupDir.hashCode()
        hash = 31 * hash + storageStats.hashCode()
        hash = 31 * hash + backupList.hashCode()
        return hash
    }

    override fun toString(): String {
        return "Schedule{" +
                "packageName=" + packageName +
                ", appInfo=" + packageInfo +
                ", appUri=" + packageBackupDir +
                ", storageStats=" + storageStats +
                ", backupList=" + backupList +
                '}'
    }

    companion object {
        fun get(packageName: String, creator: () -> Package): Package {
            if (pref_cachePackages.value)
                return OABX.app.packageCache[packageName] ?: creator()
            return creator()
        }

        fun invalidateAllPackages() {
            StorageFile.invalidateCache()
            OABX.app.packageCache.clear()
        }

        fun invalidateCacheForPackage(packageName: String) {
            StorageFile.invalidateCache { it.contains(packageName) }
            OABX.app.packageCache.remove(packageName)
        }

        fun invalidateBackupCacheForPackage(packageName: String) {
            StorageFile.invalidateCache { it.contains(packageName) }
            OABX.app.packageCache.remove(packageName)
        }

        fun invalidateSystemCacheForPackage(packageName: String) {
            StorageFile.invalidateCache { it.contains(packageName) }
            OABX.app.packageCache.remove(packageName)
        }
    }
}
