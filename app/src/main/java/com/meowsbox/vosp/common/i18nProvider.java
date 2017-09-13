/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.common;

import android.content.Context;

import java.util.Locale;

/**
 * Created by dhon on 7/11/2016.
 */
public interface i18nProvider {
    /**
     * Perform implementation initialization.
     *
     * @return TRUE on success
     */
    boolean init(Context context);

    /**
     * Get localized string.
     *
     * @param key
     * @param defValue
     * @return
     */
    String getString(String key, String defValue);

    /**
     * Store or update localized string. Any changes will be lost when locale is changed.
     *
     * @param key
     * @param value
     */
    void setString(String key, String value);

    /**
     * Set current locale.
     *
     * @param locale
     * @return TRUE on success, FALSE on locale not available and unchanged
     */
    boolean setExactLocale(String locale);

    /**
     * Get currently selected locale
     *
     * @return
     */
    String getLocale();

    /**
     * Attempt to match with available locale.
     *
     * @param locale
     * @return TRUE on success, FALSE on locale not available and unchanged
     */
    boolean setLocale(Locale locale);

    /**
     * Get array of supported locales
     *
     * @return
     */
    String[] getLocales();

    /**
     * Release any resources reserved by provider implementation.
     */
    void destroy();

    /**
     * Returns the status of the provider.
     *
     * @return TRUE = init completed and destroy not yet called
     */
    boolean isReady();
}
