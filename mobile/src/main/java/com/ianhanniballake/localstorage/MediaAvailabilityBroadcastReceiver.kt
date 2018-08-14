package com.ianhanniballake.localstorage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract

/**
 * Receives MEDIA_MOUNTED and MEDIA_REMOVED actions in response to external storage devices changing state
 */
class MediaAvailabilityBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_MEDIA_MOUNTED, Intent.ACTION_MEDIA_REMOVED ->
                // Update the root URI to ensure that only available storage directories appear
                context.contentResolver.notifyChange(
                        DocumentsContract.buildRootsUri(BuildConfig.DOCUMENTS_AUTHORITY), null)
        }
    }
}
