package dk.jens.backup;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

public class AppInfo implements Comparable<AppInfo>, Parcelable {
    private LogFile logInfo;
    private String label, packageName, versionName, sourceDir, dataDir, backupDate;
    private int versionCode, backupMode;
    private boolean system, installed, checked, disabled, hasApk, hasAppData, hasExternalData;
    private String[] splitSourceDirs;
    public Bitmap icon;
    public static final int MODE_UNSET = 0;
    public static final int MODE_APK = 1;
    public static final int MODE_DATA = 2;
    public static final int MODE_BOTH = 3;

    public AppInfo(String packageName, String label, String versionName, int versionCode, String backupDate,
                   String sourceDir, String dataDir, boolean system, boolean installed, String[] splitSourceDirs) {
        this.label = label;
        this.packageName = packageName;
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.backupDate = backupDate;
        this.sourceDir = sourceDir;
        this.dataDir = dataDir;
        this.system = system;
        this.installed = installed;
        this.splitSourceDirs = splitSourceDirs;
        this.backupMode = MODE_UNSET;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getLabel() {
        return label;
    }

    public String getVersionName() {
        return versionName;
    }

    public int getVersionCode() {
        return versionCode;
    }

    public String getBackupDate() {
        return backupDate;
    }

    public String getSourceDir() {
        return sourceDir;
    }

    public String getDataDir() {
        return dataDir;
    }

    public int getBackupMode() {
        return backupMode;
    }

    public LogFile getLogInfo() {
        return logInfo;
    }

    public void setLogInfo(LogFile newLogInfo) {
        logInfo = newLogInfo;
        backupMode = logInfo.getBackupMode();
    }

    public void setBackupMode(int modeToAdd) {
        // add only if both values are different and neither is MODE_BOTH
        if (this.backupMode == MODE_BOTH || modeToAdd == MODE_BOTH) {
            this.backupMode = MODE_BOTH;
            this.hasApk = this.hasAppData = true;
        } else if (modeToAdd != backupMode)
            this.backupMode += modeToAdd;
        if (modeToAdd == MODE_APK)
            this.hasApk = true;
        if (modeToAdd == MODE_DATA)
            this.hasAppData = true;
    }

    public void setHasExternalData(boolean hasExternalData) {
        this.hasExternalData = hasExternalData;
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public boolean isSystem() {
        return system;
    }

    public boolean isInstalled() {
        return installed;
    }

    public String[] getSplitSourceDirs() {
        return splitSourceDirs;
    }

    public boolean isHasApk() {
        return hasApk;
    }

    public boolean isHasAppData() {
        return hasAppData;
    }

    public boolean isHasExternalData() {
        return hasExternalData;
    }

    // list of single files used by special backups - only for compatibility now
    public String[] getFilesList() {
        return null;
    }

    // should ideally be removed once proper polymorphism is implemented
    public boolean isSpecial() {
        return false;
    }

    public int compareTo(AppInfo appInfo) {
        return label.compareToIgnoreCase(appInfo.getLabel());
    }

    public String toString() {
        return label + " : " + packageName;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeParcelable(logInfo, flags);
        out.writeString(label);
        out.writeString(packageName);
        out.writeString(versionName);
        out.writeString(sourceDir);
        out.writeString(dataDir);
        out.writeInt(versionCode);
        out.writeString(backupDate);
        out.writeInt(backupMode);
        out.writeBooleanArray(new boolean[]{system, installed, checked});
        out.writeStringArray(splitSourceDirs);
        out.writeParcelable(icon, flags);
    }

    public static final Parcelable.Creator<AppInfo> CREATOR = new Parcelable.Creator<AppInfo>() {
        public AppInfo createFromParcel(Parcel in) {
            return new AppInfo(in);
        }

        public AppInfo[] newArray(int size) {
            return new AppInfo[size];
        }
    };

    protected AppInfo(Parcel in) {
        logInfo = in.readParcelable(getClass().getClassLoader());
        label = in.readString();
        packageName = in.readString();
        versionName = in.readString();
        sourceDir = in.readString();
        dataDir = in.readString();
        versionCode = in.readInt();
        backupDate = in.readString();
        backupMode = in.readInt();
        boolean[] bools = new boolean[3];
        in.readBooleanArray(bools);
        system = bools[0];
        installed = bools[1];
        checked = bools[2];
        splitSourceDirs = in.createStringArrayList().toArray(new String[0]);
        icon = in.readParcelable(getClass().getClassLoader());
    }
}
