package com.aroha.astrology;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.OutputStream;

/**
 * Saves a PNG the web app drew (the Digital Yantra and its phone wallpaper)
 * into the phone's gallery, under Pictures/Aroha. Uses MediaStore, which
 * needs no storage permission on Android 10 (API 29) and later; older phones
 * get a rejection with code "unsupported" and the app falls back to opening
 * the image so it can be long-pressed and saved.
 */
@CapacitorPlugin(name = "ImageSaver")
public class ImageSaverPlugin extends Plugin {

    @PluginMethod
    public void savePng(final PluginCall call) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            call.reject("Saving to the gallery needs Android 10 or later", "unsupported");
            return;
        }
        String data = call.getString("base64");
        String fileName = call.getString("fileName", "aroha.png");
        if (data == null || data.isEmpty()) {
            call.reject("base64 is required");
            return;
        }
        // Accept a full data URL too.
        int comma = data.indexOf(',');
        if (data.startsWith("data:") && comma > 0) data = data.substring(comma + 1);

        byte[] bytes;
        try {
            bytes = Base64.decode(data, Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            call.reject("Invalid image data");
            return;
        }

        ContentResolver resolver = getContext().getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Aroha");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            call.reject("Could not create the image file");
            return;
        }
        try (OutputStream out = resolver.openOutputStream(uri)) {
            if (out == null) throw new java.io.IOException("No output stream");
            out.write(bytes);
        } catch (Exception e) {
            resolver.delete(uri, null, null);
            call.reject("Could not save the image: " + e.getMessage());
            return;
        }
        ContentValues done = new ContentValues();
        done.put(MediaStore.Images.Media.IS_PENDING, 0);
        resolver.update(uri, done, null, null);

        JSObject ret = new JSObject();
        ret.put("uri", uri.toString());
        call.resolve(ret);
    }
}
