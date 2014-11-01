package com.ianhanniballake.localstorage;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import com.android.vending.billing.IInAppBillingService;
import com.ianhanniballake.localstorage.inappbilling.Inventory;
import com.ianhanniballake.localstorage.inappbilling.Purchase;
import com.ianhanniballake.localstorage.inappbilling.Security;
import com.ianhanniballake.localstorage.inappbilling.SkuDetails;

import org.json.JSONException;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Activity controlling donations, including Paypal and In-App Billing
 */
public class DonateActivity extends ActionBarActivity {
    private final static String ITEM_TYPE_INAPP = "inapp";
    private final static String publicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnKZZkdNQIn/NPGUzeQ5WKSecjKbnTE0qNEKJNqGECHx8jSsMzUEXeM2iADJDmzDw5+yUwvAxtsLPVjiHL8s5G7jdTrN5qVA9XH+T2GnpCyPzavumgDH4DtNvDTOojJ79SXudzKynlxDXor5WYTsZTKAt+UImBcsBPR9+weYWqKZIJXIYSt2FP3OyUsKJrCuA9isqwpR/kpCbw372Rgdu85TfqDAkQcYe2cMCHU9NxilyBB+JzJxeOmE7+OI3JOBrjro8EQz1bHsVIT4cA498JcKw42tMRE6NVF6jCaxKFWl74qtxZ/muRc+3A7K/SPJvjwbkoz6yONqe+qiEpE6PuQIDAQAB";
    private final static String PURCHASED_SKU = "com.ianhanniballake.localstorage.PURCHASED_SKU";
    private final static int RC_REQUEST = 1;
    private static final String RESPONSE_CODE = "RESPONSE_CODE";
    private static final String RESPONSE_GET_SKU_DETAILS_LIST = "DETAILS_LIST";
    private static final String RESPONSE_INAPP_ITEM_LIST = "INAPP_PURCHASE_ITEM_LIST";
    private static final String RESPONSE_INAPP_PURCHASE_DATA_LIST = "INAPP_PURCHASE_DATA_LIST";
    private static final String RESPONSE_INAPP_SIGNATURE_LIST = "INAPP_DATA_SIGNATURE_LIST";
    /**
     * SKU Product Names
     */
    final HashMap<String, String> skuNames = new HashMap<String, String>();
    /**
     * US Prices for SKUs in micro-currency
     */
    final HashMap<String, Long> skuPrices = new HashMap<String, Long>();
    /**
     * InAppBillingService connection
     */
    IInAppBillingService mService;
    /**
     * Recently purchased SKU, if any. Should be saved in the instance state
     */
    String purchasedSku = "";
    /**
     * List of valid SKUs
     */
    String[] skus = new String[0];
    private ServiceConnection mServiceConn;

    /**
     * Gets the response code from the given Bundle. Workaround to bug where sometimes response codes come as Long
     * instead of Integer
     *
     * @param b Bundle to get response code
     * @return response code
     */
    static int getResponseCodeFromBundle(final Bundle b) {
        final Object o = b.get(RESPONSE_CODE);
        if (o == null)
            return 0;
        else if (o instanceof Integer)
            return (Integer) o;
        else if (o instanceof Long)
            return (int) ((Long) o).longValue();
        else
            throw new RuntimeException("Unexpected type for bundle response code: " + o.getClass().getName());
    }

    /**
     * Gets the response code from the given Intent. Workaround to bug where sometimes response codes come as Long
     * instead of Integer
     *
     * @param i Intent to get response code
     * @return response code
     */
    static int getResponseCodeFromIntent(final Intent i) {
        final Object o = i.getExtras().get(RESPONSE_CODE);
        if (o == null)
            return 0;
        else if (o instanceof Integer)
            return (Integer) o;
        else if (o instanceof Long)
            return (int) ((Long) o).longValue();
        else
            throw new RuntimeException("Unexpected type for intent response code: " + o.getClass().getName());
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (BuildConfig.DEBUG)
            Log.d(DonateActivity.class.getSimpleName(), "onActivityResult(" + requestCode + "," + resultCode + "," + data + ")");
        if (requestCode != RC_REQUEST) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        if (data == null) {
            Log.e(DonateActivity.class.getSimpleName(), "Purchase: Null intent");
            // EasyTracker.getTracker().sendEvent("Donate", "Purchase null intent", purchasedSku, -1L);
            return;
        }
        final int responseCode = getResponseCodeFromIntent(data);
        final String purchaseData = data.getStringExtra("INAPP_PURCHASE_DATA");
        final String dataSignature = data.getStringExtra("INAPP_DATA_SIGNATURE");
        if (resultCode == Activity.RESULT_OK && responseCode == 0) {
            if (purchaseData == null || dataSignature == null) {
                Log.e(DonateActivity.class.getSimpleName(), "Purchase: Invalid data fields");
                // EasyTracker.getTracker().sendEvent("Donate", "Purchase invalid data fields", purchasedSku, -1L);
                return;
            }
            Purchase purchase;
            try {
                purchase = new Purchase(ITEM_TYPE_INAPP, purchaseData, dataSignature);
                final String sku = purchase.getSku();
                // Verify signature
                if (!Security.verifyPurchase(publicKey, purchaseData, dataSignature)) {
                    Log.e(DonateActivity.class.getSimpleName(), "Purchase: Signature verification failed " + sku);
                    // EasyTracker.getTracker().sendEvent("Donate", "Purchase signature verification failed", sku, -1L);
                    return;
                }
            } catch (final JSONException e) {
                Log.e(DonateActivity.class.getSimpleName(), "Purchase: Parsing error", e);
                // EasyTracker.getTracker().sendEvent("Donate", "Purchase parsing error", purchasedSku, -1L);
                return;
            }
            new ConsumeAsyncTask(mService, true).execute(purchase);
        } else if (resultCode == Activity.RESULT_OK) {
            Log.e(DonateActivity.class.getSimpleName(), "Purchase: bad response " + responseCode);
            // EasyTracker.getTracker().sendEvent("Donate", "Purchase bad response " + responseCode, purchasedSku, -1L);
        } else if (resultCode == Activity.RESULT_CANCELED) {
            if (BuildConfig.DEBUG)
                Log.d(DonateActivity.class.getSimpleName(), "Purchase: canceled");
            // EasyTracker.getTracker().sendEvent("Donate", "Canceled", purchasedSku, 0L);
        } else {
            Log.w(DonateActivity.class.getSimpleName(), "Purchase: Unknown response");
            // EasyTracker.getTracker().sendEvent("Donate", "Purchase unknown response", purchasedSku, -1L);
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Set up SKUs
        final ArrayList<String> allSkus = new ArrayList<String>();
        if (BuildConfig.DEBUG) {
            allSkus.add("android.test.purchased");
            allSkus.add("android.test.canceled");
            allSkus.add("android.test.refunded");
            allSkus.add("android.test.item_unavailable");
        }
        final String[] skuArray = getResources().getStringArray(R.array.donate_in_app_sku_array);
        allSkus.addAll(Arrays.asList(skuArray));
        skus = allSkus.toArray(new String[allSkus.size()]);
        final int[] skuPriceArray = getResources().getIntArray(R.array.donate_in_app_price_array);
        for (int h = 0; h < skuPriceArray.length; h++)
            skuPrices.put(skuArray[h], (long) skuPriceArray[h]);
        // Set up the UI
        setContentView(R.layout.activity_donate);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        final Button paypal_button = (Button) findViewById(R.id.paypal_button);
        paypal_button.setOnClickListener(new View.OnClickListener() {
            /**
             * Donate button with PayPal by opening browser with defined URL For possible parameters see:
             * https://cms.paypal.com/us/cgi-bin/?cmd=_render -content&content_ID=
             * developer/e_howto_html_Appx_websitestandard_htmlvariables
             *
             * @param v
             *            View that was clicked
             */
            @Override
            public void onClick(final View v) {
                if (BuildConfig.DEBUG)
                    Log.d(DonateActivity.class.getSimpleName(), "Clicked Paypal");
                // EasyTracker.getTracker().sendEvent("Donate", "Paypal", "", 1L);
                final Uri.Builder uriBuilder = new Uri.Builder();
                uriBuilder.scheme("https").authority("www.paypal.com").path("cgi-bin/webscr");
                uriBuilder.appendQueryParameter("cmd", "_donations");
                uriBuilder.appendQueryParameter("business", "ian.hannibal.lake@gmail.com");
                uriBuilder.appendQueryParameter("lc", "US");
                uriBuilder.appendQueryParameter("item_name", "Local Storage Donation");
                uriBuilder.appendQueryParameter("no_note", "1");
                uriBuilder.appendQueryParameter("no_shipping", "1");
                uriBuilder.appendQueryParameter("currency_code", "USD");
                final Uri payPalUri = uriBuilder.build();
                // Start your favorite browser
                final Intent viewIntent = new Intent(Intent.ACTION_VIEW, payPalUri);
                startActivity(viewIntent);
                // Close this activity
                finish();
            }
        });
        final Button inAppButton = (Button) findViewById(R.id.donate__in_app_button);
        inAppButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                final Spinner inAppSpinner = (Spinner) findViewById(R.id.donate_in_app_spinner);
                final int selectedInAppAmount = inAppSpinner.getSelectedItemPosition();
                purchasedSku = skus[selectedInAppAmount];
                if (BuildConfig.DEBUG)
                    Log.d(DonateActivity.class.getSimpleName(), "Clicked " + purchasedSku);
                // EasyTracker.getTracker().sendEvent("Donate", "Click", purchasedSku, 0L);
                try {
                    final Bundle buyIntentBundle = mService.getBuyIntent(3, getPackageName(), purchasedSku,
                            ITEM_TYPE_INAPP, "");
                    final int response = getResponseCodeFromBundle(buyIntentBundle);
                    if (response != 0) {
                        Log.e(DonateActivity.class.getSimpleName(), "Buy bad response " + response);
                        // EasyTracker.getTracker().sendEvent("Donate", "Buy bad response " + response, purchasedSku, -1L);
                        return;
                    }
                    final PendingIntent pendingIntent = buyIntentBundle.getParcelable("BUY_INTENT");
                    startIntentSenderForResult(pendingIntent.getIntentSender(), RC_REQUEST, new Intent(), 0, 0, 0);
                } catch (final SendIntentException e) {
                    Log.e(DonateActivity.class.getSimpleName(), "Buy: Send intent failed", e);
                    // EasyTracker.getTracker().sendEvent("Donate", "Buy send intent failed", purchasedSku, -1L);
                } catch (final RemoteException e) {
                    Log.e(DonateActivity.class.getSimpleName(), "Buy: Remote exception", e);
                    // EasyTracker.getTracker().sendEvent("Donate", "Buy remote exception", purchasedSku, -1L);
                }
            }
        });
        // Start the In-App Billing process, only if on Froyo or higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
            mServiceConn = new ServiceConnection() {
                @Override
                public void onServiceConnected(final ComponentName name, final IBinder service) {
                    mService = IInAppBillingService.Stub.asInterface(service);
                    final String packageName = getPackageName();
                    try {
                        // check for in-app billing v3 support
                        final int response = mService.isBillingSupported(3, packageName, ITEM_TYPE_INAPP);
                        if (response == 0)
                            new InventoryQueryAsyncTask(mService).execute(skus);
                        else {
                            Log.w(DonateActivity.class.getSimpleName(), "Initialize: In app not supported");
                            // EasyTracker.getTracker().sendEvent("Donate", "Initialize in app not supported", "", -1L);
                        }
                    } catch (final RemoteException e) {
                        Log.e(DonateActivity.class.getSimpleName(), "Initialize: Remote exception", e);
                        // EasyTracker.getTracker().sendEvent("Donate", "Initialize remote exception", "", -1L);
                    }
                }

                @Override
                public void onServiceDisconnected(final ComponentName name) {
                    mService = null;
                }
            };
            final Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
            serviceIntent.setPackage("com.android.vending");
            if (!getPackageManager().queryIntentServices(serviceIntent, 0).isEmpty())
                // service available to handle that Intent
                bindService(serviceIntent, mServiceConn, Context.BIND_AUTO_CREATE);
            else {
                // no service available to handle that Intent
                Log.w(DonateActivity.class.getSimpleName(), "Initialize: Billing unavailable");
                // EasyTracker.getTracker().sendEvent("Donate", "Initialize billing unavailable", "", -1L);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mServiceConn != null) {
            try {
                unbindService(mServiceConn);
            } catch (final IllegalArgumentException e) {
                // Assume the service has already been unbinded, so only log that it happened
                Log.w(DonateActivity.class.getSimpleName(), "Error unbinding service", e);
            }
            mServiceConn = null;
            mService = null;
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull final Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        purchasedSku = savedInstanceState.containsKey(PURCHASED_SKU) ? savedInstanceState.getString(PURCHASED_SKU) : "";
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(PURCHASED_SKU, purchasedSku);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // EasyTracker.getInstance().activityStart(this);
        // EasyTracker.getTracker().sendView("Donate");
    }

    @Override
    protected void onStop() {
        super.onStop();
        // EasyTracker.getInstance().activityStop(this);
    }

    private class ConsumeAsyncTask extends AsyncTask<Purchase, Void, List<Purchase>> {
        private final boolean finishActivity;
        private final WeakReference<IInAppBillingService> mBillingService;

        ConsumeAsyncTask(final IInAppBillingService service, final boolean finishActivity) {
            mBillingService = new WeakReference<IInAppBillingService>(service);
            this.finishActivity = finishActivity;
        }

        @Override
        protected List<Purchase> doInBackground(final Purchase... purchases) {
            if (BuildConfig.DEBUG)
                Log.d(DonateActivity.class.getSimpleName(), "Starting Consume of " + Arrays.toString(purchases));
            final List<Purchase> consumedPurchases = new ArrayList<Purchase>();
            for (final Purchase purchase : purchases) {
                final String sku = purchase.getSku();
                try {
                    final String token = purchase.getToken();
                    if (TextUtils.isEmpty(token)) {
                        Log.e(DonateActivity.class.getSimpleName(), "Consume: Invalid token " + token);
                        // EasyTracker.getTracker().sendEvent("Donate", "Consume invalid token", sku, -1L);
                        break;
                    }
                    final IInAppBillingService service = mBillingService.get();
                    if (service == null) {
                        Log.w(DonateActivity.class.getSimpleName(), "Consume: Billing service is null");
                        break;
                    }
                    final int response = service.consumePurchase(3, getPackageName(), token);
                    if (response == 0)
                        consumedPurchases.add(purchase);
                    else {
                        Log.e(DonateActivity.class.getSimpleName(), "Consume: Bad response " + response);
                        // EasyTracker.getTracker().sendEvent("Donate", "Consume bad response " + response, sku, -1L);
                    }
                } catch (final RemoteException e) {
                    Log.e(DonateActivity.class.getSimpleName(), "Consume: Remote exception " + sku, e);
                    // EasyTracker.getTracker().sendEvent("Donate", "Consume remote exception", sku, -1L);
                }
            }
            return consumedPurchases;
        }

        @Override
        protected void onPostExecute(final List<Purchase> result) {
            if (result == null || result.isEmpty()) {
                Log.w(DonateActivity.class.getSimpleName(), "Consume: No purchases consumed");
                return;
            }
            for (final Purchase purchase : result) {
                final String sku = purchase.getSku();
                if (BuildConfig.DEBUG)
                    Log.d(DonateActivity.class.getSimpleName(), "Consume completed successfully " + sku);
                // EasyTracker.getTracker().sendEvent("Donate", "Purchased", sku, 1L);
                // final long purchasedPriceMicro = skuPrices.containsKey(sku) ? skuPrices.get(sku).longValue() : 0;
                // final String purchasedName = skuNames.containsKey(sku) ? skuNames.get(sku) : sku;
                // final Transaction transaction = new Transaction.Builder(purchase.getOrderId(), purchasedPriceMicro).setAffiliation("Google Play").build();
                // transaction.addItem(new Item.Builder(sku, purchasedName, purchasedPriceMicro, 1L).setProductCategory("Donation").build());
                // EasyTracker.getTracker().sendTransaction(transaction);
            }
            Toast.makeText(DonateActivity.this, R.string.donate_thank_you, Toast.LENGTH_LONG).show();
            if (finishActivity) {
                if (BuildConfig.DEBUG)
                    Log.d(DonateActivity.class.getSimpleName(), "Finishing Donate Activity");
                finish();
            }
        }
    }

    private class InventoryQueryAsyncTask extends AsyncTask<String, Void, Inventory> {
        private final WeakReference<IInAppBillingService> mBillingService;

        InventoryQueryAsyncTask(final IInAppBillingService service) {
            mBillingService = new WeakReference<IInAppBillingService>(service);
        }

        @Override
        protected Inventory doInBackground(final String... moreSkus) {
            try {
                final Inventory inv = new Inventory();
                if (BuildConfig.DEBUG)
                    Log.d(DonateActivity.class.getSimpleName(), "Starting query inventory");
                int r = queryPurchases(inv);
                if (r != 0)
                    return null;
                if (BuildConfig.DEBUG)
                    Log.d(DonateActivity.class.getSimpleName(), "Starting sku details query");
                r = querySkuDetails(inv, moreSkus);
                if (r != 0)
                    return null;
                return inv;
            } catch (final RemoteException e) {
                Log.e(DonateActivity.class.getSimpleName(), "Inventory: Remote exception", e);
                // EasyTracker.getTracker().sendEvent("Donate", "Inventory remote exception", "", -1L);
            } catch (final JSONException e) {
                Log.e(DonateActivity.class.getSimpleName(), "Inventory: Parsing error", e);
                // EasyTracker.getTracker().sendEvent("Donate", "Inventory parsing error", "", -1L);
            }
            return null;
        }

        @Override
        protected void onPostExecute(final Inventory inv) {
            if (BuildConfig.DEBUG)
                Log.d(DonateActivity.class.getSimpleName(), "Inventory Returned: " + inv);
            // If we failed to get the inventory, then leave the in-app billing UI hidden
            if (inv == null)
                return;
            // Make sure we've consumed any previous purchases
            final List<Purchase> purchases = inv.getAllPurchases();
            if (!purchases.isEmpty()) {
                final IInAppBillingService service = mBillingService.get();
                if (service != null)
                    new ConsumeAsyncTask(service, false).execute(purchases.toArray(new Purchase[purchases.size()]));
                else
                    Log.w(DonateActivity.class.getSimpleName(), "Inventory: Billing service is null");
            }
            final String[] inAppName = new String[skus.length];
            for (int h = 0; h < skus.length; h++) {
                final String currentSku = skus[h];
                final SkuDetails sku = inv.getSkuDetails(currentSku);
                skuNames.put(currentSku, sku.getTitle());
                inAppName[h] = sku.getDescription() + " (" + sku.getPrice() + ")";
            }
            final Spinner inAppSpinner = (Spinner) findViewById(R.id.donate_in_app_spinner);
            final ArrayAdapter<String> adapter = new ArrayAdapter<String>(DonateActivity.this,
                    android.R.layout.simple_spinner_item, inAppName);
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            // Apply the adapter to the spinner
            inAppSpinner.setAdapter(adapter);
            // And finally show the In-App Billing UI
            final View inAppLayout = findViewById(R.id.in_app_layout);
            inAppLayout.setVisibility(View.VISIBLE);
        }

        int queryPurchases(final Inventory inv) throws JSONException, RemoteException {
            // Query purchases
            boolean verificationFailed = false;
            String continueToken = null;
            do {
                final IInAppBillingService service = mBillingService.get();
                if (service == null) {
                    Log.w(DonateActivity.class.getSimpleName(), "Purchases: Billing service is null");
                    return -1;
                }
                final Bundle ownedItems = service.getPurchases(3, getPackageName(), ITEM_TYPE_INAPP, continueToken);
                final int response = getResponseCodeFromBundle(ownedItems);
                if (response != 0) {
                    Log.e(DonateActivity.class.getSimpleName(), "Purchases: Bad response " + response);
                    // EasyTracker.getTracker().sendEvent("Donate", "Purchases bad response " + response, "", -1L);
                    return response;
                }
                if (!ownedItems.containsKey(RESPONSE_INAPP_ITEM_LIST)
                        || !ownedItems.containsKey(RESPONSE_INAPP_PURCHASE_DATA_LIST)
                        || !ownedItems.containsKey(RESPONSE_INAPP_SIGNATURE_LIST)) {
                    Log.e(DonateActivity.class.getSimpleName(), "Purchases: Invalid data");
                    // EasyTracker.getTracker().sendEvent("Donate", "Purchases invalid data", "", -1L);
                    return -1;
                }
                final ArrayList<String> purchaseDataList = ownedItems
                        .getStringArrayList(RESPONSE_INAPP_PURCHASE_DATA_LIST);
                final ArrayList<String> signatureList = ownedItems.getStringArrayList(RESPONSE_INAPP_SIGNATURE_LIST);
                for (int i = 0; i < purchaseDataList.size(); ++i) {
                    final String purchaseData = purchaseDataList.get(i);
                    final String signature = signatureList.get(i);
                    final Purchase purchase = new Purchase(ITEM_TYPE_INAPP, purchaseData, signature);
                    if (purchase.getSku().startsWith("android.test") || Security.verifyPurchase(publicKey,
                            purchaseData, signature)) {
                        // Record ownership and token
                        inv.addPurchase(purchase);
                    } else
                        verificationFailed = true;
                }
                continueToken = ownedItems.getString("INAPP_CONTINUATION_TOKEN");
            } while (!TextUtils.isEmpty(continueToken));
            return verificationFailed ? -1 : 0;
        }

        int querySkuDetails(final Inventory inv, final String[] moreSkus) throws RemoteException, JSONException {
            final ArrayList<String> skuList = new ArrayList<String>();
            skuList.addAll(inv.getAllOwnedSkus(ITEM_TYPE_INAPP));
            if (moreSkus != null)
                skuList.addAll(Arrays.asList(moreSkus));
            if (skuList.size() == 0)
                return 0;
            final Bundle querySkus = new Bundle();
            querySkus.putStringArrayList("ITEM_ID_LIST", skuList);
            final IInAppBillingService service = mBillingService.get();
            if (service == null) {
                Log.w(DonateActivity.class.getSimpleName(), "SkuDetails: Billing service is null");
                return -1;
            }
            final Bundle skuDetails = service.getSkuDetails(3, getPackageName(), ITEM_TYPE_INAPP, querySkus);
            if (!skuDetails.containsKey(RESPONSE_GET_SKU_DETAILS_LIST)) {
                final int response = getResponseCodeFromBundle(skuDetails);
                if (response != 0) {
                    Log.e(DonateActivity.class.getSimpleName(), "SkuDetails: Bad response " + response);
                    // EasyTracker.getTracker().sendEvent("Donate", "SkuDetails bad response " + response, "", -1L);
                    return response;
                }
                return -1;
            }
            final ArrayList<String> responseList = skuDetails.getStringArrayList(RESPONSE_GET_SKU_DETAILS_LIST);
            for (final String thisResponse : responseList) {
                final SkuDetails d = new SkuDetails(ITEM_TYPE_INAPP, thisResponse);
                inv.addSkuDetails(d);
            }
            return 0;
        }
    }
}
