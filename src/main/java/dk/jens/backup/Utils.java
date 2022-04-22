package dk.jens.backup;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;

import dk.jens.backup.ui.HandleMessages;

public class Utils {
    @SuppressLint("SimpleDateFormat")
    public static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd - HH:mm:ss");
    @SuppressLint("SimpleDateFormat")
    public static final SimpleDateFormat logFileDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");

    public static void showErrors(final Activity activity) {
        activity.runOnUiThread(() -> {
            String errors = ShellCommands.getErrors();
            if (errors.length() > 0) {
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.errorDialogTitle)
                        .setMessage(errors)
                        .setPositiveButton(R.string.dialogOK, null)
                        .show();
                ShellCommands.clearErrors();
            }
        });
    }

    public static File createBackupDir(final Activity activity, final String path) {
        FileCreationHelper fileCreator = new FileCreationHelper();
        File backupDir;
        if (path.trim().length() > 0) {
            backupDir = fileCreator.createBackupFolder(path);
            if (fileCreator.isFallenBack()) {
                activity.runOnUiThread(() -> Toast.makeText(activity, activity.getString(R.string.mkfileError) + " " + path + " - " + activity.getString(R.string.fallbackToDefault) + ": " + FileCreationHelper.getDefaultBackupDirPath(), Toast.LENGTH_LONG).show());
            }
        } else {
            backupDir = fileCreator.createBackupFolder(FileCreationHelper.getDefaultBackupDirPath());
        }
        if (backupDir == null) {
            showWarning(activity, activity.getString(R.string.mkfileError) + " " + FileCreationHelper.getDefaultBackupDirPath(), activity.getString(R.string.backupFolderError));
        }
        return backupDir;
    }

    public static void showWarning(final Activity activity, final String title, final String message) {
        activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setNeutralButton(R.string.dialogOK, (dialog, id) -> {
                })
                .setCancelable(false)
                .show());
    }

    public static void showConfirmDialog(Activity activity, String title, String message, final Command confirmCommand) {
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.dialogOK, (dialog, id) -> confirmCommand.execute())
                .setNegativeButton(R.string.dialogCancel, (dialog, id) -> {
                })
                .show();
    }

    public static void reloadWithParentStack(Activity activity) {
        Intent intent = activity.getIntent();
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        activity.finish();
        activity.overridePendingTransition(0, 0);
        TaskStackBuilder.create(activity)
                .addNextIntentWithParentStack(intent)
                .startActivities();
    }

    public static void reShowMessage(HandleMessages handleMessages, long tid) {
        // since messages are progressdialogs and not dialogfragments they need to be set again manually
        if (tid != -1)
            for (Thread t : Thread.getAllStackTraces().keySet())
                if (t.getId() == tid && t.isAlive())
                    handleMessages.reShowMessage();
    }

    public static String getName(String path) {
        if (path.endsWith(File.separator))
            path = path.substring(0, path.length() - 1);
        return path.substring(path.lastIndexOf(File.separator) + 1);
    }

    public static void logDeviceInfo(Context context, String tag) {
        final String abiVersion = AssetsHandler.getAbi();
        try {
            final String packageName = context.getPackageName();
            final PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(packageName, 0);
            final int versionCode = packageInfo.versionCode;
            final String versionName = packageInfo.versionName;
            Log.i(tag, String.format("running version %s/%s on abi %s",
                    versionCode, versionName, abiVersion));
        } catch (PackageManager.NameNotFoundException e) {
            Log.i(tag, String.format(
                    "unable to determine package version (%s), abi version %s",
                    e.toString(), abiVersion));
        }
    }

    public interface Command {
        void execute();
    }

    public static String formatDate(Date date, boolean localTimestampFormat) {
        String dateFormated;
        if (localTimestampFormat) {
            DateFormat dateFormat = DateFormat.getDateTimeInstance();
            dateFormated = dateFormat.format(date);
        } else {
            dateFormated = simpleDateFormat.format(date);
        }
        return dateFormated;
    }

    public static String convertDate(String strDate, SimpleDateFormat fromDateFormat,
                                     SimpleDateFormat toDateFormat) {
        try {
            return toDateFormat.format(Objects.requireNonNull(fromDateFormat.parse(strDate)));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return strDate;
    }

    public static void deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        directoryToBeDeleted.delete();
    }

    public static byte[] initIv() {
        int blockSize;
        try {
            blockSize = Cipher.getInstance("AES/GCM/NoPadding").getBlockSize();
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            blockSize = 32;
        }

        // create byte array
        byte[] bytes = new byte[blockSize];
        // put the next byte in the array
        new Random().nextBytes(bytes);

        return  bytes;
    }
}
