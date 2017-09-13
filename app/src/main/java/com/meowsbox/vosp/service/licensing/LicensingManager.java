/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.service.licensing;

import android.content.Context;
import android.os.Bundle;

import com.meowsbox.vosp.common.Logger;
import com.meowsbox.vosp.service.SipService;

import java.util.LinkedList;

/**
 * Unified app licensing API across various licensing providers.
 * Usage: Construct instance, add listener, call init(), wait for STATE_OK before calling any methods.
 * <p>
 * Created by dhon on 6/19/2017.
 */

public class LicensingManager {
    public static final int STATE_PRE_INIT = 0; // new construction
    public static final int STATE_INIT_PROGRESS = 1; // init called and in progress
    public static final int STATE_OK = 2; // init complete, ready
    public static final int STATE_NOT_AVAILABLE = 3; // no licensing available at all, unusual.
    public static final int RESULT_OK = 0;
    public static final int RESULT_ERROR = 1;
    public static final int RESULT_ERROR_ALREADY_OWNED = 2;
    public static final String KEY_PATRON = "key_patron";
    public static final String KEY_PREM = "key_prem";
    public static final String KEY_STATE = "key_state";
    public static final boolean DEBUG = SipService.DEBUG;
    public static final boolean DEV = SipService.DEV;
    public final static int LOGGER_VERBOSITY = SipService.LOGGER_VERBOSITY;
    public final String TAG = this.getClass().getName();
    Context context;
    private int state = STATE_PRE_INIT;
    private LinkedList<ILicensingManagerEvents> lcb = new LinkedList<>();
    // Licensing Providers - should generify, however not time effective considering limited providers
    private GoogleBillingController lpGoogle;
    private RuFederationLicensingProvider lpRu;

    private ListenerGbc listenerGbc;
    private Logger gLog;

    public LicensingManager(Context context) {
        this.context = context;
        gLog = SipService.getInstance().getLoggerInstanceShared();
    }

    public int buyPatron() {
        if (state != STATE_OK) return RESULT_ERROR; // not init nor available
        if (isPatron()) return RESULT_ERROR_ALREADY_OWNED;
        if (lpGoogle != null && lpGoogle.getState() == ILicensingProvider.STATE_OK) {
            lpGoogle.buyPatronSub();
            return RESULT_OK;
        }
        return RESULT_ERROR;
    }

    public int buyPrem() {
        if (state != STATE_OK) return RESULT_ERROR; // not init nor available
        if (isPrem()) return RESULT_ERROR_ALREADY_OWNED;
        if (lpGoogle != null && lpGoogle.getState() == ILicensingProvider.STATE_OK) {
            lpGoogle.buyPremium();
            return RESULT_OK;
        }
        return RESULT_ERROR;
    }

    /**
     * Returns a {@link Bundle} containing the LicenseManager state and if {@link GoogleBillingController} is available and ready, the LicensingManager.KEY_PREM and LicensingManager.KEY_PATRON bundles.
     *
     * @return
     */
    public Bundle getSkuInfoBundle() {
        Bundle bundle = new Bundle();
        bundle.putInt(KEY_STATE, state);
        if (lpGoogle != null && lpGoogle.getState() == ILicensingProvider.STATE_OK) {
            bundle.putBundle(LicensingManager.KEY_PREM, lpGoogle.getSkuDataPremium().toBundle());
            bundle.putBundle(LicensingManager.KEY_PATRON, lpGoogle.getSkuDataPatron().toBundle());
        }
        return bundle;
    }

    /**
     * Returns a {@link Bundle} containing the LicenseManager state and if {@link GoogleBillingController} is available and ready, the isPrem and isPatron flags.
     *
     * @return
     */
    public Bundle getStateBundle() {
        Bundle bundle = new Bundle();
        bundle.putInt(KEY_STATE, state);
        bundle.putBoolean(LicensingManager.KEY_PREM, isPrem());
        bundle.putBoolean(LicensingManager.KEY_PATRON, isPatron());
        return bundle;
    }

    /**
     * Google Billing specific method for purchase-flow return data
     *
     * @param data
     */
    public void googleBillingOnResult(Bundle data) {
        if (lpGoogle != null && lpGoogle.getState() == ILicensingProvider.STATE_OK) lpGoogle.onResult(data);
        else gLog.l(TAG, Logger.lvDebug, "GBC not available or null: onResult discarded");
    }

    public void init() {
        if (state != STATE_PRE_INIT) return; // init already called or in progress
        pushState(STATE_INIT_PROGRESS);
        lpGoogle = new GoogleBillingController(context, gLog);
        lpGoogle.listenerAdd(listenerGbc = new ListenerGbc());
        lpGoogle.init();

        lpRu = new RuFederationLicensingProvider(context, gLog);
        lpRu.init();

    }

    public boolean isPatron() {
        boolean result = false;
        if (lpGoogle != null && lpGoogle.getState() == ILicensingProvider.STATE_OK) result |= lpGoogle.isPatron();
        if (lpRu != null && lpRu.getState() == ILicensingProvider.STATE_OK) result |= lpRu.isPatron();
        return result;
    }

    public boolean isPrem() {
        boolean result = false;
        if (lpGoogle != null && lpGoogle.getState() == ILicensingProvider.STATE_OK) result |= lpGoogle.isPremium();
        if (lpRu != null && lpRu.getState() == ILicensingProvider.STATE_OK) result |= lpRu.isPremium();
        return result;
    }

    public void listenerAdd(ILicensingManagerEvents licensingCallbacks) {
        if (!lcb.contains(licensingCallbacks)) lcb.add(licensingCallbacks);
    }

    public void listenerRemove(ILicensingManagerEvents licensingCallbacks) {
        lcb.remove(licensingCallbacks);
    }

    public void onDestroy() {
        if (lpGoogle != null) {
            lpGoogle.listenerRemove(listenerGbc);
            lpGoogle.onDestroy();
            lcb.clear();
            state = STATE_PRE_INIT;
        }
    }

    private void pushPurchaseResult() {
        LinkedList<ILicensingManagerEvents> lcbNull = null;
        for (ILicensingManagerEvents cb : lcb) {
            if (cb != null) cb.onPurchaseResult();
            else {
                if (lcbNull == null) lcbNull = new LinkedList<>();
                lcbNull.add(cb);
            }
        }
        if (lcbNull != null) for (ILicensingManagerEvents cb : lcbNull)
            lcb.remove(cb);
    }

    /**
     * Update the {@link LicensingManager} state and push to all {@link ILicensingManagerEvents} subscribers.
     *
     * @param state
     */
    private void pushState(int state) {
        this.state = state;
        LinkedList<ILicensingManagerEvents> lcbNull = null;
        for (ILicensingManagerEvents cb : lcb) {
            if (cb != null) cb.onState(state);
            else {
                if (lcbNull == null) lcbNull = new LinkedList<>();
                lcbNull.add(cb);
            }
        }
        if (lcbNull != null) for (ILicensingManagerEvents cb : lcbNull)
            lcb.remove(cb);
    }

    /**
     * Provider listener for GoogleBillingController
     */
    private class ListenerGbc implements ILicensingProviderEvents {

        @Override
        public void onInitFinished(int state) {
            pushState(STATE_OK); // lpGoogle init completed, LicenseManager is ready to handle requests
        }

        @Override
        public void onPurchaseResult() {
            pushPurchaseResult();
        }

        @Override
        public void onState(int state) {

        }
    }

}
