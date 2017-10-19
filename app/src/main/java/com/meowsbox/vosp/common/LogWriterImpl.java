/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.common;


import android.content.Context;
import android.util.Log;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.sqlite.database.sqlite.SQLiteDatabase;
import org.sqlite.database.sqlite.SQLiteStatement;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Created by dhon on 7/6/2017.
 */

public class LogWriterImpl implements Logger.LogWriter {
    private static final String TAG = "LogWriter";
    private static final String DATA_FILE_NAME = "logs";
    private static final String DATA_FILE_EXT = ".db";
    private static final String DATA_FILE_NAME_FULL = DATA_FILE_NAME + DATA_FILE_EXT;
    private static final long FLUSH_INTERVAL = 1000 * 60 * 60 * 4; // flush buffer every 4 hours
    private static final long MAX_LOG_AGE = 1000 * 60 * 60 * 24 * 3; // keep rolling 3 days of logs

    private static final int BUFFER_SIZE = 20;
    private ConcurrentLinkedQueue<LogItem> logBuffer = new ConcurrentLinkedQueue<>();
    private Context mContext;
    private SQLiteDatabase mDb;
    private String intStoragePath;
    private SQLiteStatement putLog;
    private Timer flushTimer;
    private ExecutorService executorService;
    private volatile boolean isFlushProgress = false;
    private volatile long lastFlush = 0;

    public LogWriterImpl(Context context) {
        mContext = context;
        intStoragePath = context.getFilesDir().getPath() + "/";
        final String liveFilePath = intStoragePath + DATA_FILE_NAME_FULL;
        File file = new File(liveFilePath);
        Log.d(TAG, file.getAbsolutePath());
        try {
            mDb = SQLiteDatabase.openOrCreateDatabase(liveFilePath, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (mDb == null) {
            logBuffer = null;
            Log.e(TAG, "Failed to open or create DB");
            return;
        }

        String sqlNewtable = "CREATE TABLE IF NOT EXISTS `glog` (\n" +
                "\t`logTime`\tINTEGER NOT NULL,\n" +
                "\t`logLevel`\tINTEGER NOT NULL,\n" +
                "\t`logTag`\tTEXT,\n" +
                "\t`logText`\tTEXT\n" +
                ");";

        final SQLiteStatement sNewTable = mDb.compileStatement(sqlNewtable);
        sNewTable.execute();

        putLog = mDb.compileStatement("INSERT INTO glog (logTime,logLevel,logTag,logText) values (?,?,?,?)");

        executorService = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setPriority(Thread.MIN_PRIORITY).build());
        flushTimer = new Timer("LogWriter Flusher");
        flushTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                flushSmart(false);
                purgeOldLogs();
            }
        }, FLUSH_INTERVAL, FLUSH_INTERVAL);
    }

    @Override
    public void onDestroy() {
        l(Logger.lvVerbose, "...");
        Log.d(TAG, "onDestroy...");
        flushTimer.cancel();
        flushTimer.purge();
        flushLogBuffer();
        if (!executorService.isShutdown()) {
            executorService.shutdownNow();
            try {
                executorService.awaitTermination(3000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (putLog != null) {
            putLog.releaseReference();
            putLog.close();
            putLog = null;
        }
        if (mDb != null) {
            mDb.close();
            mDb = null;
        }
        Log.d(TAG, "...onDestroy");

    }

    @Override
    public void onFlushHint() {
        if (mDb == null) return; // no mDB to write!
        l(Logger.lvVerbose, "onFlushHint");
        flushSmart(true);
    }

    @Override
    public void onLog(final int level, final String tag, final String text) {
        if (mDb == null) {
            Log.d(tag,text);
            return; // no mDB to write!
        }
        if (logBuffer != null) logBuffer.add(new LogItem(level, tag, text));
        flushSmart(false);
    }

    private void flushLogBuffer() {
        if (logBuffer == null || putLog == null) return;
        while (!logBuffer.isEmpty()) {
            final LogItem logItem = logBuffer.poll();
            if (putLog != null) putLog(logItem);
            else break;
        }
        lastFlush = System.currentTimeMillis();
        l(Logger.lvVerbose, "Flushed");
    }

    private void flushSmart(boolean ignoreInterval) {
        if (!ignoreInterval)
            if (logBuffer.size() < BUFFER_SIZE && (System.currentTimeMillis() - lastFlush) < FLUSH_INTERVAL) return;
        if (isFlushProgress) return;
        isFlushProgress = true;
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                flushLogBuffer();
                isFlushProgress = false;
            }
        });
    }

    private void l(final int level, final String text) {
        if (logBuffer != null) logBuffer.add(new LogItem(level, TAG, text));
        flushSmart(false);
    }

    private void purgeOldLogs() {
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                final long l = System.currentTimeMillis() - MAX_LOG_AGE;
                String purgeCmd = "delete from glog where glog.logTime < " + l;
                mDb.execSQL(purgeCmd);
            }
        });
    }

    private void putLog(LogItem logItem) {
        if (logItem == null || putLog == null || mDb == null) return;
        putLog.clearBindings();
        putLog.bindLong(1, logItem.time);
        putLog.bindLong(2, logItem.level);
        putLog.bindString(3, logItem.tag);
        putLog.bindString(4, logItem.text);
        putLog.execute();
    }

    class LogItem {
        long time;
        int level;
        String tag;
        String text;

        LogItem(final int level, final String tag, final String text) {
            time = System.currentTimeMillis();
            this.level = level;
            this.tag = tag;
            this.text = text;
        }

        LogItem(final long logTime, final int level, final String tag, final String text) {
            this.time = logTime;
            this.level = level;
            this.tag = tag;
            this.text = text;
        }
    }

}
