/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.common;

import android.content.Context;

/**
 * Created by dhon on 2/7/2017.
 */

public interface LocalStore {
    final static int INIT_RESULT_OK = 0;
    final static int INIT_RESULT_FAIL = 1; // generic error
    final static int INIT_RESULT_CIPHER_NOT_SET = 2;


    /**
     * Commit data to storage.
     *
     * @param immediate Force blocking commit data to storage medium before returning.
     * @return
     */
    boolean commit(boolean immediate);

    /**
     * Remove key and associated data for specified key.
     *
     * @param key TRUE if specified key was found and deleted.
     * @return
     */
    boolean delKey(String key);

    /**
     * Release any internally allocated resources. Do not call any storage methods after calling Destroy.
     */
    void destroy();

    boolean getBoolean(String key, boolean defaultValue);

    byte[] getBytes(String key, byte[] defaultValue);

    float getFloat(String key, float defaultValue);

    int getInt(String key, int defaultValue);

    long getLong(String key, long defaultValue);

    String getString(String key, String defaultValue);

    /**
     * Start internal setup. This method must be called before any other storage methods are used. Most methods will return null, throw exceptions or otherwise have undefined behaviors if called before this method is completed.
     *
     * @return
     */
    int init(Context context);

    boolean isKeyExist(String key);

    void setBoolean(String key, boolean value);

    void setBytes(String key, byte[] bytes);

    /**
     * Set the CipherProvider used to (de)obfuscate storage data.
     *
     * @param cipherProvider
     */
    void setCipherProvider(CipherProvider cipherProvider);

    void setFloat(String key, float value);

    void setInt(String key, int value);

    void setLong(String key, long value);

    /**
     * Note attempting to store a NULL value is the same as deleting the key-value pair. Alternatively store a magic or blank string.
     *
     * @param key
     * @param value
     */
    void setString(String key, String value);

}
