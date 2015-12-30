package com.ianhanniballake.localstorage;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.DocumentsContract;

/**
 * Receives MEDIA_MOUNTED and MEDIA_REMOVED actions in response to external storage devices changing state
 */
public class MediaAvailabilityBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, final Intent intent) {
        // Update the root URI to ensure that only available storage directories appear
        context.getContentResolver().notifyChange(
                DocumentsContract.buildRootsUri(BuildConfig.DOCUMENTS_AUTHORITY), null);
    }
}
