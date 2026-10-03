package com.aroha.astrology;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import androidx.core.content.FileProvider;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Keeps a PDF the web app made (a report's "Download PDF" button). A webview
 * ignores an ordinary browser download, so the web app hands the file's bytes
 * over here instead.
 *
 * On Android 10 (API 29) and later the file goes into Downloads/Aroha through
 * MediaStore, which needs no storage permission, and is then opened. Older
 * phones would need that permission to write there, so the file is written to
 * the app's cache and opened from it; the reader can save it from the viewer.
 */
@CapacitorPlugin(name = "FileSaver")
public class FileSaverPlugin extends Plugin {

    private static final String PDF = "application/pdf";

    @PluginMethod
    public void savePdf(final PluginCall call) {
        String data = call.getString("base64");
        String fileName = call.getString("fileName", "aroha.pdf");
        if (data == null || data.isEmpty()) {
            call.reject("base64 is required");
            return;
        }
        // Accept a full data URL too.
        int comma = data.indexOf(',');
        if (data.startsWith("data:") && comma > 0) data = data.substring(comma + 1);
        // The name comes from the web page; it must stay a plain file name.
        fileName = fileName.replaceAll("[^A-Za-z0-9._-]", "_");

        byte[] bytes;
        try {
            bytes = Base64.decode(data, Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            call.reject("Invalid file data");
            return;
        }

        boolean saved = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
        Uri uri;
        try {
            uri = saved ? writeToDownloads(bytes, fileName) : writeToCache(bytes, fileName);
        } catch (IOException e) {
            call.reject("Could not save the PDF: " + e.getMessage());
            return;
        }

        boolean opened = open(uri);
        if (!saved && !opened) {
            call.reject("No app on this phone can open a PDF", "unsupported");
            return;
        }

        JSObject ret = new JSObject();
        ret.put("uri", uri.toString());
        ret.put("saved", saved);
        ret.put("opened", opened);
        call.resolve(ret);
    }

    private Uri writeToDownloads(byte[] bytes, String fileName) throws IOException {
        ContentResolver resolver = getContext().getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, PDF);
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Aroha");
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("No file could be created");
        try (OutputStream out = resolver.openOutputStream(uri)) {
            if (out == null) throw new IOException("No output stream");
            out.write(bytes);
        } catch (IOException | RuntimeException e) {
            resolver.delete(uri, null, null);
            throw new IOException(e.getMessage(), e);
        }
        ContentValues done = new ContentValues();
        done.put(MediaStore.Downloads.IS_PENDING, 0);
        resolver.update(uri, done, null, null);
        return uri;
    }

    private Uri writeToCache(byte[] bytes, String fileName) throws IOException {
        // "pdf/" is the folder res/xml/file_paths.xml lets the FileProvider share.
        File dir = new File(getContext().getCacheDir(), "pdf");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("No cache folder");
        File file = new File(dir, fileName);
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        }
        return FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", file);
    }

    /** Shows the PDF in whatever viewer the phone has. False when it has none. */
    private boolean open(Uri uri) {
        Intent view = new Intent(Intent.ACTION_VIEW);
        view.setDataAndType(uri, PDF);
        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getContext().startActivity(view);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
