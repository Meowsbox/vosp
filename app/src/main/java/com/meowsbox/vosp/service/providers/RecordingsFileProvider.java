/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.service.providers;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.meowsbox.vosp.common.Logger;
import com.meowsbox.vosp.service.SipService;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Created by dhon on 11/19/2017.
 */

public class RecordingsFileProvider extends ContentProvider {
    public static final String AUTHORITY = "com.meowsbox.vosp.exportRecordingsProvider";
    private static final boolean DEBUG = SipService.DEBUG;
    private static final String TAG = RecordingsFileProvider.class.getName();
    private static final Logger gLog = new Logger(Logger.lvVerbose); // always new instance and verbose in this class
    private static final String MIME_TYPE_WAV = "audio/wav";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return MIME_TYPE_WAV;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "begin");

        if (!uri.toString().contains(AUTHORITY)) {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "AUTHORITY failed " + uri.toString());
            return null;
        }

        final String lastPathSegment = uri.getLastPathSegment();
        if (lastPathSegment == null || lastPathSegment.isEmpty()) {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "lastPathSegment invalid " + lastPathSegment);
            return null;
        }

        ParcelFileDescriptor pfd;
        final String filePath = SipService.getInstance().getAppExtStoragePath() + '/' + lastPathSegment;
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "filePath " + filePath);
        final File file = new File(filePath);
        if (!file.exists() || file.isDirectory()) {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "File not exist or is dir!");
            return null;
        }

        pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "end");

        return pfd;
    }
}
