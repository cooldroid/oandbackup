package dk.jens.backup;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.topjohnwu.superuser.Shell;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ShellCommands implements CommandHandler.UnexpectedExceptionListener
{
    final static String TAG = OAndBackup.TAG;
    final static String EXTERNAL_FILES = "external_files";
    final static String EXPANSION_FILES = "obb_files";
    public static boolean IS_SUPER_USER = false;
	
    CommandHandler commandHandler = new CommandHandler();

    private final String oabUtils;
    private String busybox;
    private boolean legacyMode;

    private final String[] excludeFolders = new String[] {"lib", "cache", "app_*",
            "no_backup", "code_cache", "files/.Fabric"};
    private final String[] externalExcludeFolders = new String[] {"cache", "files/.vungle"};

    SharedPreferences prefs;
    ArrayList<String> users;
    private static String errors = "";
    boolean multiuserEnabled;
    private static final Pattern gidPattern = Pattern.compile("Gid:\\s*\\(\\s*(\\d+)");
    private static final Pattern uidPattern = Pattern.compile("Uid:\\s*\\(\\s*(\\d+)");
    private static final String extAndroidData = Environment.
            getExternalStoragePublicDirectory("Android/data").toString();
    public ShellCommands(SharedPreferences prefs, ArrayList<String> users,
        File filesDir)
    {
        this.users = users;
        this.prefs = prefs;
        busybox = prefs.getString(Constants.PREFS_PATH_BUSYBOX, "").trim();
        if(busybox.length() == 0) {
            this.busybox = new File(filesDir, AssetsHandler.BUSYBOX).getAbsolutePath();
            if (!checkBusybox()) {
                String[] boxPaths = new String[]{"/data/adb/magisk/busybox",
                        "/system/xbin/busybox"};
                for (String box : boxPaths) {
                    if (checkBusyboxWithRoot(box)) {
                        this.busybox = box;
                        break;
                    }
                    // fallback:
                    this.busybox = Build.VERSION.SDK_INT >= 23 ? "toybox" : AssetsHandler.BUSYBOX;
                }
            }
        }
        this.users = getUsers();
        multiuserEnabled = this.users != null && this.users.size() > 1;
        this.oabUtils = new File(filesDir, AssetsHandler.OAB_UTILS).getAbsolutePath();
        checkOabUtils();
    }

    public ShellCommands(SharedPreferences prefs, File filesDir)
    {
        this(prefs, null, filesDir);
        // initialize with userlist as null. getUsers checks if list is null and simply returns it if isn't and if its size is greater than 0.
        this.busybox = prefs.getString(Constants.PREFS_PATH_BUSYBOX, "").trim();
        checkBusybox();
    }

    @Override
    public void onUnexpectedException(Throwable t) {
        Log.e(TAG, "unexpected exception caught", t);
        writeErrorLog("", t.toString());
        errors += t.toString();
    }

    @SuppressLint("NewApi")
    public int doBackup(Context context, File backupSubDir, String label, String packageData, String packageApk, int backupMode)
    {
        String backupSubDirPath = swapBackupDirPath(backupSubDir.getAbsolutePath());
        Log.i(TAG, "backup: " + label);
        // since api 24 (android 7) ApplicationInfo.dataDir can be null
        // this doesn't seem to be documented. proper sanity checking is needed
        if(packageData == null){
            writeErrorLog(label,
                "packageData is null. this is unexpected, please report it.");
            return 1;
        }
        List<String> commands = new ArrayList<>();
        // -L because fat (which will often be used to store the backup files)
        // doesn't support symlinks
        String followSymlinks = prefs.getBoolean("followSymlinks", true) ? "h" : "";

        File fPackageData = new File(packageData);
        String folder = fPackageData.getName();
        String folderPath = Objects.requireNonNull(fPackageData.getParentFile()).getPath();

        boolean hasSplits = false;
        ApplicationInfo applicationInfo = null;
        try {
            applicationInfo = context.getPackageManager().getApplicationInfo(folder,0);
            hasSplits = (applicationInfo.splitPublicSourceDirs != null && applicationInfo.splitPublicSourceDirs.length > 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        String backupAPKCommand = null;
        if (!hasSplits) {
            //File apkFile = new File(packageApk);
            //String backupAPKCommand = packageApk.isEmpty() ? "" : "cp " + packageApk + " " + backupSubDirPath;
            backupAPKCommand = packageApk.isEmpty() ? "" :
                    //busybox + " tar -czf " + backupSubDirPath + "/" + folder + ".apk.gz " + "-C " + apkFile.getParent() + " " + apkFile.getName();
                    //busybox + " gzip -1 -ck " + apkFile.getAbsolutePath() + " > " + backupSubDirPath + "/" + folder + ".apk.gz ";
                    "cp " + packageApk + " " + backupSubDirPath + "/";
        }
        StringBuilder excludes = new StringBuilder();
        for (String exclFolder: excludeFolders) {
            excludes.append(" --exclude='").append(exclFolder).append("'");
        }
        String backupDataCommand = busybox + " tar -cz" + followSymlinks + "f " +
                backupSubDirPath + "/" + "data.tar.gz " +
                "-C " + folderPath + "/" + folder + excludes + " .";
        switch(backupMode)
        {
            case AppInfo.MODE_APK:
                if (hasSplits) {
                    executeWithPacking(context, applicationInfo, backupSubDirPath + "/" + "base.apks");
                } else {
                    commands.add(backupAPKCommand);
                }
                break;
            case AppInfo.MODE_DATA:
                commands.add(backupDataCommand);
                break;
            default: // defaults to MODE_BOTH
                commands.add(backupDataCommand);
                if (hasSplits) {
                    executeWithPacking(context, applicationInfo, backupSubDirPath + "/" + folder + ".apks");
                } else {
                    commands.add(backupAPKCommand);
                }
                break;
        }
        File externalFilesDir = getExternalFilesDirPath(context, packageData);
        boolean backupExternalFiles = prefs.getBoolean("backupExternalFiles", false);
        if(backupExternalFiles && backupMode != AppInfo.MODE_APK && externalFilesDir != null) {
            if (folderSize(externalFilesDir) > 0) {
                StringBuilder externalExcludes = new StringBuilder();
                for (String exclFolder: externalExcludeFolders) {
                    externalExcludes.append(" --exclude='").append(folder).append("/").append(exclFolder).append("'");
                }
                String externalFolderPath = swapBackupDirPath(Objects.requireNonNull(externalFilesDir.getParentFile()).getAbsolutePath());
                commands.add(busybox + " tar -cz" + followSymlinks + "f " +
                        swapBackupDirPath(backupSubDir.getAbsolutePath() + "/" + EXTERNAL_FILES + ".tar.gz") +
                        " -C " + externalFolderPath + externalExcludes + " " + folder);
            }
        } else if(!backupExternalFiles && backupMode != AppInfo.MODE_APK) {
            deleteBackup(new File(backupSubDir, EXTERNAL_FILES + ".tar.gz"));
        }

        File expansionDir = getExpansionDirectoryPath(context, packageData);
        boolean backupExpansionFiles = prefs.getBoolean("backupExpansionFiles", false);

        if (backupExpansionFiles && backupMode != AppInfo.MODE_APK && expansionDir != null) {
            //commands.add(busybox + " cp -R " + swapBackupDirPath(expansionDir.getAbsolutePath()) +
            //        " " + swapBackupDirPath(backupSubDir.getAbsolutePath() + "/" + EXPANSION_FILES));
            commands.add(busybox + " tar -czf " + backupSubDir.getAbsolutePath() + "/" +
                EXPANSION_FILES + ".tar.gz" + " " + expansionDir.getAbsolutePath());
        } else if (!backupExpansionFiles && backupMode != AppInfo.MODE_APK) {
            deleteBackup(new File(backupSubDir, EXPANSION_FILES + ".tar.gz"));
        }
        List<String> errors = new ArrayList<>();
        int ret = commandHandler.runCmd("su", commands, line -> {},
            errors::add, e -> {
                Log.e(TAG, String.format("Exception caught running: %s",
                    TextUtils.join(", ", commands)), e);
                writeErrorLog(label, e.toString());
            }, this);
        if(errors.size() == 1 ) {
            String line = errors.get(0);
            // ignore error if it is about /lib while followSymlinks
            // is false or if it is about /lock in the data of firefox
            if((!prefs.getBoolean("followSymlinks", true) &&
                    (line.contains("lib") && ((line.contains("not permitted")
                    && line.contains("symlink"))) || line.contains("No such file or directory")))
                    || (line.contains("mozilla") && line.contains("/lock"))
                    || (line.equals("no commands to run")))
                ret = 0;
        } else {
            for (String line : errors)
                writeErrorLog(label, line);
        }
        if(backupSubDirPath.startsWith(context.getApplicationInfo().dataDir))
        {
            /**
            * if backupDir is set to oab's own datadir (/data/data/dk.jens.backup)
            * we need to ensure that the permissions are correct before trying to
            * zip. on the external storage, gid will be sdcard_r (or something similar)
            * without any changes but in the app's own datadir files will have both uid
            * and gid as 0 / root when they are first copied with su.
            */
            ret = ret + setPermissions(backupSubDirPath);
        }
        //deleteBackup(new File(backupSubDir, folder + "/lib"));
        if(label.equals(TAG))
        {
            copySelfAPk(backupSubDir, packageApk); // copy apk of app to parent directory for visibility
        }
        /*
        // only zip if data is backed up
        if(backupMode != AppInfo.MODE_APK)
        {
            int zipret = compress(new File(backupSubDir, folder));
            if(backupSubDirExternalFiles != null)
                zipret += compress(new File(backupSubDirExternalFiles, packageData.substring(packageData.lastIndexOf("/") + 1)));
            if (backupSubDirExpansionFiles != null)
                zipret += compress(new File(backupSubDirExpansionFiles, packageData.substring(packageData.lastIndexOf("/") + 1)));
            if (zipret != 0)
                ret += zipret;
        }
        */
        // delete old encrypted files if encryption is not enabled
        if (!prefs.getBoolean(Constants.PREFS_ENABLECRYPTO, false))
            Crypto.cleanUpEncryptedFiles(backupSubDir, packageApk, packageData, backupMode, prefs.getBoolean("backupExternalFiles", false), prefs.getBoolean("backupExpansionFiles", false));
        return ret;
    }

    @SuppressLint("NewApi")
    private void executeWithPacking(Context context, ApplicationInfo applicationInfo, String destination) {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(destination))) {
            List<File> apkFiles = new ArrayList<>();
            apkFiles.add(new File(applicationInfo.publicSourceDir));
            if (applicationInfo.splitPublicSourceDirs != null) {
                for (String splitPath : applicationInfo.splitPublicSourceDirs)
                    apkFiles.add(new File(splitPath));
            }
            //APKs
            for (File apkFile : apkFiles) {
                zipOutputStream.setMethod(ZipOutputStream.STORED);

                ZipEntry zipEntry = new ZipEntry(apkFile.getName());
                zipEntry.setMethod(ZipEntry.STORED);
                zipEntry.setCompressedSize(apkFile.length());
                zipEntry.setSize(apkFile.length());
                zipEntry.setCrc(ShellCommands.calculateFileCrc32(apkFile));

                zipOutputStream.putNextEntry(zipEntry);

                try (FileInputStream apkInputStream = new FileInputStream(apkFile)) {
                    byte[] buffer = new byte[1024 * 512];
                    int read;

                    while ((read = apkInputStream.read(buffer)) > 0) {
                        zipOutputStream.write(buffer, 0, read);
                    }
                }
                zipOutputStream.closeEntry();
            }
        } catch (IOException e) {
            writeErrorLog(applicationInfo.name, e.toString());
        }
    }

    public static long calculateFileCrc32(File file) throws IOException {
        return calculateCrc32(new FileInputStream(file));
    }

    public static long calculateCrc32(InputStream inputStream) throws IOException {
        try (InputStream in = inputStream) {
            CRC32 crc32 = new CRC32();
            byte[] buffer = new byte[1024 * 1024];
            int read;

            while ((read = in.read(buffer)) > 0)
                crc32.update(buffer, 0, read);

            return crc32.getValue();
        }
    }

    private long folderSize(File directory) {
        long length = 0;
        for (File file : Objects.requireNonNull(directory.listFiles())) {
            if (length > 0) break;
            if (file.isFile())
                length += file.length();
            else {
                if (!file.getName().matches("(?i)cache|.vungle")) {
                    length += folderSize(file);
                }
            }
        }
        return length;
    }

    public int doRestore(Context context, File backupSubDir, String label, String packageName, String dataDir)
    {
        File fileDataDir = new File(dataDir);
        String dataDirName = fileDataDir.getName();
        Log.i(TAG, "restoring: " + label);

        File zipFile = new File(backupSubDir, "data.tar.gz");
        if (zipFile.exists()) {
            List<String> commands = new ArrayList<>();

            killPackage(context, packageName);
            if(prefs.getBoolean("backupExternalFiles", false))
            {
                File externalFiles = new File(backupSubDir, EXTERNAL_FILES + ".tar.gz");
                if (externalFiles.exists()) {
                    //String externalFilesPath = context.getExternalFilesDir(null).getParentFile().getParentFile().getAbsolutePath();
                    commands.add(busybox + " tar -C " + extAndroidData + "/" + dataDirName + " -xzf " + externalFiles.getAbsolutePath());
                }
            }

            if(prefs.getBoolean("backupExpansionFiles", false))
            {
                File expansionFiles = new File(backupSubDir, EXPANSION_FILES + ".tar.gz");
                File expansionFilesPath = new File(context.getObbDir().getParentFile(), packageName);
                if (expansionFilesPath.exists() ||  expansionFilesPath.mkdir())
                {
                    //commands.add(busybox + " cp -R " + expansionFiles + "/* " + expansionFilesPath);
                    commands.add(busybox + " tar -xzf " + expansionFiles + " " + expansionFilesPath);
                }
            }

            String restoreCommand = busybox + " tar -C " + fileDataDir.getAbsolutePath() + " -xzf " +
                    zipFile.getAbsolutePath();
            if (!(fileDataDir.exists()))
			{
                commands.add("mkdir " + dataDir);
                // restored system apps will not necessarily have the data folder (which is otherwise handled by pm)
            }
            commands.add(restoreCommand);
            if (Build.VERSION.SDK_INT >= 23)
                commands.add("restorecon -R " + dataDir);
            //if (multiuserEnabled)
			//{
            //    disablePackage(packageName);
            //}
            return commandHandler.runCmd("su", commands, line -> {},
                    line -> writeErrorLog(label, line),
                    e -> Log.e(TAG, "doRestore: " + e.toString()), this);
        }
		else
		{
            Log.i(TAG, packageName + " has empty or non-existent backup: " + backupSubDir.getAbsolutePath() + "/" + dataDirName);
            return 0;
        }
    }
    public int backupSpecial(File backupSubDir, String label, String... files)
    {
        // backup method only used for the special appinfos which can have lists of single files
        String backupSubDirPath = swapBackupDirPath(backupSubDir.getAbsolutePath());
        Log.i(TAG, "backup: " + label);
        List<String> commands = new ArrayList<>();
        if(files != null)
            for(String file : files)
                commands.add("cp -r " + file + " " + backupSubDirPath);
        int ret = commandHandler.runCmd("su", commands, line -> {},
            line -> writeErrorLog(label, line),
            e -> Log.e(TAG, "backupSpecial: " + e.toString()), this);
        if(files != null)
        {
            for(String file : files)
            {
                File f = new File(backupSubDir, Utils.getName(file));
                if(f.isDirectory())
                {
                    int zipret = compress(f);
                    if(zipret != 0 && zipret != 2)
                        ret += zipret;
                }
            }
        }
        return ret;
    }
    public int restoreSpecial(File backupSubDir, String label, String... files)
    {
        String backupSubDirPath = swapBackupDirPath(backupSubDir.getAbsolutePath());
        int unzipRet = 0;
        ArrayList<String> toDelete = new ArrayList<String>();

        Log.i(TAG, "restoring: " + label);
        try
        {
            List<String> commands = new ArrayList<>();
            if(files != null)
            {
                for(String file : files)
                {
                    Ownership ownership = getOwnership(file);
                    String filename = Utils.getName(file);
                    if(file.endsWith(File.separator))
                        file = file.substring(0, file.length() - 1);
                    String dest = file;
                    if(new File(file).isDirectory())
                    {
                        dest = file.substring(0, file.lastIndexOf("/"));
                        File zipFile = new File(backupSubDir, filename + ".zip");
                        if(zipFile.exists())
                        {
                            int ret = Compression.unzip(zipFile, backupSubDir);
                            // delay the deletion of the unzipped directory until the copying has been done
                            if(ret == 0)
                            {
                                toDelete.add(filename);
                            }
                            else
                            {
                                unzipRet += ret;
                                writeErrorLog(label, "error unzipping " + file);
                                continue;
                            }
                        }
                    }
                    else
                    {
                        ownership = getOwnership(file, "su");
                    }
                    commands.add("cp -r " + backupSubDirPath + "/" + filename + " " + dest);
                    commands.add(String.format("%s -R %s %s", busybox,
                        ownership.toString(), file));
                    commands.add(busybox + " chmod -R 0771 " + file);
                }
            }
            int ret = commandHandler.runCmd("su", commands, line -> {},
                line -> writeErrorLog(label, line),
                e -> Log.e(TAG, "restoreSpecial: " + e.toString()), this);
            return ret + unzipRet;
        }
        catch(IndexOutOfBoundsException | OwnershipException e)
        {
            Log.e(TAG, "restoreSpecial: " + e);
        }
        finally
        {
            for(String filename : toDelete)
                deleteBackup(new File(backupSubDir, filename));
        }
        return 1;
    }
    private static ArrayList<String> getIdsFromStat(String stat)
    {
        Matcher uid = uidPattern.matcher(stat);
        Matcher gid = gidPattern.matcher(stat);
        if(!uid.find() || !gid.find())
            return null;
        ArrayList<String> res = new ArrayList<String>();
        res.add(uid.group(1));
        res.add(gid.group(1));
        return res;
    }
    public Ownership getOwnership(String packageDir) throws OwnershipException
    {
        return getOwnership(packageDir, "su");
    }
    public Ownership getOwnership(String packageDir, String shellPrivs)
        throws OwnershipException
    {
        if(!legacyMode) {
            List<String> result = new ArrayList<>();
            commandHandler.runCmd(shellPrivs, String.format("%s owner %s", oabUtils, packageDir),
                result::add, line -> writeErrorLog("oab-utils", line),
                e -> Log.e(TAG, "getOwnership: " + e.toString()), this);
            if(result.size() != 1) {
                if(result.size() < 1) {
                    throw new OwnershipException(
                            "got empty result from oab-utils");
                }
                StringBuilder sb = new StringBuilder();
                for(String line : result) {
                    sb.append(line).append("\n");
                }
                throw new OwnershipException(String.format(
                        "unexpected ownership result from oab-utils: %s",
                        sb));
            }
            try {
                JSONObject ownershipJson = new JSONObject(result.get(0));
                return new Ownership(ownershipJson.getInt("uid"),
                        ownershipJson.getInt("gid"));
            } catch (JSONException e) {
                throw new OwnershipException(String.format(
                        "error parsing ownership json: %s", e));
            }
        } else {
            List<String> commands = new ArrayList<>();
            /*
             * some packages can have 0 / UNKNOWN as uid and gid for a short
             * time before being switched to their proper ids so to work
             * around the race condition we sleep a little.
             */
            commands.add("sleep 1");
            commands.add(busybox + " stat " + packageDir);
            StringBuilder sb = new StringBuilder();
            // you don't need su for stat - you do for ls -l /data/
            // and for stat on single files
            int ret = commandHandler.runCmd(shellPrivs, commands, sb::append,
                line -> writeErrorLog("", line),
                e -> Log.e(TAG, "getOwnership: " + e.toString()), this);
            Log.i(TAG, "getOwnership return: " + ret);
            ArrayList<String> uid_gid = getIdsFromStat(sb.toString());
            if(uid_gid == null || uid_gid.isEmpty()) {
                throw new OwnershipException(
                        "no uid or gid found while trying to set permissions");
            }
            return new Ownership(uid_gid.get(0), uid_gid.get(1));
        }
    }
    public int setPermissions(String packageDir)
    {
        try
        {
            Ownership ownership = getOwnership(packageDir);
            List<String> commands = new ArrayList<>();
            if(Build.VERSION.SDK_INT < 23) {
                commands.add("for dir in " + packageDir + "/*; do if " +
                    "[[ \"`" + busybox + " basename $dir`\" != @(lib|cache) ]]; then " +
                    busybox + " chown -R " + ownership.toString() + " $dir; " +
                    "fi; done");
            } else {
                if(!legacyMode) {
                    commands.add(String.format("%s change-owner -r %s %s",
                        oabUtils, ownership.toString(), packageDir));
                    //commands.add(String.format("%s set-permissions -r 771 %s", oabUtils,
                    //    packageDir));
                } else {
                    // android 6 has moved to toybox which doesn't include [ or [[
                    // meanwhile its implementation of test seems to be broken at least in cm 13
                    // cf. https://github.com/jensstein/oandbackup/issues/116
                    commands.add(String.format("%s chown -R %s %s",
                        busybox, ownership.toString(), packageDir));
                    //commands.add(String.format("%s chmod -R 771 %s",
                    //    busybox, packageDir));
                }
            }
            // midlertidig indtil mere detaljeret som i fix_permissions l.367
            int ret = commandHandler.runCmd("su", commands, line -> {},
                line -> writeErrorLog(packageDir, line),
                e -> Log.e(TAG, "error while setPermissions: " + e.toString()), this);
            Log.i(TAG, "setPermissions return: " + ret);
            return ret;
        }
        catch(IndexOutOfBoundsException | OwnershipException e)
        {
            Log.e(TAG, "error while setPermissions: " + e);
            writeErrorLog("", "setPermissions error: could not find permissions for " + packageDir);
        }
        return 1;
    }
    public int restoreUserApk(File backupDir, String label, String apk, String ownDataDir)
    {
        /* according to a comment in the android 8 source code for
         * /frameworks/base/cmds/pm/src/com/android/commands/pm/Pm.java
         * pm install is now discouraged / deprecated in favor of cmd
         * package install.
         */
        final String installCmd = Build.VERSION.SDK_INT >= 28 ?
            "cmd package install --user current" : "pm install --user current";
        // swapBackupDirPath is not needed with pm install
        List<String> commands = new ArrayList<>();
        final File packageStagingDirectory = new File("/data/local/tmp");
        if(backupDir.getAbsolutePath().startsWith(ownDataDir))
        {
            /**
            * pm cannot install from a file on the data partition
            * Failure [INSTALL_FAILED_INVALID_URI] is reported
            * therefore, if the backup directory is oab's own data
            * directory a temporary directory on the external storage
            * is created where the apk is then copied to.
            */
            String tempPath = Environment.getExternalStorageDirectory() + "/apkTmp" + System.currentTimeMillis();
            commands.add(busybox + " mkdir " + swapBackupDirPath(tempPath));
            commands.add(busybox + " cp " + swapBackupDirPath(
                backupDir.getAbsolutePath() + "/" + apk) + " " +
                swapBackupDirPath(tempPath));
            commands.add(String.format("%s -r %s/%s", installCmd, tempPath, apk));
            commands.add(busybox + " rm -r " + swapBackupDirPath(tempPath));
        } else {
            String apkDestPath = packageStagingDirectory + "/base.apk";
            //commands.add(busybox + " gzip -dc " + backupDir.getAbsolutePath() + "/" + apk + " > " + apkDestPath);
            commands.add("cat " + backupDir.getAbsolutePath() + "/" + apk + " > " + apkDestPath);
            commands.add(String.format("%s -r %s", installCmd, apkDestPath));
            commands.add(String.format("%s rm -r %s", busybox, apkDestPath));
        }
        List<String> err = new ArrayList<>();
        int ret = commandHandler.runCmd("su", commands, line -> {},
            err::add, e -> Log.e(TAG, "restoreUserApk: ", e), this);
        // pm install returns 0 even for errors and prints part of its normal output to stderr
        // on api level 10 successful output spans three lines while it spans one line on the other api levels
        int limit = 1;
        if(err.size() > limit)
        {
            for(String line : err)
            {
                writeErrorLog(label, line);
            }
            return 1;
        }
        else
        {
            return ret;
        }
    }

    int restoreUserSplitApk(Context context, AppInfo appInfo, File backupDir) {
        List<String> err = new ArrayList<>();

        final String pmPrefix = Build.VERSION.SDK_INT >= 28 ? "cmd package ": "pm ";
        String installCreateCmd = pmPrefix + "install-create --user current -r -i " + makeLiteral();
        final int[] sessionId = new int[1];
        commandHandler.runCmd("su", installCreateCmd, line -> {
                    if (line != null) { sessionId[0] = extractSessionId(line); }
                },
                err::add, e -> Log.e(TAG, "restoreUserSplitApk: ", e), this);

        try (ZipFile zipFile = new ZipFile(backupDir.getAbsolutePath() + "/base.apks")) {
            Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
            int currentApkFile = 0;
            while (zipEntries.hasMoreElements()) {
                ZipEntry nextEntry = zipEntries.nextElement();
                int retCode = Shell.cmd(pmPrefix + "install-write -S " + nextEntry.getSize() + " "
                        + sessionId[0] + " "  + String.format(Locale.getDefault(), "%d.apk", currentApkFile++) + " - ")
                        .add(zipFile.getInputStream(nextEntry)).exec().getCode();
            }
        } catch (IOException ioex) {
            ShellCommands.writeErrorLog(appInfo.getPackageName(), ioex.getMessage());
        }

        String installCommitCmd = pmPrefix + "install-commit " +  sessionId[0];
        int ret = commandHandler.runCmd("su", installCommitCmd, line -> {},
                err::add, e -> Log.e(TAG, "restoreUserSplitApk: ", e), this);
        int limit = 1;
        if(err.size() > limit)
        {
            for(String line : err)
            {
                writeErrorLog(appInfo.getLabel(), line);
            }
            return 1;
        }
        else
        {
            return ret;
        }
    }

    private Integer extractSessionId(String commandResult) {
        try {
            Pattern sessionIdPattern = Pattern.compile("(\\d+)");
            Matcher sessionIdMatcher = sessionIdPattern.matcher(commandResult);
            sessionIdMatcher.find();
            return Integer.parseInt(Objects.requireNonNull(sessionIdMatcher.group(1)));
        } catch (Exception e) {
            Log.w(TAG, commandResult, e);
            return null;
        }
    }

    private String makeLiteral() {
        return "'" + BuildConfig.APPLICATION_ID.replace("'", "'\\''") + "'";
    }

    public int restoreSystemApk(File backupDir, String label, String apk) {
        List<String> commands = new ArrayList<>();
        commands.add("mount -o remount,rw /system");
        // remounting with busybox mount seems to make android 4.4 fail the following commands without error

        // locations of apks have been changed in android 5
        String basePath = "/system/app/";
        basePath += apk.substring(0, apk.lastIndexOf(".")) + "/";
        commands.add("mkdir -p " + basePath);
        commands.add(busybox + " chmod 755 " + basePath);
        // for some reason a permissions error is thrown if the apk path is not created first (W/zipro   ( 4433): Unable to open zip '/system/app/Term.apk': Permission denied)
        // with touch, a reboot is not necessary after restoring system apps
        // maybe use MediaScannerConnection.scanFile like CommandHelper from CyanogenMod FileManager
        commands.add(busybox + " touch " + basePath + apk);
        commands.add(busybox + " cp " + swapBackupDirPath(
            backupDir.getAbsolutePath()) + "/" + apk + " " + basePath);
        commands.add(busybox + " chmod 644 " + basePath + apk);
        commands.add("mount -o remount,ro /system");
        return commandHandler.runCmd("su", commands, line -> {},
            line -> writeErrorLog(label, line),
            e -> Log.e(TAG, "restoreSystemApk: ", e), this);
    }

    public int compress(File directoryToCompress)
    {
        int zipret = Compression.zip(directoryToCompress);
        if(zipret == 0)
        {
            deleteBackup(directoryToCompress);
        }
        else if(zipret == 2)
        {
            // handling empty zip
            deleteBackup(new File(directoryToCompress.getAbsolutePath() + ".zip"));
            return 0;
            // zipret == 2 shouldn't be treated as an error
        }
        return zipret;
    }
    public int uninstall(String packageName, String sourceDir, String dataDir, boolean isSystem)
    {
        List<String> commands = new ArrayList<>();
        if(!isSystem)
        {
            commands.add("pm uninstall " + packageName);
            //commands.add(busybox + " rm -r /data/lib/" + packageName + "/*");
            // pm uninstall sletter ikke altid mapper og lib-filer ordentligt.
            // indføre tjek på pm uninstalls return
        }
        else
        {
            // it seems that busybox mount sometimes fails silently so use toolbox instead
            /*
            commands.add("mount -o remount,rw /system");
            commands.add(busybox + " rm " + sourceDir);
            if(Build.VERSION.SDK_INT >= 21)
            {
                String apkSubDir = Utils.getName(sourceDir);
                apkSubDir = apkSubDir.substring(0, apkSubDir.lastIndexOf("."));
                commands.add("rm -r /system/app/" + apkSubDir);
            }
            commands.add("mount -o remount,ro /system");
            commands.add(busybox + " rm -r " + dataDir);
            commands.add(busybox + " rm -r /data/app-lib/" + packageName + "*");
            */
        }
        List<String> err = new ArrayList<>();
        int ret = commandHandler.runCmd("su", commands, line -> {},
            err::add, e -> Log.e(TAG, "uninstall", e), this);
        if(ret != 0)
        {
            for(String line : err)
            {
                if(line.contains("No such file or directory") && err.size() == 1)
                {
                    // ignore errors if it is only that the directory doesn't exist for rm to remove
                    ret = 0;
                }
                else
                {
                    writeErrorLog(packageName, line);
                }
            }
        }
        Log.i(TAG, "uninstall return: " + ret);
        return ret;
    }
    public int quickReboot()
    {
        List<String> commands = new ArrayList<>();
        commands.add(busybox + " pkill system_server");
//            dos.writeBytes("restart\n"); // restart doesn't seem to work here even though it works fine from ssh
        return commandHandler.runCmd("su", commands, line -> {},
            line -> writeErrorLog("", line),
            e -> Log.e(TAG, "quickReboot: ", e), this);
    }
    public static void deleteBackup(File file)
    {
        if(file.exists())
        {
            if(file.isDirectory())
                if(Objects.requireNonNull(file.list()).length > 0 && file.listFiles() != null)
                    for(File child : Objects.requireNonNull(file.listFiles()))
                        deleteBackup(child);
            file.delete();
        }
    }
    public void deleteOldApk(File backupfolder, String newApkPath)
    {
        final String apk = new File(newApkPath).getName();
        File[] files = backupfolder.listFiles((dir, filename) -> (!filename.equals(apk) && filename.endsWith(".apk")));
        if(files != null)
        {
            for(File file : files)
            {
                file.delete();
            }
        }
        else
        {
            Log.e(TAG, "deleteOldApk: listFiles returned null");
        }
    }
    public void killPackage(Context context, String packageName)
    {
        List<ActivityManager.RunningAppProcessInfo> runningList;
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        runningList = manager.getRunningAppProcesses();
        for(ActivityManager.RunningAppProcessInfo process : runningList)
        {
            if(process.processName.equals(packageName) && process.pid != android.os.Process.myPid())
            {
                List<String> commands = new ArrayList<>();
                commands.add("kill " + process.pid);
                commandHandler.runCmd("su", commands, line -> {},
                    line -> writeErrorLog(packageName, line),
                    e -> Log.e(TAG, "killPackage: ", e), this);
            }
        }
    }

    public void logReturnMessage(Context context, int returnCode)
    {
        String returnMessage = returnCode == 0 ? context.getString(R.string.shellReturnSuccess) : context.getString(R.string.shellReturnError);
        Log.i(TAG, "return: " + returnCode + " / " + returnMessage);
    }
    public static void writeErrorLog(String packageName, String err)
    {
        errors += packageName + ": " + err + "\n";
        Date date = new Date();
        @SuppressLint("SimpleDateFormat") SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd - HH:mm:ss");
        String dateFormatted = dateFormat.format(date);
        try
        {
            File outFile = new FileCreationHelper().createLogFile(FileCreationHelper.getDefaultLogFilePath());
            if(outFile != null)
            {
                try(FileWriter fw = new FileWriter(outFile.getAbsoluteFile(),
                        true);
                        BufferedWriter bw = new BufferedWriter(fw)) {
                    bw.write(dateFormatted + ": " + err + " [" + packageName + "]\n");
                }
            }
        }
        catch(IOException e)
        {
            Log.e(TAG, e.toString());
        }
    }
    public static String getErrors()
    {
        return errors;
    }
    public static void clearErrors()
    {
        errors = "";
    }
    public static boolean checkSuperUser()
    {
        if (IS_SUPER_USER || Shell.rootAccess()) {
            IS_SUPER_USER = true;
        }
        return IS_SUPER_USER;
    }

    public boolean checkBusyboxWithRoot(String busyboxPath)
    {
        int ret = commandHandler.runCmd("sh", busyboxPath,
            line -> {}, line -> writeErrorLog("busybox", line),
            e -> Log.e(TAG, "checkBusybox: ", e), this);
        return ret == 0;
    }

    public boolean checkOabUtils() {
        return checkAssets(this.oabUtils);
    }

    private boolean checkBusybox() {
        return checkAssets(this.busybox);
    }

    private boolean checkAssets(String asset) {
        int ret = commandHandler.runCmd("sh", "[ -f " + asset + " ]",
                line -> {}, line -> writeErrorLog(asset, line),
                e -> Log.e(TAG, "checkAssets: ", e), this);
        return ret == 0;
    }
    public void copyNativeLibraries(File apk, File outputDir, String packageName)
    {
        /*
         * first try the primary abi and then the secondary if the
         * first doesn't give any results.
         * see frameworks/base/core/jni/com_android_internal_content_NativeLibraryHelper.cpp:iterateOverNativeFiles
         * frameworks/base/core/java/com/android/internal/content/NativeLibraryHelper.java
         * in the android source
         */
        String libPrefix = "lib/";
        ArrayList<String> libs = Compression.list(apk, libPrefix + Build.SUPPORTED_ABIS[0]);
        if(libs == null || libs.size() == 0)
            libs = Compression.list(apk, libPrefix + Build.SUPPORTED_ABIS[0]);
        if(libs != null && libs.size() > 0)
        {
            if(Compression.unzip(apk, outputDir, libs) == 0)
            {
                List<String> commands = new ArrayList<>();
                commands.add("mount -o remount,rw /system");
                String src = swapBackupDirPath(outputDir.getAbsolutePath());
                for(String lib : libs)
                {
                    commands.add("cp " + src + "/" + lib + " /system/lib");
                    commands.add("chmod 644 /system/lib/" + Utils.getName(lib));
                }
                commands.add("mount -o remount,ro /system");
                commandHandler.runCmd("su", commands, line -> {},
                    line -> writeErrorLog(packageName, line),
                    e -> Log.e(TAG, "copyNativeLibraries: ", e), this);
            }
            deleteBackup(new File(outputDir, "lib"));
        }
    }

    public ArrayList<String> getUsers()
    {
        if(users != null && users.size() > 0)
        {
            return users;
        }
        else
        {
//            int currentUser = getCurrentUser();
            List<String> commands = new ArrayList<>();
            commands.add("pm list users | " + busybox + " sed -nr 's/.*\\{([0-9]+):.*/\\1/p'");
            ArrayList<String> users = new ArrayList<>();
            int ret = commandHandler.runCmd("su", commands, line -> {
                if(line.trim().length() != 0)
                    users.add(line.trim());
                }, line -> writeErrorLog("", line),
                e -> Log.e(TAG, "getUsers: ", e), this);
            return ret == 0 ? users : null;
        }
    }
    public static int getCurrentUser()
    {
        try
        {
            // using reflection to get id of calling user since method getCallingUserId of UserHandle is hidden
            // https://github.com/android/platform_frameworks_base/blob/master/core/java/android/os/UserHandle.java#L123
            Class<?> userHandle = Class.forName("android.os.UserHandle");
            boolean muEnabled = userHandle.getField("MU_ENABLED").getBoolean(null);
            int range = userHandle.getField("PER_USER_RANGE").getInt(null);
            if(muEnabled)
                return android.os.Binder.getCallingUid() / range;
        }
        catch(ClassNotFoundException | NoSuchFieldException | IllegalAccessException ignored){}
        return 0;
    }
    public static ArrayList<String> getDisabledPackages(CommandHandler commandHandler)
    {
        List<String> commands = new ArrayList<>();
        commands.add("pm list packages -d");
        ArrayList<String> packages = new ArrayList<>();
        int ret = commandHandler.runCmd("sh", commands, line -> {
            if(line.contains(":"))
                packages.add(line.substring(line.indexOf(":") + 1).trim());
        }, line -> {}, e -> Log.e(TAG, "getDisabledPackages: ", e), e -> {});
        if(ret == 0 && packages.size() > 0)
            return packages;
        return null;
    }
    public void enableDisablePackage(String packageName, ArrayList<String> users, boolean enable)
    {
        String option = enable ? "enable" : "disable";
        if(users != null && users.size() > 0)
        {
            for(String user : users)
            {
                List<String> commands = new ArrayList<>();
                commands.add("pm " + option + " --user " + user + " " + packageName);
                commandHandler.runCmd("su", commands, line -> {},
                    line -> writeErrorLog(packageName, line),
                    e -> Log.e(TAG, "enableDisablePackage: ", e), this);
            }
        }
    }
    public void disablePackage(String packageName)
    {
        StringBuilder userString = new StringBuilder();
        int currentUser = getCurrentUser();
        for(String user : users)
        {
            userString.append(" ").append(user);
        }
        List<String> commands = new ArrayList<>();
        // reflection could probably be used to find packages available to a given user: PackageManager.queryIntentActivitiesAsUser
        // http://androidxref.com/4.2_r1/xref/frameworks/base/core/java/android/content/pm/PackageManager.java#1880

        // editing package-restrictions.xml directly seems to require a reboot
        // sub=`grep $packageName package-restrictions.xml`
        // sed -i 's|$sub|"<pkg name=\"$packageName\" inst=\"false\" />"' package-restrictions.xml

        // disabling via pm has the unfortunate side-effect that packages can only be re-enabled via pm
        String disable = "pm disable --user $user " + packageName;
        // if packagename is in package-restriction.xml the app is probably not installed by $user
        String grep = busybox + " grep " + packageName + " /data/system/users/$user/package-restrictions.xml";
        // though it could be listed as enabled
        String enabled = grep + " | " + busybox + " grep enabled=\"1\"";
        // why doesn't ! enabled work
        commands.add("for user in " + userString + "; do if [ $user != " +
            currentUser + " ] && " + grep + " && " + enabled + "; then " +
            disable + "; fi; done");
        commandHandler.runCmd("su", commands, line -> {},
            line -> writeErrorLog(packageName, line),
            e -> Log.e(TAG, "disablePackage: ", e), this);
    }
    // manually installing can be used as workaround for issues with multiple users - have checkbox in preferences to toggle this
    /*
    public void installByIntent(File backupDir, String apk)
    {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(new File(backupDir, apk)), "application/vnd.android.package-archive");
        context.startActivity(intent);
    }
    */
    // due to changes in 4.3 (api level 18) the root user cannot see /storage/emulated/$user/ so calls using su (except pm in restoreApk) should swap the first part with /mnt/shell/emulated/, which is readable by the root user
    // api 23 (android 6) seems to have reverted to the old behaviour
    public String swapBackupDirPath(String path)
    {
        if(Build.VERSION.SDK_INT < 23)
        {
            if(path.contains("/storage/emulated/"))
            {
                path = path.replace("/storage/emulated/", "/mnt/shell/emulated/");
            }
        }
        return path;
    }
    public void copySelfAPk(File backupSubDir, String apk)
    {
        if(prefs.getBoolean("copySelfApk", false))
        {
            String parent = backupSubDir.getParent() + "/" + TAG + ".apk";
            String apkPath = backupSubDir.getAbsolutePath() + "/" + new File(apk).getName();
            List<String> commands = new ArrayList<>();
            commands.add(busybox + " cp " + apkPath + " " + parent);
            commandHandler.runCmd("sh", commands, line -> {},
                line -> writeErrorLog("", line),
                e -> Log.e(TAG, "copySelfApk: ", e), this);
        }
    }
    public File getExternalFilesDirPath(Context context, String packageData)
    {
        //String externalFilesPath = context.getExternalFilesDir(null).getParentFile().getParentFile().getAbsolutePath();
        File externalFilesDir = new File(extAndroidData, new File(packageData).getName());
        if(externalFilesDir.exists())
            return externalFilesDir;
        return null;
    }

    public File getExpansionDirectoryPath(Context context, String packageData)
    {
        String expansionFilesPath = Objects.requireNonNull(context.getObbDir().getParentFile()).getAbsolutePath();
        File expansionFilesDir = new File(expansionFilesPath, new File(packageData).getName());
        if(expansionFilesDir.exists())
            return expansionFilesDir;
        return null;
    }

    private static class Ownership {
        private int uid;
        private int gid;
        private final boolean legacyMode;

        public Ownership(int uid, int gid) {
            this.uid = uid;
            this.gid = gid;
            this.legacyMode = false;
        }

        // only for legacy compatibility
        public Ownership(String uidStr, String gidStr) throws OwnershipException {
            if((uidStr == null || uidStr.isEmpty()) ||
                    (gidStr == null || gidStr.isEmpty())) {
                throw new OwnershipException(
                        "cannot initiate ownership object with empty uid or gid");
            }
            this.uidStr = uidStr;
            this.gidStr = gidStr;
            this.legacyMode = true;
        }
        private String uidStr;
        private String gidStr;

        @NonNull
        @Override
        public String toString() {
            if(legacyMode) {
                return String.format("%s:%s", uidStr, gidStr);
            } else {
                return String.format("%s:%s", uid, gid);
            }
        }

    }

    private static class OwnershipException extends Exception {
        public OwnershipException(String msg) {
            super(msg);
        }
    }
}
