package dk.jens.backup;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class BackupRestoreHelper {
    final static String TAG = OAndBackup.TAG;

    public enum ActionType {
        BACKUP, RESTORE
    }

    private String getBackupFileFolderName() {
        @SuppressLint("SimpleDateFormat") DateFormat targetFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS");
        return String.format("%s-user_%s", targetFormat.format(new Date()), 0);
    }

    public int backup(Context context, File backupDir, AppInfo appInfo, ShellCommands shellCommands, int backupMode) {
        int ret = 0;
        File subdir = new File(backupDir, appInfo.getPackageName());
        File[] files = subdir.listFiles();

        File backupSubDir = new File(subdir, File.separator + getBackupFileFolderName());
        if (!backupSubDir.exists())
            backupSubDir.mkdirs();
        else if (backupMode != AppInfo.MODE_DATA && appInfo.getSourceDir().length() > 0) {
            if (appInfo.getLogInfo() != null && appInfo.getLogInfo().getSourceDir().length() > 0 && !appInfo.getSourceDir().equals(appInfo.getLogInfo().getSourceDir())) {
                String apk = appInfo.getLogInfo().getApk();
                if (apk != null) {
                    ShellCommands.deleteBackup(new File(backupSubDir, apk));
                    if (appInfo.getLogInfo().isEncrypted())
                        ShellCommands.deleteBackup(new File(backupSubDir, apk + ".gpg"));
                }
            }
        }

        if (appInfo.isSpecial()) {
            ret = shellCommands.backupSpecial(backupSubDir, appInfo.getLabel(), appInfo.getFilesList());
            appInfo.setBackupMode(AppInfo.MODE_DATA);
        } else {
            if (appInfo.isSystem()) {
                ret = shellCommands.doBackup(context, backupSubDir, appInfo, appInfo.getLabel(), appInfo.getDataDir(), "", backupMode);
                appInfo.setBackupMode(AppInfo.MODE_DATA);
            } else {
                ret = shellCommands.doBackup(context, backupSubDir, appInfo, appInfo.getLabel(), appInfo.getDataDir(), appInfo.getSourceDir(), backupMode);
                appInfo.setBackupMode(backupMode);
            }
        }

        shellCommands.logReturnMessage(context, ret);
        LogFile.writeLogFile(backupSubDir, appInfo, backupMode);
        // clean old backup
        if (files != null && files.length > 0) {
            for (File file : files) {
                if (file.isDirectory()) {
                    Utils.deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        return ret;
    }

    public int restore(Context context, File backupDir, AppInfo appInfo, ShellCommands shellCommands, int mode, Crypto crypto) {
        int apkRet, restoreRet, permRet, cryptoRet;
        apkRet = restoreRet = permRet = cryptoRet = 0;
        File subdir = new File(backupDir, appInfo.getPackageName());
        File[] files = subdir.listFiles(File::isDirectory);
        assert files != null;
        File backupSubDir = files[0];
        LogFile logInfo = new LogFile(subdir, files[0].getName());
        String apk = logInfo.getApk();
        String dataDir = appInfo.getDataDir();
        // extra check for needToDecrypt here because of BatchActivity which cannot really reset crypto to null for every package to restore
        if (crypto != null && Crypto.needToDecrypt(backupDir, appInfo, mode))
            crypto.decryptFromAppInfo(context, backupDir, appInfo, mode);
        if (mode == AppInfo.MODE_APK || mode == AppInfo.MODE_BOTH) {
            if (apk != null && apk.length() > 0) {
                if (appInfo.isSystem()) {
                    //apkRet = shellCommands.restoreSystemApk(backupSubDir,
                    //    appInfo.getLabel(), apk);
                } else {
                    if (appInfo.getSplitSourceDirs() != null && appInfo.getSplitSourceDirs().length > 0) {
                        apkRet = shellCommands.restoreUserSplitApk(context, appInfo, backupSubDir);
                    } else {
                        apkRet = shellCommands.restoreUserApk(backupSubDir,
                                appInfo.getLabel(), apk, context.getApplicationInfo().dataDir);
                    }
                }
                //if(appInfo.isSystem() && appInfo.getLogInfo() != null)
                //{
                //    File apkFile = new File(backupDir, appInfo.getPackageName() + "/" + appInfo.getLogInfo().getApk());
                //    shellCommands.copyNativeLibraries(apkFile, backupSubDir, appInfo.getPackageName());
                //}
            } else if (!appInfo.isSpecial()) {
                String s = "no apk to install: " + appInfo.getPackageName();
                Log.e(TAG, s);
                ShellCommands.writeErrorLog(appInfo.getPackageName(), s);
                apkRet = 1;
            }
        }
        if (mode == AppInfo.MODE_DATA || mode == AppInfo.MODE_BOTH) {
            if (apkRet == 0 && (appInfo.isInstalled() || mode == AppInfo.MODE_BOTH)) {
                if (appInfo.isSpecial()) {
                    restoreRet = shellCommands.restoreSpecial(backupSubDir, appInfo.getLabel(), appInfo.getFilesList());
                } else {
                    restoreRet = shellCommands.doRestore(context, backupSubDir, appInfo.getLabel(), appInfo.getPackageName());

                    permRet = shellCommands.setPermissions(dataDir);
                }
            } else {
                Log.e(TAG, "cannot restore data without restoring apk, package is not installed: " + appInfo.getPackageName());
                apkRet = 1;
                ShellCommands.writeErrorLog(appInfo.getPackageName(), context.getString(R.string.restoreDataWithoutApkError));
            }
        }
        if (crypto != null) {
            Crypto.cleanUpDecryption(appInfo, backupSubDir, mode);
            if (crypto.isErrorSet())
                cryptoRet = 1;
        }
        int ret = apkRet + restoreRet + permRet + cryptoRet;
        shellCommands.logReturnMessage(context, ret);
        return ret;
    }

    public interface OnBackupRestoreListener {
        void onBackupRestoreDone();
    }
}
