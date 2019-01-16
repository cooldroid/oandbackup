package dk.jens.backup;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class HandleShares
{
    private static Map<String, String> mimeTypes;
    public static Intent constructIntentSingle(Context context, String title, File file)
    {
        String ext = file.getName().substring(file.getName().lastIndexOf(".") + 1).toLowerCase(Locale.ENGLISH);
        Intent intent = new Intent();
        Uri apkURI = FileProvider.getUriForFile(
                context,
                context.getApplicationContext()
                        .getPackageName() + ".provider", file);
        intent.setAction(Intent.ACTION_SEND);
        intent.setType(getMimeType(ext));
        intent.putExtra(Intent.EXTRA_STREAM, apkURI);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return Intent.createChooser(intent, title);
    }
    public static Intent constructIntentMultiple(Context context, String title, File... files)
    {
        ArrayList<Uri> uris = new ArrayList<>();
        for(File file : files)
        {
            Uri apkURI = FileProvider.getUriForFile(
                    context,
                    context.getApplicationContext()
                            .getPackageName() + ".provider", file);
            uris.add(apkURI);
        }
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND_MULTIPLE);
        // assume an apk and a zip of data is being sent
        intent.setType("application/*");
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return Intent.createChooser(intent, title);
    }
    public static String getMimeType(String extension)
    {
        if(mimeTypes == null)
        {
            mimeTypes = new HashMap<>();
            mimeTypes.put("apk", "application/vnd.android.package-archive");
            mimeTypes.put("zip", "application/zip");
            mimeTypes.put("gz", "application/tar+gzip");
        }
        if(mimeTypes.containsKey(extension))
        {
            return mimeTypes.get(extension);
        }
        else
        {
            return "*/*"; // catch-all mimetype
        }
    }
}