/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.service.licensing;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;

import com.android.vending.billing.IInAppBillingService;
import com.meowsbox.vosp.common.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Minimal Google-specific licensing management. No piracy counter-measures have been implemented, we don't even verify the signature.<br>
 * Call the init() method and wait for the STATE callback within 30 seconds before calling any other methods.
 * The GoogleBillingHelper Activity is used to start the UI purchasing flow.
 * Created by dhon on 6/9/2017.
 */

public class GoogleBillingController implements ServiceConnection, ILicensingProvider {
    public static final boolean DEBUG = LicensingManager.DEBUG;
    public static final boolean DEV = LicensingManager.DEV;
    public static final String KEY_BUYINTENTBUNDLE = "key_bundle_intent";
    public static final String KEY_HELPER_ACTION = "key_helper_action";
    public static final String KEY_CONSUME_PURCHASE_TOKEN = "key_consume_token";
    public static final int VALUE_HELPER_ACTION_BUY_INAPP = 1;
    public static final int VALUE_HELPER_ACTION_BUY_SUBS = 2;
    private static final int CONNECT_ATTEMPT_MAX = 5;
    private static final int GOOGLE_BILLING_API_VERISON = 3;
    private static final String GOOGLE_BILLING_KEY_INAPP = "inapp";
    private static final String GOOGLE_BILLING_KEY_SUBS = "subs";
    private static final int CMD_GOOGLE_BILLING_SETUP = 1;
    private static final int CMD_GET_SKU_CATALOG = 2;
    private static final int CMD_GET_SKU_OWNED_ALL = 3;
    private static final int CMD_PUSH_INIT_STATE = 4;
    private static final int CMD_PUSH_PURCHASE_RESULT = 5;
    private static final int CMD_CONSUME_SKU = 6;
    private static final int CONNECTION_TIMEOUT = 30000;
    private static final String[] SKU_ALL_INAPP = {"com.meowsbox.vosp.prem_1"};
    private static final String[] SKU_ALL_SUBS = {"com.meowsbox.vosp.sub.patron_1"};
    public final String TAG = this.getClass().getName();
    private Logger gLog;
    private Context context;
    private boolean isBound = false;
    private IInAppBillingService bService;
    private boolean queueUnbind = false;
    private int countConnectAttempt = 0;
    private boolean isGoogleBillingSupportedInApp = false;
    private boolean isGoogleBillingSupportedSubs = false;
    private HashMap<String, Sku> skuData = new HashMap<>();
    private LinkedList<Sku> skuOwned = new LinkedList<>(); // all owned skus including subs
    private CommandThread commandLooper;
    private LinkedList<ILicensingProviderEvents> listeners = new LinkedList<>();
    private Timer connectionTimer = new Timer();
    private TimerTask watchDogTimerTask;
    private boolean isCatalogLoaded = false;
    private boolean isOwnedLoaded = false;
    private int state = 0;

    public GoogleBillingController(Context context, Logger logger) {
        this.context = context;
        if (logger == null) gLog = new Logger(LicensingManager.LOGGER_VERBOSITY);
        else gLog = logger;
        commandLooper = new CommandThread();
        commandLooper.start();
        commandLooper.waitUntilReady(10000);
    }

    /**
     * Start purchase activity for Patron subscription
     */
    @Override
    public void buyPatronSub() {
        try {
            Bundle buyIntentBundle = bService.getBuyIntent(GOOGLE_BILLING_API_VERISON, context.getPackageName(), SKU_ALL_SUBS[0], GOOGLE_BILLING_KEY_SUBS, null);
            Intent intent = new Intent(context, GoogleBillingHelper.class);
            buyIntentBundle.putInt(KEY_HELPER_ACTION, VALUE_HELPER_ACTION_BUY_SUBS);
            intent.putExtra(KEY_BUYINTENTBUNDLE, buyIntentBundle);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * Start purchase activity for Premium upgrade
     */
    @Override
    public void buyPremium() {
        try {
            Bundle buyIntentBundle = bService.getBuyIntent(GOOGLE_BILLING_API_VERISON, context.getPackageName(), SKU_ALL_INAPP[0], GOOGLE_BILLING_KEY_INAPP, null);
            Intent intent = new Intent(context, GoogleBillingHelper.class);
            buyIntentBundle.putInt(KEY_HELPER_ACTION, VALUE_HELPER_ACTION_BUY_INAPP);
            intent.putExtra(KEY_BUYINTENTBUNDLE, buyIntentBundle);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getState() {
        return state;
    }

    @Override
    public void init() {
        bindService();
    }

    /**
     * Return TRUE if the user is an active patron subscriber
     *
     * @return
     */
    @Override
    public boolean isPatron() {
        if (state != STATE_OK) {
            gLog.l(TAG, Logger.lvDebug, "Not Ready");
            return false;
        }
        final List<String> skuPremSubs = Arrays.asList(SKU_ALL_SUBS);
        synchronized (skuOwned) {
            for (Sku s : skuOwned) {
                if (skuPremSubs.contains(s.sku)) return true;
            }
        }
        return false;
    }

    /**
     * Get the premium purchase state.
     *
     * @return TRUE = premium or patron
     */
    @Override
    public boolean isPremium() {
        if (state != STATE_OK) {
            gLog.l(TAG, Logger.lvDebug, "Not Ready");
            return false;
        }
        final List<String> skuPremInapp = Arrays.asList(SKU_ALL_INAPP);
        final List<String> skuPremSubs = Arrays.asList(SKU_ALL_SUBS);
        synchronized (skuOwned) {
            for (Sku s : skuOwned) {
                if (skuPremInapp.contains(s.sku)) return true;
                if (skuPremSubs.contains(s.sku)) return true;
            }
        }
        return false;
    }

    @Override
    public void listenerAdd(ILicensingProviderEvents listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    @Override
    public void listenerRemove(ILicensingProviderEvents listener) {
        listeners.remove(listener);
    }

    public void onDestroy() {
        unbindService();
        commandLooper.mHandler.removeCallbacksAndMessages(null);
        commandLooper.mHandler.getLooper().quit();
    }

    /**
     * Directly consume purchase token
     */
    public int consumePurchaseToken(Bundle data) {
        if (data == null) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, "Bundle NULL");
            return 7; //BILLING_RESPONSE_RESULT_ERROR
        }
        try {
            final String purchaseToken = data.getString(KEY_CONSUME_PURCHASE_TOKEN);
            if (purchaseToken == null) return 7; //BILLING_RESPONSE_RESULT_ERROR
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, purchaseToken);
            final int result = bService.consumePurchase(GOOGLE_BILLING_API_VERISON, context.getPackageName(), purchaseToken);
            gLog.l(TAG, Logger.lvVerbose, "Result: " + result);
            return result;
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return 2; //BILLING_RESPONSE_RESULT_SERVICE_UNAVAILABLE
    }

    /**
     * @param data
     */
    public void onResult(Bundle data) {
        if (DEV) gLog.l(Logger.lvVerbose);
        int responseCode = data.getInt("RESPONSE_CODE", 0);
        if (DEBUG) gLog.l(TAG, Logger.lvDebug, "Purchase Result: " + responseCode);
//        String purchaseData = data.getString("INAPP_PURCHASE_DATA");
//        String dataSignature = data.getString("INAPP_DATA_SIGNATURE");
        if (responseCode != 0) { // not successful
            pushPurchaseResult(); // notify
            return;
        }

        // we ignore the returned purchase result and query Google Play Billing directly
        Message msg = new Message();
        msg.arg1 = CMD_GET_SKU_OWNED_ALL;
        commandLooper.mHandler.sendMessage(msg);
        msg = new Message();
        msg.arg1 = CMD_PUSH_PURCHASE_RESULT;
        commandLooper.mHandler.sendMessage(msg);

    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        if (DEV) gLog.l(Logger.lvVerbose);
        bService = IInAppBillingService.Stub.asInterface(service);
        isBound = true;
        connectionWatchDogCancel();
        countConnectAttempt = 0;
        Message m = new Message();
        m.arg1 = CMD_GOOGLE_BILLING_SETUP;
        commandLooper.mHandler.sendMessage(m);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        bService = null;
        isBound = false;
        if (DEV) gLog.l(Logger.lvVerbose);
        rebindService();
    }

    Sku getSkuDataPatron() {
        final Sku sku = skuData.get(SKU_ALL_SUBS[0]);
        if (sku != null)
            return sku;
        else return null;
    }

    Sku getSkuDataPremium() {
        final Sku sku = skuData.get(SKU_ALL_INAPP[0]);
        if (sku != null)
            return sku;
        else return null;
    }

    private void bindService() {
        queueUnbind = false;
        if (isBound) return;
        if (DEBUG) gLog.l(Logger.lvVerbose);

        Intent intent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
        intent.setPackage("com.android.vending");
        boolean bindResult = context.bindService(intent, this, Context.BIND_AUTO_CREATE);
        if (!bindResult) {
            gLog.l(TAG, Logger.lvDebug, "Google Billing Unavailable");
            connectionWatchDogCancel();
            pushState(STATE_NOT_AVAILABLE);
            return;
        }
        connectionWatchDogStart();
    }

    private void connectionWatchDogCancel() {
        if (watchDogTimerTask != null) watchDogTimerTask.cancel();
    }

    private void connectionWatchDogStart() {
        if (watchDogTimerTask != null) watchDogTimerTask.cancel();
        watchDogTimerTask = new TimerTask() {
            @Override
            public void run() {
                gLog.l(TAG, Logger.lvDebug, "Google Billing Unavailable - Connection Timeout");
                unbindService();
                pushState(STATE_NOT_AVAILABLE);
            }
        };
        connectionTimer.schedule(watchDogTimerTask, CONNECTION_TIMEOUT);
    }

    private void getSkuAvailable(String skuType, String[] skuInterested) {
        try {
            ArrayList<String> skuList = new ArrayList<>();
            skuList.addAll(Arrays.asList(skuInterested));
            Bundle querySkus = new Bundle();
            querySkus.putStringArrayList("ITEM_ID_LIST", skuList);
            Bundle response = bService.getSkuDetails(GOOGLE_BILLING_API_VERISON, context.getPackageName(), skuType, querySkus);
            if (response.getInt("RESPONSE_CODE") == 0) {
                ArrayList<String> responseList = response.getStringArrayList("DETAILS_LIST");
                if (responseList.size() == 0) {
                    gLog.l(TAG, Logger.lvDebug, "No SKU available to purchase: " + skuType);
                    return;
                }
                synchronized (skuData) { // convert JSONObject to native object
                    for (String thisResponse : responseList) {
                        JSONObject object = null;
                        try {
                            object = new JSONObject(thisResponse);
                            Sku sku = new Sku();
                            sku.sku = object.getString("productId");
                            sku.price = object.getString("price");
                            sku.title = object.getString("title");
                            sku.desc = object.getString("description");
                            sku.type = object.getString("type");
                            if (object.has("subscriptionPeriod"))
                                sku.subscriptionPeriod = object.getString("subscriptionPeriod");
                            skuData.put(sku.sku, sku);
                        } catch (JSONException e) {
                            if (DEBUG) e.printStackTrace();
                        }
                    }
                }
            } else {
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "getSkuAvailable: " + response.getInt("RESPONSE_CODE"));
            }
        } catch (RemoteException e) {
            if (DEBUG) e.printStackTrace();
        }
    }

    /**
     * Get all owned sku, recursive for more than 700 sku.
     */
    private void getSkuOwnedInapp(final String continuationToken) {
        try {
            Bundle response = bService.getPurchases(GOOGLE_BILLING_API_VERISON, context.getPackageName(), GOOGLE_BILLING_KEY_INAPP, null);
            if (response.getInt("RESPONSE_CODE") == 0) {

                ArrayList<String> purchaseDataList = response.getStringArrayList("INAPP_PURCHASE_DATA_LIST");
                ArrayList<String> signatureList = response.getStringArrayList("INAPP_DATA_SIGNATURE_LIST");
                String latestContinuationToken = response.getString("INAPP_CONTINUATION_TOKEN");

                synchronized (skuOwned) {
//                    if (continuationToken == null) skuOwned.clear(); // clear the owned list on first iteration
                    for (int i = 0; i < purchaseDataList.size(); ++i) {
                        try {
                            if (DEBUG) gLog.l(TAG, Logger.lvDebug, purchaseDataList.get(i));
                            JSONObject object = new JSONObject(purchaseDataList.get(i));
                            Sku sku = new Sku();
                            sku.sku = object.getString("productId");
                            sku.purchaseData = purchaseDataList.get(i);
                            sku.signature = signatureList.get(i);
                            skuOwned.add(sku);
                        } catch (JSONException e) {
                            if (DEBUG) e.printStackTrace();
                        }
                    }
                }
                if (latestContinuationToken != null)
                    getSkuOwnedInapp(latestContinuationToken); // recurse if response contains a continuationToken
            } else {
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "getSkuOwnedInapp: " + response.getInt("RESPONSE_CODE"));
            }
        } catch (RemoteException e) {
            if (DEBUG) e.printStackTrace();
        }
    }

    /**
     * Get all subbed sku, recursive for more than 700 sku.
     */
    private void getSkuOwnedSubs(final String continuationToken) {
        try {
            Bundle response = bService.getPurchases(GOOGLE_BILLING_API_VERISON, context.getPackageName(), GOOGLE_BILLING_KEY_SUBS, null);
            if (response.getInt("RESPONSE_CODE") == 0) {

                ArrayList<String> purchaseDataList = response.getStringArrayList("INAPP_PURCHASE_DATA_LIST");
                ArrayList<String> signatureList = response.getStringArrayList("INAPP_DATA_SIGNATURE_LIST");
                String latestContinuationToken = response.getString("INAPP_CONTINUATION_TOKEN");

                synchronized (skuOwned) {
//                    if (continuationToken == null) skuOwned.clear(); // clear the owned list on first iteration
                    for (int i = 0; i < purchaseDataList.size(); ++i) {
                        try {
                            if (DEBUG) gLog.l(TAG, Logger.lvDebug, purchaseDataList.get(i));
                            JSONObject object = new JSONObject(purchaseDataList.get(i));
                            Sku sku = new Sku();
                            sku.sku = object.getString("productId");
                            sku.purchaseData = purchaseDataList.get(i);
                            sku.signature = signatureList.get(i);
                            skuOwned.add(sku);
                        } catch (JSONException e) {
                            if (DEBUG) e.printStackTrace();
                        }
                    }
                }
                if (latestContinuationToken != null)
                    getSkuOwnedSubs(latestContinuationToken); // recurse if response contains a continuationToken
            } else {
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "getSkuOwnedInapp: " + response.getInt("RESPONSE_CODE"));
            }
        } catch (RemoteException e) {
            if (DEBUG) e.printStackTrace();
        }
    }

    private void googleBillingSetup() {
        try {
            isGoogleBillingSupportedInApp = bService.isBillingSupported(GOOGLE_BILLING_API_VERISON, context.getPackageName(), GOOGLE_BILLING_KEY_INAPP) == 0;
            gLog.l(TAG, Logger.lvVerbose, GOOGLE_BILLING_KEY_INAPP + " " + isGoogleBillingSupportedInApp);
            isGoogleBillingSupportedSubs = bService.isBillingSupported(GOOGLE_BILLING_API_VERISON, context.getPackageName(), GOOGLE_BILLING_KEY_SUBS) == 0;
            gLog.l(TAG, Logger.lvVerbose, GOOGLE_BILLING_KEY_SUBS + " " + isGoogleBillingSupportedSubs);
            if (isGoogleBillingSupportedInApp || isGoogleBillingSupportedSubs) {
                synchronized (skuData) {
                    skuData.clear();
                }
                synchronized (skuOwned) {
                    skuOwned.clear();
                }

                Message msg = new Message();

                if (DEBUG) {
                    msg.arg1 = CMD_CONSUME_SKU;
                    final Bundle bundle = new Bundle();
                    bundle.putString(KEY_CONSUME_PURCHASE_TOKEN, "inapp:com.meowsbox.vosp:android.test.purchased");
                    msg.setData(bundle);
                    commandLooper.mHandler.sendMessage(msg);
                    msg = new Message();
                }

                msg.arg1 = CMD_GET_SKU_CATALOG;
                commandLooper.mHandler.sendMessage(msg);
                msg = new Message();
                msg.arg1 = CMD_GET_SKU_OWNED_ALL;
                commandLooper.mHandler.sendMessage(msg);
                msg = new Message();
                msg.arg1 = CMD_PUSH_INIT_STATE;
                commandLooper.mHandler.sendMessage(msg);
            } else {
                pushState(STATE_NOT_AVAILABLE);
            }
        } catch (Exception e) {
            if (DEBUG) e.printStackTrace();
            pushState(STATE_NOT_AVAILABLE);
        }
    }

    /**
     * Push purchase result to {@link ILicensingProviderEvents} subscribers
     */
    private void pushPurchaseResult() {
        LinkedList<ILicensingProviderEvents> gpcNull = null;
        for (ILicensingProviderEvents g : listeners) {
            if (g != null) g.onPurchaseResult();
            else {
                if (gpcNull == null) gpcNull = new LinkedList<>();
                gpcNull.add(g);
            }
        }
        if (gpcNull != null) for (ILicensingProviderEvents g : gpcNull)
            listeners.remove(g);
    }

    /**
     * Push state to {@link ILicensingProviderEvents} subscribers
     *
     * @param state
     */
    private void pushState(int state) {
        this.state = state;
        LinkedList<ILicensingProviderEvents> gpcNull = null;
        for (ILicensingProviderEvents g : listeners) {
            if (g != null) g.onInitFinished(state);
            else {
                if (gpcNull == null) gpcNull = new LinkedList<>();
                gpcNull.add(g);
            }
        }
        if (gpcNull != null) for (ILicensingProviderEvents g : gpcNull)
            listeners.remove(g);
    }

    private void rebindService() {
        if (queueUnbind) return;
        if (DEV) gLog.l(Logger.lvVerbose);
        countConnectAttempt++;
        if (countConnectAttempt > CONNECT_ATTEMPT_MAX) {
            gLog.l(TAG, Logger.lvVerbose, "Google Billing Unavailable - connection attempts exceeded");
            return;
        }
    }

    private void unbindService() {
        if (DEV) gLog.l(Logger.lvVerbose);
        queueUnbind = true;
        if (bService != null && context != null) context.unbindService(this);
    }

    class CommandThread extends Thread {
        public volatile Handler mHandler = null;

        @Override
        public void run() {
            Looper.prepare();
            mHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.arg1) {
                        case CMD_GOOGLE_BILLING_SETUP:
                            googleBillingSetup();
                            break;
                        case CMD_GET_SKU_CATALOG:
                            isCatalogLoaded = false;
                            getSkuAvailable(GOOGLE_BILLING_KEY_INAPP, SKU_ALL_INAPP);
                            getSkuAvailable(GOOGLE_BILLING_KEY_SUBS, SKU_ALL_SUBS);
                            isCatalogLoaded = true;
                            break;
                        case CMD_GET_SKU_OWNED_ALL:
                            isOwnedLoaded = false;
                            synchronized (skuOwned) {
                                skuOwned.clear();
                            }
                            getSkuOwnedInapp(null);
                            getSkuOwnedSubs(null);
                            isOwnedLoaded = true;
                            break;
                        case CMD_PUSH_INIT_STATE:
                            if (isCatalogLoaded && isOwnedLoaded) pushState(STATE_OK);
                            break;
                        case CMD_PUSH_PURCHASE_RESULT:
                            pushPurchaseResult();
                            break;
                        case CMD_CONSUME_SKU:
                            consumePurchaseToken(msg.getData());
                            break;
                        default:
                            if (DEBUG) gLog.l(TAG, Logger.lvDebug, "Unhandled Command");

                            break;
                    }
                }
            };
            Looper.loop();
        }

        public void waitUntilReady(long timelimit) {
            long start = System.currentTimeMillis();
            while (mHandler == null) {
                if (System.currentTimeMillis() - start > timelimit) return;
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    if (DEBUG) e.printStackTrace();
                }
            }
        }
    }


}
