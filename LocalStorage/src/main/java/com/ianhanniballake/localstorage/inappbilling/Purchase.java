/* Copyright (c) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ianhanniballake.localstorage.inappbilling;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents an in-app billing purchase.
 */
@SuppressWarnings("javadoc")
public class Purchase {
    String mDeveloperPayload;
    String mItemType; // ITEM_TYPE_INAPP or ITEM_TYPE_SUBS
    String mOrderId;
    String mOriginalJson;
    String mPackageName;
    int mPurchaseState;
    long mPurchaseTime;
    String mSignature;
    String mSku;
    String mToken;

    public Purchase(final String itemType, final String jsonPurchaseInfo, final String signature) throws JSONException {
        mItemType = itemType;
        mOriginalJson = jsonPurchaseInfo;
        final JSONObject o = new JSONObject(mOriginalJson);
        mOrderId = o.optString("orderId");
        mPackageName = o.optString("packageName");
        mSku = o.optString("productId");
        mPurchaseTime = o.optLong("purchaseTime");
        mPurchaseState = o.optInt("purchaseState");
        mDeveloperPayload = o.optString("developerPayload");
        mToken = o.optString("token", o.optString("purchaseToken"));
        mSignature = signature;
    }

    public String getDeveloperPayload() {
        return mDeveloperPayload;
    }

    public String getItemType() {
        return mItemType;
    }

    public String getOrderId() {
        return mOrderId;
    }

    public String getOriginalJson() {
        return mOriginalJson;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public int getPurchaseState() {
        return mPurchaseState;
    }

    public long getPurchaseTime() {
        return mPurchaseTime;
    }

    public String getSignature() {
        return mSignature;
    }

    public String getSku() {
        return mSku;
    }

    public String getToken() {
        return mToken;
    }

    @Override
    public String toString() {
        return "PurchaseInfo(type:" + mItemType + "):" + mOriginalJson;
    }
}
