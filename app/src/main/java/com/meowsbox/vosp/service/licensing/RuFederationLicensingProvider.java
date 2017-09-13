/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.service.licensing;

import android.content.Context;

import com.meowsbox.vosp.common.Logger;

import java.util.Locale;

/**
 * Premium License for Russian device language users.
 * State and flag are immediately available after calling init. {@link ILicensingProviderEvents} is not implemented by this provider.
 * Created by dhon on 8/16/2017.
 */

public class RuFederationLicensingProvider implements ILicensingProvider {
    public static final boolean DEBUG = LicensingManager.DEBUG;
    public static final boolean DEV = LicensingManager.DEV;
    public final String TAG = this.getClass().getName();
    private int state = STATE_PRE_INIT;
    private boolean isPatron = false;
    private boolean isPremium = false;
    private Logger gLog;
    private Context mcContext;

    RuFederationLicensingProvider(Context context, Logger logger) {
        if (logger == null) gLog = new Logger(LicensingManager.LOGGER_VERBOSITY);
        else gLog = logger;
        mcContext = context;
    }

    @Override
    public void buyPatronSub() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void buyPremium() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getState() {
        return state;
    }

    @Override
    public void init() {
        final String language = Locale.getDefault().getLanguage();

        if (language.contains("ru")) {
            isPremium |= true;
                gLog.l(TAG, Logger.lvVerbose, "Версия для бывшего СССР. Купите если понравилось!");
        }
        state = isPremium ? STATE_OK : STATE_NOT_AVAILABLE;
    }

    @Override
    public boolean isPatron() {
        return isPatron;
    }

    @Override
    public boolean isPremium() {
        return isPremium;
    }

    @Override
    public void listenerAdd(ILicensingProviderEvents listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void listenerRemove(ILicensingProviderEvents listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onDestroy() {

    }


}
