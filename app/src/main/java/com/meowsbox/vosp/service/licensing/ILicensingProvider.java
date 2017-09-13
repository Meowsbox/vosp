/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.service.licensing;

import com.meowsbox.vosp.common.Logger;

/**
 * Licensing provider minimal interface
 * Created by dhon on 8/16/2017.
 */

public interface ILicensingProvider {
    int STATE_PRE_INIT = 0;
    int STATE_OK = 1;
    int STATE_NOT_AVAILABLE = 2;

    /**
     * Start purchasing flow
     */
    void buyPatronSub();

    /**
     * Start purchasing flow
     */
    void buyPremium();

    /**
     * Get provider current state
     *
     * @return
     */
    int getState();

    /**
     * Start provider initialization. Call and wait for {@link ILicensingProviderEvents} onInitFinished.
     */
    void init();

    /**
     * Return TRUE if patron subscription is active
     *
     * @return
     */
    boolean isPatron();

    /**
     * Return TRUE if premium sku is owned
     *
     * @return
     */
    boolean isPremium();

    /**
     * Add {@link ILicensingProviderEvents} listener to provider
     *
     * @param listener
     */
    void listenerAdd(ILicensingProviderEvents listener);

    /**
     * Remove {@link ILicensingProviderEvents} listener to provider
     *
     * @param listener
     */
    void listenerRemove(ILicensingProviderEvents listener);

    /**
     * Perform destruction and cleanup routines. Provider is no longer valid and no further method calls should be made.
     */
    void onDestroy();

}
