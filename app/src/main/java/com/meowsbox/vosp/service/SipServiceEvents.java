/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.service;

/**
 * Created by dhon on 11/9/2016.
 */

public interface SipServiceEvents {

    /**
     * Call by the service when Call Mute state has changed.
     *
     * @param pjsipCallId
     */
    void onCallMuteStateChanged(int pjsipCallId);

    /**
     * Called by service on Call change of state. Be sure to retain a reference to the Call as once the call is disconnected it can no longer be retrieved.
     *
     * @param pjsipCallId
     */
    void onCallStateChanged(int pjsipCallId);

    /**
     * Called by service on change of Sip Stack state.
     *
     * @param stackState
     */
    void onStackStateChanged(int stackState);
}
