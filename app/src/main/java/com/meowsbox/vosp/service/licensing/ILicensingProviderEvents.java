/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.service.licensing;

/**
 * Callbacks for {@link ILicensingProvider} event implementations
 * Created by dhon on 8/16/2017.
 */

public interface ILicensingProviderEvents {
    /**
     * Notify the {@link LicensingManager} that provider has completed initialization
     *
     * @param state
     */
    void onInitFinished(int state);

    /**
     * Notify {@link LicensingManager} that results of a purchase flow has been received.
     */
    void onPurchaseResult();

    /**
     * Notifies {@link LicensingManager} of the current state of the Licensing Provider.
     *
     * @param state
     */
    void onState(int state);
}
