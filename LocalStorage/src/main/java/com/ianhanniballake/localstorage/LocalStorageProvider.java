package com.ianhanniballake.localstorage;

import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class LocalStorageProvider extends DocumentsProvider {
    @Override
    public Cursor queryRoots(final String[] projection) throws FileNotFoundException {
        // Create a cursor with either the requested fields, or the default
        // projection if "projection" is null.
        final MatrixCursor result =
                new MatrixCursor(resolveRootProjection(projection));
        // Add Home directory
        File homeDir = Environment.getExternalStorageDirectory();
        final MatrixCursor.RowBuilder row = result.newRow();
        row.add(Root.COLUMN_ROOT_ID, homeDir.getAbsolutePath());
        row.add(Root.COLUMN_DOCUMENT_ID, homeDir.getAbsolutePath());
        row.add(Root.COLUMN_TITLE, getContext().getString(R.string.home));
        row.add(Root.COLUMN_SUMMARY, homeDir.getAbsolutePath());
        row.add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY | Root.FLAG_SUPPORTS_CREATE);
        row.add(Root.COLUMN_ICON, R.drawable.ic_launcher);
        row.add(Root.COLUMN_AVAILABLE_BYTES, new StatFs(homeDir.getAbsolutePath()).getAvailableBytes());
        return result;
    }

    @Override
    public String createDocument(final String parentDocumentId, final String mimeType,
                                 final String displayName) throws FileNotFoundException {
        File newFile = new File(parentDocumentId, displayName);
        try {
            newFile.createNewFile();
            return newFile.getAbsolutePath();
        } catch (IOException e) {
            Log.e(LocalStorageProvider.class.getSimpleName(), "Error creating new file " + newFile);
        }
        return null;
    }

    @Override
    public AssetFileDescriptor openDocumentThumbnail(final String documentId, final Point sizeHint,
                                                     final CancellationSignal signal) throws FileNotFoundException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(documentId, options);
        final int targetHeight = 2 * sizeHint.y;
        final int targetWidth = 2 * sizeHint.x;
        final int height = options.outHeight;
        final int width = options.outWidth;
        options.inSampleSize = 1;
        if (height > targetHeight || width > targetWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / options.inSampleSize) > targetHeight
                    || (halfWidth / options.inSampleSize) > targetWidth) {
                options.inSampleSize *= 2;
            }
        }
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(documentId, options);
        File tempFile = null;
        FileOutputStream out = null;
        try {
            tempFile = File.createTempFile("thumbnail", null, getContext().getCacheDir());
            out = new FileOutputStream(tempFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
        } catch (IOException e) {
            Log.e(LocalStorageProvider.class.getSimpleName(), "Error writing thumbnail", e);
            return null;
        } finally {
            if (out != null)
                try {
                    out.close();
                } catch (IOException e) {
                    Log.e(LocalStorageProvider.class.getSimpleName(), "Error closing thumbnail", e);
                }
        }
        return new AssetFileDescriptor(ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY), 0, AssetFileDescriptor.UNKNOWN_LENGTH);
    }

    private String[] resolveRootProjection(final String[] projection) {
        if (projection != null)
            return projection;
        else
            return new String[]{Root.COLUMN_ROOT_ID, Root.COLUMN_SUMMARY, Root.COLUMN_FLAGS, Root.COLUMN_TITLE,
                    Root.COLUMN_DOCUMENT_ID, Root.COLUMN_ICON, Root.COLUMN_AVAILABLE_BYTES};
    }

    @Override
    public Cursor queryChildDocuments(final String parentDocumentId, final String[] projection,
                                      final String sortOrder) throws FileNotFoundException {
        final MatrixCursor result = new
                MatrixCursor(resolveDocumentProjection(projection));
        final File parent = new File(parentDocumentId);
        for (File file : parent.listFiles()) {
            if (!file.getName().startsWith(".")) {
                // Adds the file's display name, MIME type, size, and so on.
                includeFile(result, file);
            }
        }
        return result;
    }

    @Override
    public Cursor queryDocument(final String documentId, final String[] projection) throws FileNotFoundException {
        Log.d(LocalStorageProvider.class.getSimpleName(), "Document Id: " + documentId);
        // Create a cursor with the requested projection, or the default projection.
        final MatrixCursor result = new
                MatrixCursor(resolveDocumentProjection(projection));
        includeFile(result, new File(documentId));
        return result;
    }

    private void includeFile(final MatrixCursor result, final File file) throws FileNotFoundException {
        final MatrixCursor.RowBuilder row = result.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, file.getAbsolutePath());
        row.add(Document.COLUMN_DISPLAY_NAME, file.getName());
        String mimeType = getDocumentType(file.getAbsolutePath());
        row.add(Document.COLUMN_MIME_TYPE, mimeType);
        int flags = file.canWrite() ? Document.FLAG_SUPPORTS_DELETE | Document.FLAG_SUPPORTS_WRITE : 0;
        if (mimeType.startsWith("image/"))
            flags |= Document.FLAG_SUPPORTS_THUMBNAIL;
        row.add(Document.COLUMN_FLAGS, flags);
        row.add(Document.COLUMN_SIZE, file.length());
        row.add(Document.COLUMN_LAST_MODIFIED, file.lastModified());
    }

    @Override
    public String getDocumentType(final String documentId) throws FileNotFoundException {
        File file = new File(documentId);
        if (file.isDirectory())
            return Document.MIME_TYPE_DIR;
        final int lastDot = file.getName().lastIndexOf('.');
        if (lastDot >= 0) {
            final String extension = file.getName().substring(lastDot + 1);
            final String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mime != null) {
                return mime;
            }
        }
        return "application/octet-stream";
    }

    @Override
    public void deleteDocument(final String documentId) throws FileNotFoundException {
        new File(documentId).delete();
    }

    private String[] resolveDocumentProjection(final String[] projection) {
        if (projection != null)
            return projection;
        else
            return new String[]{Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_FLAGS,
                    Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED};
    }

    @Override
    public ParcelFileDescriptor openDocument(final String documentId, final String mode,
                                             final CancellationSignal signal) throws FileNotFoundException {
        File file = new File(documentId);
        final boolean isWrite = (mode.indexOf('w') != -1);
        if (isWrite) {
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE);
        } else {
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        }
    }

    @Override
    public boolean onCreate() {
        return true;
    }
}
