/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.common;

import java.util.UUID;

/**
 * Created by dhon on 6/6/2016.
 */
public class DeviceUuid {
    public final static String KEY_DEVICE_ID = "deviceID";

    /**
     * Gets the current installation specific UUID. Generates and returns a new UUID if one was not previously present.
     *
     * @param gLog
     * @param storageProvider
     * @return
     */
    public static UUID get(Logger gLog, LocalStore storageProvider) {
        if (gLog == null || storageProvider == null) throw new NullPointerException();
        String tmpString = storageProvider.getString(KEY_DEVICE_ID, "NOUUID");
        if (tmpString != "NOUUID") {
            return UUID.fromString(tmpString);
        } else {
            UUID newUUID = UUID.randomUUID();
            saveDevice(gLog, storageProvider, newUUID);
            return newUUID;
        }
    }

    /**
     * Replaces the installation specific UUID.
     *
     * @param gLog
     * @param storageProvider
     * @param extUUID
     */
    public static void saveDevice(Logger gLog, LocalStore storageProvider, UUID extUUID) {
        if (gLog == null || storageProvider == null) throw new NullPointerException();
        if (extUUID == null) storageProvider.delKey(KEY_DEVICE_ID);
        else storageProvider.setString(KEY_DEVICE_ID, extUUID.toString());
        storageProvider.commit(true);
    }
}
