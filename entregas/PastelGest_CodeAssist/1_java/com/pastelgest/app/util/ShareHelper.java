package com.pastelgest.app.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;

/** Partilha/abre ficheiros gerados (PDF, CSV) através do FileProvider. */
public final class ShareHelper {
    public static final String AUTHORITY = "com.pastelgest.app.fileprovider";

    private ShareHelper() { }

    public static void share(Context c, File f, String mime, String title) {
        Uri uri = FileProvider.getUriForFile(c, AUTHORITY, f);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(mime);
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.putExtra(Intent.EXTRA_SUBJECT, title);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent chooser = Intent.createChooser(send, title);
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(chooser);
        } catch (ActivityNotFoundException e) {
            com.pastelgest.app.ui.Ui.toast(c, "Nenhuma aplicação disponível para partilhar o ficheiro.");
        }
    }

    public static File cacheFile(Context c, String folder, String name) {
        File dir = new File(c.getCacheDir(), folder);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, name);
    }
}
