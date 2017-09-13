/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.common;

//import android.database.Cursor;
//import android.database.SQLException;
//import android.database.sqlite.SQLiteDatabase;
//import android.database.sqlite.SQLiteDoneException;
//import android.database.sqlite.SQLiteStatement;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Environment;

import com.meowsbox.vosp.DialerApplication;
import com.meowsbox.vosp.service.SipService;

import org.sqlite.database.SQLException;
import org.sqlite.database.sqlite.SQLiteDatabase;
import org.sqlite.database.sqlite.SQLiteDoneException;
import org.sqlite.database.sqlite.SQLiteStatement;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;

/**
 * Created by dhon on 7/11/2016.
 */
public class i18nProviderImpl implements i18nProvider {
    public final static boolean DEBUG = DialerApplication.DEBUG;
    private static final String TAG = i18nProviderImpl.class.getName();
    private final static String KEY_LAST_UPDATE = "key_last_update";
    private final static String DATA_FILE_NAME = "i18n";
    private final static String DATA_FILE_EXT = ".db";
    private final static String DATA_FILE_NAME_FULL = DATA_FILE_NAME + DATA_FILE_EXT;
    Logger gLog;
    private volatile SQLiteDatabase mDb;
    private volatile SoftStringCache cache = new SoftStringCache();
    private volatile boolean isInitalized = false;
    private SQLiteStatement getKeyValueCommandString;
    private Context mContext;
    private String intStoragePath;

    @Override
    public boolean init(Context context) {
//        if (DEBUG) gLog.lBegin();
        synchronized (cache) {
            cache.clear();
        }
        gLog = SipService.getInstance().getLoggerInstanceShared();
        this.mContext = context;

        intStoragePath = Environment.getDataDirectory().getAbsolutePath() + "/data/" + mContext.getPackageName() + "/files/";
        // check if live db present. Extract if file size is zero, or app has been updated
        long lastUpdate = SipService.getInstance().getLocalStore().getLong(KEY_LAST_UPDATE, 0);
        long lastUpdateExpected = 0;
        try {
            lastUpdateExpected = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            if (DEBUG) e.printStackTrace();
        }
        if (lastUpdate != lastUpdateExpected) {
            if (!copyApkDataToAppData(true)) return false;
            SipService.getInstance().getLocalStore().setLong(KEY_LAST_UPDATE, lastUpdateExpected == 0 ? -1 : lastUpdateExpected);
        } else if (!copyApkDataToAppData(false)) return false;

        mDb = SQLiteDatabase.openDatabase(intStoragePath + DATA_FILE_NAME_FULL, null, SQLiteDatabase.OPEN_READWRITE);
        if (mDb == null) {
            gLog.l(TAG, Logger.lvError, "openDatabase failed");
            return false;
        }
        if (!compileStatements()) {
            // localized table may be missing: regenerate.
            try {
                mDb.execSQL("DROP TABLE IF EXISTS localized"); // drop table in case corrupt, may fail table does not exist
            } catch (SQLException e) {
                gLog.l(TAG, Logger.lvError, e);
            }
            try {
                mDb.execSQL("create table `localized` as select keyString,coalesce(en,en) as keyValue from `i18n`;");
            } catch (SQLException e) {
                gLog.l(TAG, Logger.lvError, e);
            }
            if (!compileStatements()) {
                gLog.l(TAG, Logger.lvError, "Could not compile db statements");
                return false;
            }
        }
        isInitalized = true;

        if (DEBUG) {
            gLog.l(TAG, Logger.lvVerbose, "i18n Version: " + metaGetVersion());
            gLog.l(TAG, Logger.lvVerbose, "Available Locales: " + Arrays.toString(metaGetLocales()));
            gLog.l(TAG, Logger.lvVerbose, "Active Locale: " + getLocale());
        }

//        if (DEBUG) gLog.lEnd();
        return true;
    }

    @Override
    public String getString(String key, String defValue) {
        if (!isInitalized) {
            gLog.l(TAG, Logger.lvError, "Not ready");
            return null;
        }
        // fast return cached value if present
        synchronized (cache) {
            String cached = cache.get(key);
            if (cached != null) {
//                if (DEV) gLog.l(TAG, Logger.lvDebug, "Cached String Pair: " + key + " : " + cached);
                return cached;
            }
        }
        // lookup from db

        try {
            synchronized (getKeyValueCommandString) {
                getKeyValueCommandString.clearBindings();
                getKeyValueCommandString.bindString(1, key);
                String s = getKeyValueCommandString.simpleQueryForString();
//                if (DEV) gLog.l(TAG, Logger.lvDebug, "Query String Pair: " + key + " : " + s);
                synchronized (cache) {
                    cache.put(key, s); // push to cache
                }
                return s;
            }
        } catch (SQLiteDoneException e) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, "String Pair Not Found: " + key + " : " + defValue);
            return defValue;
        }
    }

    @Override
    public void setString(String key, String value) {
        gLog.l(TAG, Logger.lvError, "Unsupported");
    }

    @Override
    public boolean setExactLocale(String locale) {
        if (Arrays.asList(metaGetLocales()).contains(locale)) {
            synchronized (cache) {
                cache.clear();
            }
            mDb.execSQL("DROP TABLE IF EXISTS localized");
            mDb.execSQL("create table localized as select keyString,coalesce(" + locale + ",en) as keyValue from i18n;");
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "Locale changed to: " + locale);
            return true;
        } else {
            gLog.l(TAG, Logger.lvDebug, "Unsupported locale selected: " + locale);
            return false;
        }
    }

    @Override
    public String getLocale() {
        if (!isInitalized) {
            gLog.l(TAG, Logger.lvError, "Not ready");
            return null;
        }
        return getString("lang_code", null);
    }

    @Override
    public boolean setLocale(Locale locale) {
        if (locale == null) return false; // fail fast
        String language = locale.getLanguage(); // get IANA Language Subtag
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "Requested Language: " + language);
        if (getLocale().contentEquals(language)) return true; // active locale matches request locale
        boolean localeResult = setExactLocale(language); // attempt to set locale to Language Subtag
        return localeResult;
    }

    @Override
    public String[] getLocales() {
        return metaGetLocales();
    }

    @Override
    public void destroy() {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "destroy");
        isInitalized = false;
        synchronized (cache) {
            this.cache.clear();
        }
        if (mDb != null) {
            destroyCompiledStatements();
            mDb.close();
            mDb.releaseReference();
            mDb = null;
        }
        mContext = null;
    }

    @Override
    public boolean isReady() {
        return isInitalized;
    }

    private boolean compileStatements() {
        try {
            getKeyValueCommandString = mDb.compileStatement("SELECT keyValue from localized where keyString = ? LIMIT 1");
            return true;
        } catch (Exception e) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, e.getMessage());
            return false;
        }
    }

    /**
     * Extract apk bundled sqllite database file to app private data folder if none yet exists or size is 0 bytes.
     *
     * @param forceCopy
     * @return FALSE if a problem occurred
     */
    private boolean copyApkDataToAppData(boolean forceCopy) {
        boolean result = true;
        // check if live db present. Extract if not, or file size is zero
        final String liveFilePath = intStoragePath + DATA_FILE_NAME_FULL;
        File file = new File(liveFilePath);
        if (!file.exists() || file.length() == 0 || forceCopy) {
            if (forceCopy) gLog.l(TAG, Logger.lvDebug, "Force Extract");
            // extract from APK
            InputStream fileIS = null;
            BufferedOutputStream fileBOS = null;
            try {
                // delete existing database file is present
                if (file != null) {
//                    SQLiteDatabase.deleteDatabase(file); // BUG NPE when navigates up path to permission denied folder
                    file.delete();
                    new File(file.getPath() + "-journal").delete();
                    new File(file.getPath() + "-shm").delete();
                    new File(file.getPath() + "-wal").delete();
                }
                fileIS = mContext.getAssets().open(DATA_FILE_NAME_FULL);
                fileBOS = new BufferedOutputStream(mContext.openFileOutput(DATA_FILE_NAME_FULL, Activity.MODE_PRIVATE));
                //transfer bytes from the inputfile to the outputfile
                byte[] buffer = new byte[16384];
                int length;
                while ((length = fileIS.read(buffer)) != -1) {
                    fileBOS.write(buffer, 0, length);
                }
            } catch (IOException e) {
                gLog.l(TAG, Logger.lvError, "copyApkDataToAppData " + e);
                e.printStackTrace();
                result = false;
            } finally {
                if (fileBOS != null) {
                    try {
                        fileBOS.flush();
                        fileBOS.close();
                    } catch (IOException e) {
                    }
                }
                if (fileIS != null) {
                    try {
                        fileIS.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
        return result;
    }

    private void destroyCompiledStatements() {
        if (getKeyValueCommandString == null) return;
        synchronized (getKeyValueCommandString) {
            getKeyValueCommandString.clearBindings();
            getKeyValueCommandString.close();
            getKeyValueCommandString.releaseReference();
            getKeyValueCommandString = null;
        }
    }

    private String[] metaGetLocales() {
        if (!isInitalized) {
            gLog.l(TAG, Logger.lvError, "Not ready");
            return null;
        }
        Cursor cursor = mDb.rawQuery("select keyValue from meta where keyString = ? limit 1", new String[]{"locales"});
        if (cursor.getCount() <= 0) return null; // problem
        cursor.moveToFirst();
        String[] split = cursor.getString(0).split(",");
        cursor.close();
        return split;
    }

    private int metaGetVersion() {
        if (!isInitalized) {
            gLog.l(TAG, Logger.lvError, "Not ready");
            return -1;
        }
        Cursor cursor = mDb.rawQuery("select keyValue from meta where keyString = ? limit 1", new String[]{"version"});
        if (cursor.getCount() <= 0) return -1; // problem
        cursor.moveToFirst();
        int returnVal = cursor.getInt(0);
        cursor.close();
        return returnVal;
    }

    private class SoftStringCache {
        private HashMap<String, SoftReference<String>> map = new HashMap<>();

        public void clear() {
            map.clear();
        }

        public String get(Object key) {
            final SoftReference<String> stringSoftReference = map.get(key);
            if (stringSoftReference == null) return null;
            return stringSoftReference.get();
        }

        public String put(String key, String value) {
            final SoftReference<String> put = map.put(key, new SoftReference<>(value));
            if (put == null) return null;
            return put.get();
        }
    }
}
