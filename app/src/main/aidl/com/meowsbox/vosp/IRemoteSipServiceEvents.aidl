/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */
// IRemoteSipServiceEvents.aidl
package com.meowsbox.vosp;

// Declare any non-default types here with import statements

interface IRemoteSipServiceEvents {
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

    /**
    * Called by service immediately before the service exits. Service call backs are immediately invalidated. Subscribers should cleanly exit as soon as possible.
    */
    void onExit();

    /**
    * Called by service when settings preferences have been updated. Activities should reload any preferences values as necessary.
    */
    void onSettingsChanged();

    /**
    * Called by service to show in-app notifications. First parameter is a hint for the UI to compose the appropriate presenentation. The bundle is either NULL or contains the data appropriate for the message type.
    * Remember the UI will choose when and how to display the message and may even choose to discard it. Refer to UI implementation for specific behavior mapping.
    */
    void showMessage(int iAnType, in Bundle bundle);

    /**
    * Called by service to remove in-app notifications of a specific type.
    */
    void clearMessageByType(int iAnType);

    /**
    * Called by service to remove in-app notifications with a given service message id.
    */
    void clearMessageBySmid(int smid);

    /**
    * Called by the service to notify that license state has changed
    */
    void onLicenseStateUpdated(in Bundle licenseData);

}
