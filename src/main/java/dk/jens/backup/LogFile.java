package dk.jens.backup;

import android.annotation.SuppressLint;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class LogFile implements Parcelable
{
    final static String TAG = OAndBackup.TAG;
    private String packageLabel, packageName, versionName, sourceDir, dataDir, backupDate;
    private int versionCode, backupMode;
    private boolean encrypted, system, hasApk, hasAppData, hasExternalData;
    private String[] splitSourceDirs;
    public LogFile(File backupSubDir, String packageName)
    {
        FileReaderWriter frw = new FileReaderWriter(backupSubDir.getAbsolutePath(), packageName + ".log");
        String json = frw.read();
        try
        {
            JSONObject jsonObject = new JSONObject(json);
            this.packageLabel = jsonObject.getString("packageLabel");
            this.packageName = jsonObject.getString("packageName");
            this.versionName = jsonObject.getString("versionName");
            this.sourceDir = jsonObject.getString("sourceDir");
            JSONArray arrJson = jsonObject.optJSONArray("splitSourceDirs");
            String[] arrStr = new String[Objects.requireNonNull(arrJson).length()];
            for(int i = 0; i < arrJson.length(); i++)
                arrStr[i] = arrJson.getString(i);
            this.splitSourceDirs = arrStr;
            this.dataDir = jsonObject.getString("dataDir");
            this.backupDate = jsonObject.getString("backupDate");
            this.versionCode = jsonObject.getInt("versionCode");
            this.encrypted = jsonObject.optBoolean("isEncrypted");
            this.system = jsonObject.optBoolean("isSystem");
            this.hasApk = jsonObject.optBoolean("hasApk");
            this.hasAppData = jsonObject.optBoolean("hasAppData");
            this.hasExternalData = jsonObject.optBoolean("hasExternalData");
            this.backupMode = jsonObject.optInt("backupMode", AppInfo.MODE_UNSET);
        }
        catch(JSONException e)
        {
            Log.e(TAG, packageName + ": error while reading logfile: " + e.toString());
            this.packageLabel = this.packageName = this.versionName = this.sourceDir = this.dataDir = this.backupDate = "";
            this.versionCode = 0;
            this.encrypted = this.system = this.hasApk = this.hasAppData = this.hasExternalData = false;
            this.backupMode = AppInfo.MODE_UNSET;
            this.splitSourceDirs = new String[0];
        }
    }
    public String getPackageLabel()
    {
        return packageLabel;
    }
    public String getPackageName()
    {
        return packageName;
    }
    public String getVersionName()
    {
        return versionName;
    }
    public int getVersionCode()
    {
        return versionCode;
    }
    public String getSourceDir()
    {
        return sourceDir;
    }
    public String getApk()
    {
        if(sourceDir != null && sourceDir.length() > 0)
            return "base.apk";
        return null;
    }
    public String getDataDir()
    {
        return dataDir;
    }
    public String getBackupDate()
    {
        return backupDate;
    }
    public boolean isEncrypted()
    {
        return encrypted;
    }
    public boolean isSystem()
    {
        return system;
    }
    public String[] getSplitSourceDirs() {
        return splitSourceDirs;
    }
    public int getBackupMode()
    {
        return backupMode;
    }
    public static void writeLogFile(File backupSubDir, AppInfo appInfo, int backupMode)
    {
        // the boolean for encrypted backups are only written if the encrypted succeeded so false is written first by default
        writeLogFile(backupSubDir, appInfo, backupMode, false);
    }

    public static void writeLogFile(File backupSubDir, AppInfo appInfo, int backupMode, boolean encrypted)
    {
        try
        {
            // path to apk should only be logged if it is backed up
            String sourceDir = "";
            if(backupMode == AppInfo.MODE_APK || backupMode == AppInfo.MODE_BOTH)
                sourceDir = appInfo.getSourceDir();
            else
                if(appInfo.getLogInfo() != null)
                    sourceDir = appInfo.getLogInfo().getSourceDir();

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("packageLabel", appInfo.getLabel());
            jsonObject.put("versionName", appInfo.getVersionName());
            jsonObject.put("versionCode", appInfo.getVersionCode());
            jsonObject.put("packageName", appInfo.getPackageName());
            jsonObject.put("sourceDir", sourceDir);
            jsonObject.put("splitSourceDirs", appInfo.getSplitSourceDirs() == null ?
                    new JSONArray() : appInfo.getSplitSourceDirs());
            jsonObject.put("dataDir", appInfo.getDataDir());
            jsonObject.put("backupDate", Utils.logFileDateFormat.format(new Date()));
            jsonObject.put("isEncrypted", encrypted);
            jsonObject.put("isSystem", appInfo.isSystem());
            jsonObject.put("backupMode", appInfo.getBackupMode());
            jsonObject.put("hasApk", appInfo.isHasApk());
            jsonObject.put("hasAppData", appInfo.isHasAppData());
            jsonObject.put("hasExternalData", appInfo.isHasExternalData());
            String json = jsonObject.toString().replace("\\","");
            File outFile = new File(backupSubDir, appInfo.getPackageName() + ".log");
            outFile.createNewFile();
            try(BufferedWriter bw = new BufferedWriter(
                    new FileWriter(outFile.getAbsoluteFile()))) {
                bw.write(json + "\n");
            }
        }
        catch(JSONException | IOException e)
        {
            Log.e(TAG, "LogFile.writeLogFile: " + e.toString());
        }
    }
    public int describeContents()
    {
        return 0;
    }
    public void writeToParcel(Parcel out, int flags)
    {
        out.writeString(packageLabel);
        out.writeString(packageName);
        out.writeString(versionName);
        out.writeString(sourceDir);
        out.writeStringArray(splitSourceDirs);
        out.writeString(dataDir);
        out.writeInt(versionCode);
        out.writeInt(backupMode);
        out.writeString(backupDate);
        out.writeBooleanArray(new boolean[] {encrypted, system, hasApk, hasAppData, hasExternalData});
    }
    public static final Parcelable.Creator<LogFile> CREATOR = new Parcelable.Creator<LogFile>()
    {
        public LogFile createFromParcel(Parcel in)
        {
            return new LogFile(in);
        }
        public LogFile[] newArray(int size)
        {
            return new LogFile[size];
        }
    };
    private LogFile(Parcel in)
    {
        // data is read in the order it was written
        packageLabel = in.readString();
        packageName = in.readString();
        versionName = in.readString();
        sourceDir = in.readString();
        in.readStringArray(splitSourceDirs);
        dataDir = in.readString();
        versionCode = in.readInt();
        backupMode = in.readInt();
        backupDate = in.readString();
        boolean[] bools = new boolean[5];
        in.readBooleanArray(bools);
        encrypted = bools[0];
        system = bools[1];
        hasApk = bools[2];
        hasAppData = bools[3];
        hasExternalData = bools[4];
    }
}
