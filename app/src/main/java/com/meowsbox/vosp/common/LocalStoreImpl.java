/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.common;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;

import com.meowsbox.vosp.DialerApplication;
import com.meowsbox.vosp.service.SipService;

import org.sqlite.database.sqlite.SQLiteDatabase;
import org.sqlite.database.sqlite.SQLiteStatement;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * Basic {@link LocalStore} implementation without encryption. Private data file is protected by native platform security.
 * Created by dhon on 2/7/2017.
 */

public class LocalStoreImpl implements LocalStore {
    public static final boolean DEBUG = DialerApplication.DEBUG;
    static final int VALUE_TYPE_BYTES = 0;
    static final int VALUE_TYPE_STRING = 1;
    static final int VALUE_TYPE_INT = 2;
    static final int VALUE_TYPE_LONG = 3;
    static final int VALUE_TYPE_FLOAT = 4;
    static final int VALUE_TYPE_BOOLEAN = 5;
    private static final String DATA_FILE_NAME = "data";
    private static final String DATA_FILE_EXT = ".db";
    private static final String DATA_FILE_NAME_FULL = DATA_FILE_NAME + DATA_FILE_EXT;
    private static final String LOG_TEXT_NOT_READY = "Not Ready";
    private static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN; // warning: changing this value may break data file compatibility
    private static final String CHARSET_DEFAULT = "UTF-8"; // warning: changing this value may break data file compatibility
    public final String TAG = this.getClass().getName();
    Logger gLog;
    private SQLiteDatabase mDb;
    private HashMap<String, Node> cache = new HashMap<>();
    private String intStoragePath;
    private Context mContext;
    private SQLiteStatement putNode;
    private SQLiteStatement delNode;
    private volatile boolean isInitalized = false;
    private volatile boolean isDirty = false;

    @Override
    public synchronized boolean commit(boolean immediate) {
        if (DEBUG) gLog.lBegin();
        boolean errorFree = true;
        if (immediate)
            synchronized (cache) {
                for (Map.Entry<String, Node> entrypair : cache.entrySet()) {
                    boolean b = setKeyValue(entrypair.getKey(), entrypair.getValue().valueType, entrypair.getValue().value);
                    if (!b) errorFree = false;
                }
                isDirty = false;
            }
        else {
            //TODO schdeduled commit
        }
//        if (DEBUG) gLog.lEnd();
        return errorFree;
    }

    @Override
    public boolean delKey(String key) {
        boolean result = false;
        synchronized (cache) {
            if (cache.containsKey(key)) {
                cache.remove(key);
                result = true;
            }
        }
        if (deleteKeyValue(key)) result = true;
        return result;
    }

    @Override
    public void destroy() {
        isInitalized = false;
        synchronized (cache) {
            cache.clear();
        }
        if (mDb != null) {
            putNode.releaseReference();
            putNode.close();
            delNode.releaseReference();
            delNode.close();
            mDb.close();
            mDb.releaseReference();
            mDb = null;
        }
        SQLiteDatabase.releaseMemory();
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        if (!isInitalized) {
            gLog.l(TAG, Logger.lvDebug, LOG_TEXT_NOT_READY);
            return defaultValue;
        }
        // fast return cached value if present
        Node node = cache.get(key);
        if (node != null) {
            if (node.valueType == VALUE_TYPE_BOOLEAN) return (boolean) node.value;
        }
        // lookup from db
        Object keyValue = getKeyValue(key, VALUE_TYPE_BOOLEAN);
        if (keyValue == null) return defaultValue;
        return (boolean) keyValue;
    }

    @Override
    public byte[] getBytes(String key, byte[] defaultValue) {
        if (!isInitalized) {
            gLog.l(TAG, Logger.lvDebug, LOG_TEXT_NOT_READY);
            return defaultValue;
        }
        // fast return cached value if present
        Node node = cache.get(key);
        if (node != null) {
            if (node.valueType == VALUE_TYPE_BYTES) return (byte[]) node.value;
        }
        // lookup from db
        Object keyValue = getKeyValue(key, VALUE_TYPE_BYTES);
        if (keyValue == null) return defaultValue;
        return (byte[]) keyValue;
    }

    @Override
    public float getFloat(String key, float defaultValue) {
        if (!isInitalized) {
            gLog.l(TAG, Logger.lvDebug, LOG_TEXT_NOT_READY);
            return defaultValue;
        }
        // fast return cached value if present
        Node node = cache.get(key);
        if (node != null) {
            if (node.valueType == VALUE_TYPE_FLOAT) return (float) node.value;
        }
        // lookup from db
        Object keyValue = getKeyValue(key, VALUE_TYPE_FLOAT);
        if (keyValue == null) return defaultValue;
        return (float) keyValue;
    }

    @Override
    public int getInt(String key, int defaultValue) {
        if (!isInitalized) {
            gLog.l(TAG, Logger.lvDebug, LOG_TEXT_NOT_READY);
            return defaultValue;
        }
        // fast return cached value if present
        Node node = cache.get(key);
        if (node != null) {
            if (node.valueType == VALUE_TYPE_INT) return (int) node.value;
        }
        // lookup from db
        Object keyValue = getKeyValue(key, VALUE_TYPE_INT);
        if (keyValue == null) return defaultValue;
        return (int) keyValue;
    }

    @Override
    public long getLong(String key, long defaultValue) {
        if (!isInitalized) {
            gLog.l(TAG, Logger.lvDebug, LOG_TEXT_NOT_READY);
            return defaultValue;
        }
        // fast return cached value if present
        Node node = cache.get(key);
        if (node != null) {
            if (node.valueType == VALUE_TYPE_LONG) return (long) node.value;
        }
        // lookup from db
        Object keyValue = getKeyValue(key, VALUE_TYPE_LONG);
        if (keyValue == null) return defaultValue;
        return (long) keyValue;
    }

    @Override
    public String getString(String key, String defaultValue) {
        if (!isInitalized) {
            gLog.l(TAG, Logger.lvDebug, LOG_TEXT_NOT_READY);
            return defaultValue;
        }
        // fast return cached value if present
        Node node = cache.get(key);
        if (node != null) {
            if (node.valueType == VALUE_TYPE_STRING) return (String) node.value;
        }
        // lookup from db
        Object keyValue = getKeyValue(key, VALUE_TYPE_STRING);
        if (keyValue == null) return defaultValue;
        return (String) keyValue;
    }

    @Override
    public int init(Context context) {
        gLog = SipService.getInstance().getLoggerInstanceShared();
//        if (DEBUG) gLog.lBegin();
        if (isInitalized) return INIT_RESULT_OK;
        this.mContext = context;
        int result = INIT_RESULT_OK;
        intStoragePath = context.getFilesDir().getPath() + "/";
        // check if live db present. Extract if not, or incorrect version, or file size is zero
        if (!copyApkDataToAppData()) result = INIT_RESULT_FAIL;

        mDb = SQLiteDatabase.openDatabase(intStoragePath + DATA_FILE_NAME_FULL, null, SQLiteDatabase.OPEN_READWRITE);
        if (mDb == null) {
            gLog.l(TAG, Logger.lvError, "openDatabase failed");
            result = INIT_RESULT_FAIL;
        }
        mDb.enableWriteAheadLogging();
        if (!compileStatements()) {
            gLog.l(TAG, Logger.lvError, "compileStatements failed");
            mDb.close();
            result = INIT_RESULT_FAIL;
        }
        if (result == INIT_RESULT_OK) isInitalized = true;
//        if (DEBUG) gLog.lEnd();
        return result;
    }

    @Override
    public boolean isKeyExist(String key) {
        return cache.containsKey(key);
    }

    @Override
    public void setBoolean(String key, boolean value) {
        if (!isInitalized) {
            gLog.l(TAG, Logger.lvDebug, LOG_TEXT_NOT_READY);
            return;
        }
        synchronized (cache) {
            cache.put(key, new Node(VALUE_TYPE_BOOLEAN, value));
        }
        isDirty = true;
    }

    @Override
    public void setBytes(String key, byte[] bytes) {
        if (!isInitalized) {
            gLog.l(TAG, Logger.lvDebug, LOG_TEXT_NOT_READY);
            return;
        }
        synchronized (cache) {
            cache.put(key, new Node(VALUE_TYPE_BYTES, bytes));
        }
        isDirty = true;

    }

    @Override
    public void setCipherProvider(CipherProvider cipherProvider) {
        gLog.l(TAG, Logger.lvError, "CipherProvider NOT SUPPORTED");
        // database file is stored in what is documented and assumed to be OS secured location.
    }

    @Override
    public void setFloat(String key, float value) {
        if (!isInitalized) {
            gLog.l(TAG, Logger.lvDebug, LOG_TEXT_NOT_READY);
            return;
        }
        synchronized (cache) {
            cache.put(key, new Node(VALUE_TYPE_FLOAT, value));
        }
        isDirty = true;

    }

    @Override
    public void setInt(String key, int value) {
        if (!isInitalized) {
            gLog.l(TAG, Logger.lvDebug, LOG_TEXT_NOT_READY);
            return;
        }
        synchronized (cache) {
            cache.put(key, new Node(VALUE_TYPE_INT, value));
        }
        isDirty = true;

    }

    @Override
    public void setLong(String key, long value) {
        if (!isInitalized) {
            gLog.l(TAG, Logger.lvDebug, LOG_TEXT_NOT_READY);
            return;
        }
        synchronized (cache) {
            cache.put(key, new Node(VALUE_TYPE_LONG, value));
        }
        isDirty = true;

    }

    @Override
    public void setString(String key, String value) {
        if (!isInitalized) {
            gLog.l(TAG, Logger.lvDebug, LOG_TEXT_NOT_READY);
            return;
        }
        synchronized (cache) {
            cache.put(key, new Node(VALUE_TYPE_STRING, value));
        }
        isDirty = true;

    }

    private boolean compileStatements() {
        try {
            putNode = mDb.compileStatement("REPLACE INTO prefs (keyString,keyValueType,keyValueBlob) values (?,?,?)");
            delNode = mDb.compileStatement("DELETE FROM prefs where keyString = ?");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extract apk bundled sqllite database file to app private data folder if none yet exists or size is 0 bytes.
     *
     * @return FALSE if a problem occurred.
     */
    private boolean copyApkDataToAppData() {
        boolean result = true;
        // check if live db present. Extract if not, or file size is zero
        final String liveFilePath = intStoragePath + DATA_FILE_NAME_FULL;
        File file = new File(liveFilePath);
        if (!file.exists() || file.length() == 0) {
            gLog.l(TAG, Logger.lvDebug, "DB not present or zero trunc, extracting from apk...");
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
        } else {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "Existing DB found.");
        }
        return result;
    }

    private synchronized boolean deleteKeyValue(String keyString) {
        delNode.clearBindings();
        delNode.bindString(1, keyString);
        return delNode.executeUpdateDelete() >= 1;
    }

    private <T> T getKeyValue(String keyString, int expectedType) {
        Cursor cursor = mDb.rawQuery("SELECT keyValueType, keyValueBlob from prefs where keyString = ? LIMIT 1", new String[]{keyString});
        T returnVal = null;
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            int type = cursor.getInt(0);
            if (type != expectedType) {
                cursor.close();
                return null;
            }
            switch (type) {
                case VALUE_TYPE_STRING:
                    returnVal = (T) new String(cursor.getBlob(1), Charset.forName(CHARSET_DEFAULT));
                    break;
                case VALUE_TYPE_BOOLEAN:
                    returnVal = (T) new Boolean(cursor.getBlob(1)[0] == 0x1);
                    break;
                case VALUE_TYPE_BYTES:
                    returnVal = (T) cursor.getBlob(1);
                    break;
                case VALUE_TYPE_INT:
                    returnVal = (T) new Integer(ByteBuffer.wrap(cursor.getBlob(1)).order(BYTE_ORDER).getInt());
                    break;
                case VALUE_TYPE_LONG:
                    returnVal = (T) new Long(ByteBuffer.wrap(cursor.getBlob(1)).order(BYTE_ORDER).getLong());
                    break;
                case VALUE_TYPE_FLOAT:
                    returnVal = (T) new Float(ByteBuffer.wrap(cursor.getBlob(1)).order(BYTE_ORDER).getFloat());
                    break;
                default:
                    gLog.l(TAG, Logger.lvDebug, "Unhandled value type: " + type);
                    returnVal = null;
                    break;
            }
            synchronized (cache) {
                cache.put(keyString, new Node(type, returnVal));
            }
        } else returnVal = null;
        cursor.close();
        return returnVal;
    }

    private synchronized <T> boolean setKeyValue(String keyString, int keyValueType, T keyValue) {
        putNode.clearBindings();
        putNode.bindString(1, keyString);
        putNode.bindLong(2, keyValueType);
        byte[] data = null;
        switch (keyValueType) {
            case VALUE_TYPE_STRING:
                data = ((String) keyValue).getBytes();
                break;
            case VALUE_TYPE_BOOLEAN:
                data = ((Boolean) keyValue) == true ? new byte[]{0x1} : new byte[]{0x0};
                break;
            case VALUE_TYPE_BYTES:
                data = (byte[]) keyValue;
                break;
            case VALUE_TYPE_INT:
                data = ByteBuffer.allocate(Integer.SIZE / Byte.SIZE).order(BYTE_ORDER).putInt((Integer) keyValue).array();
                break;
            case VALUE_TYPE_LONG:
                data = ByteBuffer.allocate(Long.SIZE / Byte.SIZE).order(BYTE_ORDER).putLong((Long) keyValue).array();
                break;
            case VALUE_TYPE_FLOAT:
                data = ByteBuffer.allocate(Float.SIZE / Byte.SIZE).order(BYTE_ORDER).putFloat((Float) keyValue).array();
                break;
            default:
                gLog.l(TAG, Logger.lvDebug, "Unhandled value type: " + keyValueType);
                return false;
        }
        putNode.bindBlob(3, data);
        putNode.execute();
        return true;
    }

    class Node<T> {
        int valueType;
        T value;

        Node(int valueType, T value) {
            this.valueType = valueType;
            this.value = value;
        }
    }
}
