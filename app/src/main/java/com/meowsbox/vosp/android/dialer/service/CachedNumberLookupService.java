/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.android.dialer.service;

import android.content.Context;

import com.meowsbox.vosp.android.dialer.callog.ContactInfo;

//import com.android.dialer.calllog.ContactInfo;

public interface CachedNumberLookupService {

    public interface CachedContactInfo {
        public ContactInfo getContactInfo();

        public void setDirectorySource(String name, long directoryId);
        public void setExtendedSource(String name, long directoryId);
        public void setLookupKey(String lookupKey);
    }

    public CachedContactInfo buildCachedContactInfo(ContactInfo info);

    /**
     * Perform a lookup using the cached number lookup service to return contact
     * information stored in the cache that corresponds to the given number.
     *
     * @param context Valid context
     * @param number Phone number to lookup the cache for
     * @return A {@link CachedContactInfo} containing the contact information if the phone
     * number is found in the cache, {@link ContactInfo#EMPTY} if the phone number was
     * not found in the cache, and null if there was an error when querying the cache.
     */
    public CachedContactInfo lookupCachedContactFromNumber(Context context, String number);

    public void addContact(Context context, CachedContactInfo info);

    public boolean isCacheUri(String uri);

    public boolean isBusiness(int sourceType);
    public boolean canReportAsInvalid(int sourceType, String objectId);

    public boolean addPhoto(Context context, String number, byte[] photo);

    /**
     * Remove all cached phone number entries from the cache, regardless of how old they
     * are.
     *
     * @param context Valid context
     */
    public void clearAllCacheEntries(Context context);
}
