/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Created by dhon on 11/11/2016.
 */

public class ContactProvider {

    /**
     * Returns the first ContactId with matching phoneNumber parameter or NULL if none found.
     *
     * @param context
     * @param phoneNumber
     * @return
     */
    public static Integer getContactIdByNumber(Context context, String phoneNumber) {
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup._ID}, null, null, null); // query may throw undoc exceptions when string is empty or invalid!
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (cursor == null) {
            return null;
        }
        Integer contactId = null;
        if (cursor.moveToFirst()) {
            try {
                contactId = cursor.getInt(0);
            } catch (Exception e) {
            }
        }
        cursor.close();
        return contactId;
    }

    /**
     * Returns the first contact with matching phoneNumber parameter or FALSE if none found.
     *
     * @param context
     * @param phoneNumber
     * @return NULL on not found or other error
     */
    public static Bundle getContactBundleByNumber(Context context, String phoneNumber) {
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY}, null, null, null); // query may throw undoc exceptions when string is empty or invalid!
        } catch (Exception e) {
            e.printStackTrace();
            if (cursor != null) cursor.close();
            return null;
        }
        if (cursor == null) {
            return null;
        }
        if (cursor.moveToFirst()) {
            try {
                final Bundle bundle = new Bundle(2);
                bundle.putInt(ContactsContract.PhoneLookup._ID, cursor.getInt(0));
                bundle.putString(ContactsContract.PhoneLookup.LOOKUP_KEY, cursor.getString(1));
                return bundle;
            } catch (Exception e) {
                cursor.close();
                return null;
            }
        }
        cursor.close();
        return null;
    }

    /**
     * Returns the DISPLAY_NAME of the ContactId parameter or NULL if the contact does not exist.
     *
     * @param context
     * @param contactId
     * @return
     */
    public static String getContactName(Context context, int contactId) {
        Uri contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId);
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(contactUri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (cursor == null) {
            return null;
        }
        String contactName = null;
        if (cursor.moveToFirst()) {
            contactName = cursor.getString(0);
        }
        cursor.close();
        return contactName;
    }

    /**
     * Returns the DISPLAY_NAME of the first contact with matching phoneNumber parameter or NULL if none found.
     *
     * @param context
     * @param phoneNumber
     * @return
     */
    public static String getContactNameByNumber(Context context, String phoneNumber) {
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (cursor == null) {
            return null;
        }
        String contactName = null;
        if (cursor.moveToFirst()) {
            contactName = cursor.getString(0);
        }
        cursor.close();
        return contactName;
    }

    /**
     * Returns the full resolution photo of the ContactId parameter or NULL if the contact or a high resolution photo does not exist.
     *
     * @param context
     * @param contactId
     * @return
     */
    public static Bitmap getContactPhoto(Context context, int contactId) {
        Uri contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId);
        InputStream inputStream = null;
        try {
            inputStream = ContactsContract.Contacts.openContactPhotoInputStream(context.getContentResolver(), contactUri, true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (inputStream == null) {
            return null;
        }
        return BitmapFactory.decodeStream(inputStream);
    }

    /**
     * Returns the thumbnail photo of the ContactId parameter or NULL if the contact. A high resolution photo may also be returned where one exists and a thumbnail does not.
     *
     * @param context
     * @param contactId
     * @return
     */
    public static Bitmap getContactPhotoThumb(Context context, int contactId) {
        Uri contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId);
        Uri photoUri = Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY);
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(photoUri, new String[]{ContactsContract.Contacts.Photo.PHOTO}, null, null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (cursor == null) {
            return null;
        }
        try {
            if (cursor.moveToFirst()) {
                byte[] data = cursor.getBlob(0);
                if (data != null) {
                    return BitmapFactory.decodeStream(new ByteArrayInputStream(data));
                }
            }
        } finally {
            cursor.close();
        }
        return null;
    }


}
