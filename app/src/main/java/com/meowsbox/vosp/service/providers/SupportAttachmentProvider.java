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
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.meowsbox.vosp.common.Logger;
import com.meowsbox.vosp.service.SipService;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A minimal {@link ContentProvider} implementation to provide diagnostic files as email attachments.
 */
public class SupportAttachmentProvider extends ContentProvider {
    private static final boolean DEBUG = SipService.DEBUG;
    public static final String AUTHORITY = "com.meowsbox.vosp.exportProvider";
    private static final String TAG = SupportAttachmentProvider.class.getName();
    private static final Logger gLog = new Logger(Logger.lvVerbose); // always new instance and verbose in this class
    private static final int COMPRESSION_LEVEL = Deflater.BEST_COMPRESSION;
    private static final String MIME_TYPE_ZIP = "application/zip";
    private UriMatcher uriMatcher;

    @Override
    public boolean onCreate() {
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(AUTHORITY, "logs.db.zip", 1);
        uriMatcher.addURI(AUTHORITY, "data.db.zip", 2);
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        switch (uriMatcher.match(uri)) {
            case 1:
            case 2:
                return MIME_TYPE_ZIP;
            default:
                return null;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        return 0;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "begin");
        ParcelFileDescriptor pfd;
        switch (uriMatcher.match(uri)) {
            case 1: {
                String intStoragePath = getContext().getFilesDir().getPath() + "/";
                final String liveFilePath = intStoragePath + "logs.db";
                File file = new File(liveFilePath);
                String cacheStoragePath = getContext().getCacheDir().getPath() + "/";
                final String zipFilePath = cacheStoragePath + "logs.db.zip";
                File filez = new File(zipFilePath);
                boolean result = createZipFiles(file, filez);
                if (!result) {
                    gLog.l(TAG, Logger.lvError, "Failed to export data for provider " + liveFilePath);
                    return null;
                }

                pfd = ParcelFileDescriptor.open(new File(zipFilePath), ParcelFileDescriptor.MODE_READ_ONLY);
                gLog.l(TAG, Logger.lvVerbose, "end " + zipFilePath);
                return pfd;
            }
            case 2: {
                String intStoragePath = getContext().getFilesDir().getPath() + "/";
                final String liveFilePath = intStoragePath + "data.db";
                File file = new File(liveFilePath);
                String cacheStoragePath = getContext().getCacheDir().getPath() + "/";
                final String zipFilePath = cacheStoragePath + "data.db.zip";
                File filez = new File(zipFilePath);
                boolean result = createZipFiles(file, filez);
                if (!result) {
                    gLog.l(TAG, Logger.lvError, "Failed to export data for provider " + liveFilePath);
                    return null;
                }

                pfd = ParcelFileDescriptor.open(new File(zipFilePath), ParcelFileDescriptor.MODE_READ_ONLY);
                gLog.l(TAG, Logger.lvVerbose, "end " + zipFilePath);
                return pfd;
            }
            default:
                gLog.l(TAG, Logger.lvError, "Unsupported uri " + uri.toString());
                break;
        }
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "end");
        return super.openFile(uri, mode);
    }

    private boolean createZipFiles(File in, File out) {
        if (in == null || out == null) return false;
        if (!in.exists()) return false;
        InputStream fileIS = null;
        ZipOutputStream zos = null;
        try {
            fileIS = new FileInputStream(in);
            zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)));
            zos.setLevel(COMPRESSION_LEVEL);
            ZipEntry entry = new ZipEntry(in.getName());
            zos.putNextEntry(entry);
            byte[] buffer = new byte[16384];
            int length;
            while ((length = fileIS.read(buffer)) != -1) {
                zos.write(buffer, 0, length);
            }
            zos.closeEntry();
            zos.close();
            fileIS.close();
            return true;
        } catch (java.io.IOException e) {
            gLog.l(TAG, Logger.lvError, e);
            return false;
        } finally {
            if (zos != null) try {
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "Size: " + out.length());
                zos.close();
            } catch (IOException e) {
                gLog.l(TAG, Logger.lvError, e);
            }
            if (fileIS != null) try {
                fileIS.close();
            } catch (IOException e) {
                gLog.l(TAG, Logger.lvError, e);
            }
        }

    }
}
