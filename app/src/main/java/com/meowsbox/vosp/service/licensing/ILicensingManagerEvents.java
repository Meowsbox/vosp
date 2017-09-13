/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.service.licensing;

/**
 * Callbacks for {@link LicensingManager} event subscribers
 * Created by dhon on 8/16/2017.
 */

public interface ILicensingManagerEvents {
    /**
     * Notify listener that results of a purchase flow has been received.
     */
    void onPurchaseResult();

    /**
     * Notifies listener of the current state of the Licensing Manager.
     *
     * @param state
     */
    void onState(int state);
}
