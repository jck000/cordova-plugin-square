/*
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
*/

package com.squareup.plugin.square;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.util.Log;

import com.squareup.sdk.register.ChargeRequest;
import com.squareup.sdk.register.CurrencyCode;
import com.squareup.sdk.register.RegisterClient;
import com.squareup.sdk.register.RegisterSdk;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
* This class exposes methods in Cordova that can be called from JavaScript.
*/
public class Square extends CordovaPlugin {
    /** Common tag used for logging statements. */
    private static final String LOGTAG = "Square";

    /** Cordova Actions. */
    private static final String ACTION_SET_OPTIONS = "setOptions";
    
    private static final String ACTION_REQUEST_CHARGE = "requestCharge";

    /** Options */
    private static final String OPT_APPLICATION_ID = "applicationId";
    private static final String OPT_AMOUNT = "amount";
    private static final String OPT_CURRENCY = "currency";
    private static final String OPT_TENDERS = "tenders";
    private static final String OPT_LOCATION_ID = "locationId";
    private static final String OPT_TIMEOUT = "timeout";
    private static final String OPT_NOTE = "note";
    private static final String OPT_METADATA = "metadata";

    /** Option Defaults */
    private static final String DEFAULT_CURRENCY = "USD";
    private static final int DEFAULT_TIMEOUT = 3500;

    private String applicationId;
    private String currency = DEFAULT_CURRENCY;
    private JSONArray tenders;
    private String locationId;
    private int timeout = DEFAULT_TIMEOUT;
    private String note;
    private String metadata;

    private static final Random RANDOM = new Random();
    private RegisterClient registerClient;
    private CallbackContext requestCallback;
    private int requestCode = Math.abs(RANDOM.nextInt());

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action            The action to execute.
     * @param args              JSONArry of arguments for the plugin.
     * @param callbackContext   The callback context from which we were invoked.
     */
    @Override
    public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        PluginResult result;

        if (ACTION_SET_OPTIONS.equals(action)) {
            JSONObject options = args.optJSONObject(0);
            result = executeSetOptions(options, callbackContext);
        } else if (ACTION_REQUEST_CHARGE.equals(action)) {
            JSONObject options = args.optJSONObject(0);
            result = executeRequestCharge(options, callbackContext);
        } else {
            Log.d(LOGTAG, String.format("Invalid action passed: %s", action));
            result = new PluginResult(PluginResult.Status.INVALID_ACTION);
        }
        
        if(result != null) callbackContext.sendPluginResult(result);
        
        return true;
    }

    @Override public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.w(LOGTAG, "onActivityResult");
        if (this.requestCode == requestCode) {

            // Handle the rare situation where the Square Register app was uninstalled during the request.
            if (data == null) {
                return;
            }

            if(this.requestCallback == null) {
                return;
            }

            try {
                JSONObject parameter = new JSONObject();
                if (resultCode == Activity.RESULT_OK) {
                    ChargeRequest.Success success = registerClient.parseChargeSuccess(data);

                    // Retrieve the transaction's client-generated and server-generated IDs.
                    // Descriptions of these fields are available in the SDK's javadoc.
                    parameter.putOpt("clientTransactionId", success.clientTransactionId);
                    parameter.putOpt("serverTransactionId", success.serverTransactionId);

                    // This matches the value you provided for requestMetadata in your
                    // original request.
                    parameter.putOpt("metadata", success.requestMetadata);

                    // Persist and use the transaction IDs however you choose
                    requestCallback.success(parameter);
                } else {
                    ChargeRequest.Error error = registerClient.parseChargeError(data);

                    // Get the type of error that occurred
                    parameter.putOpt("errorCode", error.code.toString());

                    // Get the debug string that describes the error
                    parameter.putOpt("errorDescription", error.debugDescription);

                    parameter.putOpt("metadata", error.requestMetadata);

                    // Use the error code and description to debug the error.
                    requestCallback.error(parameter);
                }
            } catch (JSONException e) {
                Log.w(LOGTAG, String.format("Caught JSON Exception: %s", e.getMessage()));
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
    
    private PluginResult executeSetOptions(final JSONObject options, final CallbackContext callbackContext) {
        Log.w(LOGTAG, "executeSetOptions");

        this.setOptions(options);

        return new PluginResult(PluginResult.Status.OK);
    }

    private void setOptions(final JSONObject options) {
        if(options == null) return;

        if(options.has(OPT_APPLICATION_ID)) this.applicationId = options.optString(OPT_APPLICATION_ID);
        if(options.has(OPT_CURRENCY)) this.currency = options.optString(OPT_CURRENCY);
        if(options.has(OPT_TENDERS)) this.tenders  = options.optJSONArray(OPT_TENDERS);
        if(options.has(OPT_LOCATION_ID)) this.locationId = options.optString(OPT_LOCATION_ID);
        if(options.has(OPT_TIMEOUT)) this.timeout  = options.optInt(OPT_TIMEOUT);
        if(options.has(OPT_NOTE)) this.note = options.optString(OPT_NOTE);
        if(options.has(OPT_METADATA)) this.metadata = options.optString(OPT_METADATA);
    }
    
    private PluginResult executeRequestCharge(final JSONObject options, final CallbackContext callbackContext) {
        Log.w(LOGTAG, "executeRequestCharge");

        // The amount in cents (100 corresponds to $1.00)
        int amount;
        if(options.has(OPT_AMOUNT)) {
            amount = options.optInt(OPT_AMOUNT);
        } else {
            return new PluginResult(PluginResult.Status.ERROR, "amount in cents is required for charging with Square");
        }

        this.requestCallback = callbackContext;

        this.setOptions(options);

        // Replace "applicationId" with your Square-assigned application ID,
        // available from the application dashboard.
        registerClient = RegisterSdk.createClient(cordova.getActivity(), applicationId);

        // You specify all of the details of a Register API transaction in a ChargeRequest
        // object.
        ChargeRequest.Builder request = new ChargeRequest.Builder(amount, CurrencyCode.valueOf(currency))
                .autoReturn(timeout, TimeUnit.MILLISECONDS);

        if (this.note != null) {
            request = request.note(note);
        }

        if (this.metadata != null) {
            request = request.requestMetadata(metadata);
        }

        if (this.tenders != null) {
            Set<ChargeRequest.TenderType> tenderTypes = new LinkedHashSet<ChargeRequest.TenderType>();
            for(int i = 0; i < tenders.length(); i++) {
                tenderTypes.add(ChargeRequest.TenderType.valueOf(tenders.optString(i).toUpperCase()));
            }
            request = request.restrictTendersTo(tenderTypes);
        }

        if (this.locationId != null) {
           request = request.enforceBusinessLocation(locationId);
        }

        try {
            // This method generates an intent that includes the details of the transaction to process.
            Intent chargeIntent = registerClient.createChargeIntent(request.build());

            // This fires off the request to begin the app switch.
            cordova.startActivityForResult(this, chargeIntent, requestCode);
        } catch (ActivityNotFoundException e) {

            // This opens Square Register's Google Play Store listing if Square Register
            // doesn't appear to be installed on the device.
            registerClient.openRegisterPlayStoreListing();
        }

        return null;
    }
}
