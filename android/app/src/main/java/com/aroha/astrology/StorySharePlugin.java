package com.aroha.astrology;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
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
 * Hands a picture the web app drew (a Daily Story's share card) to another
 * app. A webview cannot attach a file to a share by itself, and the web's own
 * share sheet does not open inside it, so the web app passes the PNG here.
 *
 * The picture is written to the app's cache and offered through the
 * FileProvider, so no storage permission is needed. `target` picks the app:
 * WhatsApp (its picker lists "My status" first), Instagram's story composer,
 * Instagram, X, or the phone's own share sheet for anything else.
 */
@CapacitorPlugin(name = "StoryShare")
public class StorySharePlugin extends Plugin {

    private static final String PNG = "image/png";
    private static final String WHATSAPP = "com.whatsapp";
    private static final String WHATSAPP_BUSINESS = "com.whatsapp.w4b";
    private static final String INSTAGRAM = "com.instagram.android";
    private static final String INSTAGRAM_STORY_ACTIVITY = "com.instagram.share.handleractivity.StoryShareHandlerActivity";
    private static final String X = "com.twitter.android";

    @PluginMethod
    public void shareImage(final PluginCall call) {
        String data = call.getString("base64");
        String fileName = call.getString("fileName", "aroha-story.png");
        String text = call.getString("text", "");
        String target = call.getString("target", "system");
        if (data == null || data.isEmpty()) {
            call.reject("base64 is required");
            return;
        }
        // Accept a full data URL too.
        int comma = data.indexOf(',');
        if (data.startsWith("data:") && comma > 0) data = data.substring(comma + 1);
        // The name comes from the web page; it must stay a plain file name.
        fileName = fileName.replaceAll("[^A-Za-z0-9._-]", "_");

        Uri uri;
        try {
            uri = writeToCache(Base64.decode(data, Base64.DEFAULT), fileName);
        } catch (IllegalArgumentException e) {
            call.reject("Invalid image data");
            return;
        } catch (IOException e) {
            call.reject("Could not prepare the picture: " + e.getMessage());
            return;
        }

        boolean opened;
        switch (target) {
            case "whatsapp":
            case "whatsappStatus":
                opened = start(send(uri, text, WHATSAPP)) || start(send(uri, text, WHATSAPP_BUSINESS));
                break;
            case "instagramStory":
                // Straight into the story composer; if Instagram ever renames that screen,
                // its own picker (Story, Feed, Reels, Message) still gets the picture.
                Intent story = send(uri, text, INSTAGRAM);
                story.setComponent(new ComponentName(INSTAGRAM, INSTAGRAM_STORY_ACTIVITY));
                opened = start(story) || start(send(uri, text, INSTAGRAM));
                break;
            case "instagram":
                opened = start(send(uri, text, INSTAGRAM));
                break;
            case "x":
                opened = start(send(uri, text, X));
                break;
            default:
                opened = start(Intent.createChooser(send(uri, text, null), null));
                break;
        }

        if (!opened) {
            call.reject("That app is not installed on this phone", "not_installed");
            return;
        }
        JSObject ret = new JSObject();
        ret.put("opened", true);
        call.resolve(ret);
    }

    private Uri writeToCache(byte[] bytes, String fileName) throws IOException {
        // "images/" is a folder res/xml/file_paths.xml lets the FileProvider share.
        File dir = new File(getContext().getCacheDir(), "images");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("No cache folder");
        File file = new File(dir, fileName);
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        }
        return FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", file);
    }

    private Intent send(Uri uri, String text, String packageName) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(PNG);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        if (text != null && !text.isEmpty()) intent.putExtra(Intent.EXTRA_TEXT, text);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (packageName != null) {
            intent.setPackage(packageName);
            // The grant flag alone is not honoured by every receiving app; name the app too.
            getContext().grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        return intent;
    }

    /** False when no installed app takes the intent. */
    private boolean start(Intent intent) {
        try {
            getActivity().startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
