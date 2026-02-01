package com.example.myappandroid.util;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class ImageStorage {
    private static final String DIR_NAME = "photos";

    private ImageStorage() {
    }

    public static String persistImage(Context context, Uri sourceUri) throws IOException {
        if (context == null || sourceUri == null) {
            return null;
        }
        File dir = new File(context.getFilesDir(), DIR_NAME);
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }
        String extension = guessExtension(context.getContentResolver(), sourceUri);
        if (extension == null) {
            extension = "jpg";
        }
        File outFile = new File(dir, "photo_" + System.currentTimeMillis() + "." + extension);
        try (InputStream input = context.getContentResolver().openInputStream(sourceUri);
             OutputStream output = new FileOutputStream(outFile)) {
            if (input == null) {
                return null;
            }
            byte[] buffer = new byte[8192];
            int len;
            while ((len = input.read(buffer)) != -1) {
                output.write(buffer, 0, len);
            }
        }
        return outFile.toURI().toString();
    }

    private static String guessExtension(ContentResolver resolver, Uri uri) {
        String mime = resolver.getType(uri);
        if (mime == null) {
            return null;
        }
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
    }
}
