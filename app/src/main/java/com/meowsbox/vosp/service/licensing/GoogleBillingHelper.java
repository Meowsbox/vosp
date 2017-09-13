/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.service.licensing;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.Window;

import com.meowsbox.vosp.DialerApplication;
import com.meowsbox.vosp.IRemoteSipService;
import com.meowsbox.vosp.ServiceBindingController;
import com.meowsbox.vosp.common.Logger;

/**
 * A simple Activity to start the UI purchase flow and direct the results back to the GoogleBillingController via SipService
 */
public class GoogleBillingHelper extends Activity implements ServiceBindingController.ServiceConnectionEvents {
    public final static boolean DEBUG = LicensingManager.DEBUG;
    public static final boolean DEV = LicensingManager.DEV;
    private final static int BILLING_RESPONSE_CODE = 4443;
    static Logger gLog = new Logger(DialerApplication.LOGGER_VERBOSITY);
    public final String TAG = this.getClass().getName();
    private ServiceBindingController mServiceController = null;
    private IRemoteSipService sipService = null;
    private Bundle buyIntentBundle;

    @Override
    public void onServiceConnectTimeout() {
        finish();
    }

    @Override
    public void onServiceConnected(IRemoteSipService remoteService) {
        if (DEV) gLog.l(Logger.lvVerbose);
        sipService = remoteService;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                handleIntent();
            }
        });
    }

    @Override
    public void onServiceDisconnected() {
        if (DEV) gLog.l(Logger.lvVerbose);
        sipService = null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        requestWindowFeature(Window.FEATURE_NO_TITLE);


        final Intent intent = getIntent();
        if (intent == null) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, "Intent NULL");
            finish();
            return;
        }
        Bundle extras = intent.getExtras();
        if (extras == null) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, "Extras NULL");
            finish();
            return;
        }
        buyIntentBundle = extras.getBundle(GoogleBillingController.KEY_BUYINTENTBUNDLE);
        if (buyIntentBundle == null) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, "Bundle NULL");
            finish();
            return;
        }
        mServiceController = new ServiceBindingController(gLog, this, this);
        mServiceController.bindToService();

    }

    @Override
    protected void onResume() {
        super.onResume();
        mServiceController.bindToService();

    }

    @Override
    protected void onDestroy() {
        mServiceController.unbindToService();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (DEV) gLog.l(Logger.lvVerbose);
        switch (requestCode) {
            case BILLING_RESPONSE_CODE:
                try {
                    if (sipService != null) sipService.googleBillingOnResult(data.getExtras());
                    else gLog.l(TAG, Logger.lvError, "SipService NULL, billing result lost!");
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                finish();
                break;
            default:
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "Unknown requestCode: " + requestCode);
                super.onActivityResult(requestCode, resultCode, data);
                finish();
                break;
        }
    }

    private void handleIntent() {
        if (DEV) gLog.l(Logger.lvVerbose);
        int action = buyIntentBundle.getInt(GoogleBillingController.KEY_HELPER_ACTION);
        switch (action) {
            case GoogleBillingController.VALUE_HELPER_ACTION_BUY_INAPP:
            case GoogleBillingController.VALUE_HELPER_ACTION_BUY_SUBS:
                int response_code = buyIntentBundle.getInt("RESPONSE_CODE");
                if (response_code != 0) {
                    gLog.l(TAG, Logger.lvDebug, "Billing Failed: " + response_code);
                    finish();
                    return;
                }
                PendingIntent pendingIntent = buyIntentBundle.getParcelable("BUY_INTENT");
                try {
                    startIntentSenderForResult(pendingIntent.getIntentSender(), BILLING_RESPONSE_CODE, new Intent(), 0, 0, 0);
                } catch (IntentSender.SendIntentException e) {
                    e.printStackTrace();
                }
                break;
            default:
                gLog.l(TAG, Logger.lvDebug, "Unhandled helper action: " + action);
                finish();
                break;
        }
    }

}
