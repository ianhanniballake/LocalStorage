package com.ianhanniballake.localstorage

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.support.v4.content.ContextCompat
import android.support.v4.os.EnvironmentCompat
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

class LocalStorageProvider : DocumentsProvider() {

    companion object {
        private const val TAG = "LocalStorageProvider"
        /**
         * Default root projection: everything but Root.COLUMN_MIME_TYPES
         */
        private val DEFAULT_ROOT_PROJECTION = arrayOf(
                Root.COLUMN_ROOT_ID,
                Root.COLUMN_SUMMARY,
                Root.COLUMN_FLAGS,
                Root.COLUMN_TITLE,
                Root.COLUMN_DOCUMENT_ID,
                Root.COLUMN_ICON,
                Root.COLUMN_AVAILABLE_BYTES)
        /**
         * Default document projection: everything but Document.COLUMN_ICON and Document.COLUMN_SUMMARY
         */
        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_FLAGS,
                Document.COLUMN_MIME_TYPE,
                Document.COLUMN_SIZE,
                Document.COLUMN_LAST_MODIFIED)

        /**
         * Check to see if we are missing the Storage permission group. In those cases, we cannot
         * access local files and must invalidate any root URIs currently available.
         *
         * @param context The current Context
         * @return whether the permission has been granted it is safe to proceed
         */
        internal fun isMissingPermission(context: Context?): Boolean {
            if (context == null) {
                return true
            }
            if (ContextCompat.checkSelfPermission(context,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                // Make sure that our root is invalidated as apparently we lost permission
                context.contentResolver.notifyChange(
                        DocumentsContract.buildRootsUri(BuildConfig.DOCUMENTS_AUTHORITY), null)
                return true
            }
            return false
        }
    }

    @SuppressLint("InlinedApi")
    override fun queryRoots(projection: Array<String>?): Cursor? {
        val context = context ?: return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        // Create a cursor with either the requested fields, or the default projection if "projection" is null.
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        // Add Home directory
        val homeDir = Environment.getExternalStorageDirectory()
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            val row = result.newRow()
            // These columns are required
            row.add(Root.COLUMN_ROOT_ID, homeDir.absolutePath)
            row.add(Root.COLUMN_DOCUMENT_ID, homeDir.absolutePath)
            row.add(Root.COLUMN_TITLE, context.getString(R.string.home))
            row.add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY or Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD)
            row.add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
            // These columns are optional
            row.add(Root.COLUMN_SUMMARY, homeDir.absolutePath)
            row.add(Root.COLUMN_AVAILABLE_BYTES, StatFs(homeDir.absolutePath).availableBytes)
            // Root.COLUMN_MIME_TYPE is another optional column and useful if you have multiple roots with different
            // types of mime types (roots that don't match the requested mime type are automatically hidden)
        }
        // Add SD card directory
        val sdCard = File("/storage/extSdCard")
        val storageState = EnvironmentCompat.getStorageState(sdCard)
        if (storageState == Environment.MEDIA_MOUNTED ||
                storageState == Environment.MEDIA_MOUNTED_READ_ONLY) {
            val row = result.newRow()
            // These columns are required
            row.add(Root.COLUMN_ROOT_ID, sdCard.absolutePath)
            row.add(Root.COLUMN_DOCUMENT_ID, sdCard.absolutePath)
            row.add(Root.COLUMN_TITLE, context.getString(R.string.sd_card))
            // Always assume SD Card is read-only
            row.add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY)
            row.add(Root.COLUMN_ICON, R.drawable.ic_sd_card)
            row.add(Root.COLUMN_SUMMARY, sdCard.absolutePath)
            row.add(Root.COLUMN_AVAILABLE_BYTES, StatFs(sdCard.absolutePath).availableBytes)
        }
        return result
    }

    override fun createDocument(
            parentDocumentId: String,
            mimeType: String,
            displayName: String
    ): String? {
        if (LocalStorageProvider.isMissingPermission(context)) {
            return null
        }
        val newFile = File(parentDocumentId, displayName)
        return try {
            if (mimeType == Document.MIME_TYPE_DIR) {
                val success = newFile.mkdir()
                if (!success) {
                    throw IOException("could not create directory")
                }
            } else {
                val success = newFile.createNewFile()
                if (!success) {
                    throw IOException("could not create file")
                }
            }
            newFile.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "Error creating new file $newFile")
            null
        }
    }

    @Throws(FileNotFoundException::class)
    override fun openDocumentThumbnail(
            documentId: String,
            sizeHint: Point,
            signal: CancellationSignal?
    ): AssetFileDescriptor? {
        val context = context ?: return null
        if (LocalStorageProvider.isMissingPermission(context)) {
            return null
        }
        // Assume documentId points to an image file. Build a thumbnail no larger than twice the sizeHint
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(documentId, options)
        val targetHeight = 2 * sizeHint.y
        val targetWidth = 2 * sizeHint.x
        val height = options.outHeight
        val width = options.outWidth
        options.inSampleSize = 1
        if (height > targetHeight || width > targetWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / options.inSampleSize > targetHeight || halfWidth / options.inSampleSize > targetWidth) {
                options.inSampleSize *= 2
            }
        }
        options.inJustDecodeBounds = false
        val bitmap = BitmapFactory.decodeFile(documentId, options)
        // Write out the thumbnail to a temporary file
        val tempFile: File
        try {
            tempFile = File.createTempFile("thumbnail", null, context.cacheDir)
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error writing thumbnail", e)
            return null
        }
        // It appears the Storage Framework UI caches these results quite aggressively so there is little reason to
        // write your own caching layer beyond what you need to return a single AssetFileDescriptor
        return AssetFileDescriptor(ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY), 0,
                AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        return documentId.startsWith(parentDocumentId)
    }

    override fun queryChildDocuments(
            parentDocumentId: String,
            projection: Array<String>?,
            sortOrder: String?
    ): Cursor? {
        if (LocalStorageProvider.isMissingPermission(context)) {
            return null
        }
        // Create a cursor with either the requested fields, or the default projection if "projection" is null.
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val parent = File(parentDocumentId)
        for (file in parent.listFiles()) {
            // Don't show hidden files/folders
            if (!file.name.startsWith(".")) {
                // Adds the file's display name, MIME type, size, and so on.
                includeFile(result, file)
            }
        }
        return result
    }

    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor? {
        if (LocalStorageProvider.isMissingPermission(context)) {
            return null
        }
        // Create a cursor with either the requested fields, or the default projection if "projection" is null.
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        includeFile(result, File(documentId))
        return result
    }

    private fun includeFile(result: MatrixCursor, file: File) {
        val row = result.newRow()
        // These columns are required
        row.add(Document.COLUMN_DOCUMENT_ID, file.absolutePath)
        row.add(Document.COLUMN_DISPLAY_NAME, file.name)
        val mimeType = getDocumentType(file.absolutePath)
        row.add(Document.COLUMN_MIME_TYPE, mimeType)
        @SuppressLint("InlinedApi")
        var flags = if (file.canWrite())
            Document.FLAG_SUPPORTS_DELETE or
                    Document.FLAG_SUPPORTS_WRITE or
                    Document.FLAG_SUPPORTS_RENAME or
                    if (mimeType == Document.MIME_TYPE_DIR) Document.FLAG_DIR_SUPPORTS_CREATE else 0
        else
            0
        // We only show thumbnails for image files - expect a call to openDocumentThumbnail for each file that has
        // this flag set
        if (mimeType?.startsWith("image/") == true)
            flags = flags or Document.FLAG_SUPPORTS_THUMBNAIL
        row.add(Document.COLUMN_FLAGS, flags)
        // COLUMN_SIZE is required, but can be null
        row.add(Document.COLUMN_SIZE, file.length())
        // These columns are optional
        row.add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
        // Document.COLUMN_ICON can be a resource id identifying a custom icon. The system provides default icons
        // based on mime type
        // Document.COLUMN_SUMMARY is optional additional information about the file
    }

    override fun getDocumentType(documentId: String): String? {
        if (LocalStorageProvider.isMissingPermission(context)) {
            return null
        }
        val file = File(documentId)
        if (file.isDirectory)
            return Document.MIME_TYPE_DIR
        // From FileProvider.getType(Uri)
        val lastDot = file.name.lastIndexOf('.')
        if (lastDot >= 0) {
            val extension = file.name.substring(lastDot + 1)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            if (mime != null) {
                return mime
            }
        }
        return "application/octet-stream"
    }

    override fun deleteDocument(documentId: String) {
        if (LocalStorageProvider.isMissingPermission(context)) {
            return
        }
        val file = File(documentId)
        if (!file.delete()) {
            Log.e(TAG, "Error deleting $documentId")
        }
    }

    @Throws(FileNotFoundException::class)
    override fun renameDocument(documentId: String, displayName: String): String? {
        if (LocalStorageProvider.isMissingPermission(context)) {
            return null
        }
        val existingFile = File(documentId)
        if (!existingFile.exists()) {
            throw FileNotFoundException("$documentId does not exist")
        }
        if (existingFile.name == displayName) {
            return null
        }
        val parentDirectory = existingFile.parentFile
        var newFile = File(parentDirectory, displayName)
        var conflictIndex = 1
        while (newFile.exists()) {
            newFile = File(parentDirectory, "${displayName}_${conflictIndex++}")
        }
        val success = existingFile.renameTo(newFile)
        if (!success) {
            throw FileNotFoundException("Unable to rename $documentId to ${existingFile.absolutePath}")
        }
        return existingFile.absolutePath
    }

    @Throws(FileNotFoundException::class)
    override fun openDocument(
            documentId: String,
            mode: String,
            signal: CancellationSignal?
    ): ParcelFileDescriptor? {
        if (LocalStorageProvider.isMissingPermission(context)) {
            return null
        }
        val file = File(documentId)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun onCreate(): Boolean {
        return true
    }
}
