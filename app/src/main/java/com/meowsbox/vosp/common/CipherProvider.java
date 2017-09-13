/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.common;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by dhon on 6/7/2016.
 */
public interface CipherProvider {
    /**
     * Perform implementation initialization.
     *
     * @return TRUE on success
     */
    boolean init();

    /**
     * Release any resources reserved by provider implementation.
     */
    void destroy();

    /**
     * Get unique cipher type identifier
     *
     * @return
     */
    int getType();

    /**
     * Get human-readable cipher description
     *
     * @return
     */
    String getTypeFriendly();

    OutputStream getOutputStream();

    InputStream getInputStream();

    /**
     * Perform cipher routines on byte array
     *
     * @param data
     * @return ciphered bytes
     */
    byte[] enc(byte[] data);

    /**
     * Perform decipher routines on byte array
     *
     * @param data
     * @return deciphered bytes
     */
    byte[] dec(byte[] data);

}
