/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.common;

import android.util.Log;

/**
 * General logging class with optional output interface for long term storage. Provides caller tagging using StackTrace or method parameter. <br><br>
 * LoggerVerbosity is inverted-importance, where 1 is the most important and INTEGER.MAX the least.
 * Two filters are available: loggerVerbosity sets the master threshold, logs outside the bounds are completely ignored. <br>
 * loggerVisiblity sets the threshold to to print to console or native logging facility. Typically loggerVisiblity and a {@link LogWriter}
 * are used in production to keep a record of all logs while only printing to native console significant errors.
 */
public class Logger {
    public static final String TAG = "Logger";
    public static final int lvNone = 0;
    public static final int lvWTF = 1;
    public static final int lvError = 2;
    public static final int lvDebug = 3;
    public static final int lvInfo = 4;
    public static final int lvVerbose = 5;
    public static final String PREFIX_COMMON = "_";
    private final int stackTraceLevel = 4;
    private int loggerVerbosity = 0; // drop logs with verbosity higher than this level
    private int loggerVisiblity = lvVerbose; // do not print logs with verbosity higher than this level
    private LogWriter logWriter = null;


    public Logger(int loggerVerbosity) {
        setVerbosity(loggerVerbosity);
    }

    public Logger(int loggerVerbosity, LogWriter logWriter) {
        setVerbosity(loggerVerbosity);
        this.logWriter = logWriter;
        if (logWriter == null) Log.d(TAG, "No LogWriter Specified");
    }

    /**
     * Call to give the Logger a hint that now would be an opportune time to flush any log buffers to prevent possible data loss in the event of immediate process termination.
     */
    public void flushHint() {
        if (logWriter != null) logWriter.onFlushHint();
    }

    public LogWriter getLogWriter() {
        return logWriter;
    }

    /**
     * The following methods use StackTrace to determine the calling method for tagging purposes. Avoid using in production.
     */

//    public void l(int level, String logText) {
//        if (loggerVerbosity > 0) {
//            if (logText != null) loggerSwitch(level, logText);
//            else loggerSwitch(level, "NULL");
//        }
//    }
//
//    public void l(int level, boolean logText) {
//        if (loggerVerbosity > 0) {
//            loggerSwitch(level, Boolean.toString(logText));
//        }
//    }
//
//    public void l(int level, int logText) {
//        if (loggerVerbosity > 0) {
//            loggerSwitch(level, Integer.toString(logText));
//        }
//    }
//
//    public void l(int level, float logText) {
//        if (loggerVerbosity > 0) {
//            loggerSwitch(level, Float.toString(logText));
//        }
//    }
//
//    public void l(int level, Exception logText) {
//        if (loggerVerbosity > 0) {
//            String message = logText.getMessage();
//            if (message == null) message = "EXCEPTION MESSAGE NULL";
//            loggerSwitch(level, message);
//        }
//    }
//
//    public void l(int level, float[] logText) {
//        if (loggerVerbosity > 0) {
//            loggerSwitch(level, java.util.Arrays.toString(logText));
//        }
//    }
    public void setLogWriter(LogWriter logWriter) {
        if (logWriter == null) return;
        this.logWriter = logWriter;
        lt(lvVerbose, TAG, "LogWriter Bound");
    }

    public void l(final String tag, final int level, final Throwable logObject) {
        final StringBuilder sb = new StringBuilder();
        sb.append(logObject.toString());
        for (StackTraceElement traceElement : logObject.getStackTrace()) {
            sb.append("\tat ");
            sb.append(traceElement);
            sb.append("\r\n");
        }
        lt(level, tag, sb.toString());
    }

    public void l(final String tag, final int level, final Object logObject) {
        if (logObject instanceof String) lt(level, tag, (String) logObject);
        else if (logObject instanceof Integer) lt(level, tag, logObject.toString());
        else if (logObject instanceof Float) lt(level, tag, logObject.toString());
        else if (logObject instanceof Long) lt(level, tag, logObject.toString());
        else if (logObject instanceof Boolean) lt(level, tag, logObject.toString());
        else if (logObject instanceof Exception) lt(level, tag, ((Exception) logObject).getMessage());
        else if (logObject instanceof Integer[]) lt(level, tag, java.util.Arrays.toString((Integer[]) logObject));
        else if (logObject instanceof Float[]) lt(level, tag, java.util.Arrays.toString((Float[]) logObject));
        else if (logObject instanceof Boolean[]) lt(level, tag, java.util.Arrays.toString((Boolean[]) logObject));
        else lt(level >= lvError ? level : lvError, tag, "Unhandled logObject");
    }

    /**
     * Log the calling method. Typically used to log events with minimal code. Defaults to Logger.lvVerbose level. Avoid using in production as a StackTrace is performed to determine the caller.
     *
     * @param level
     */
    public void l(int level) {
        if (loggerVerbosity > 0) {
            loggerSwitch(lvVerbose, "...");
        }
    }

    /**
     * Convenience method typically used to log events with minimal code. Defaults to Logger.lvVerbose level. Avoid using in production as a StackTrace is performed to determine the caller.
     */
    public void lBegin() {
        if (loggerVerbosity > 0) {
            loggerSwitch(lvVerbose, "Begin...");
        }
    }

    public void lBegin(final String tag) {
        if (loggerVerbosity > 0) {
            lt(lvVerbose, tag, "Begin...");
        }
    }

    /**
     * Convenience method typically used to log events with minimal code. Defaults to Logger.lvVerbose level. Avoid using in production as a StackTrace is performed to determine the caller.
     */
    public void lEnd() {
        if (loggerVerbosity > 0) {
            loggerSwitch(lvVerbose, "...End");
        }
    }

    public void lEnd(final String tag) {
        if (loggerVerbosity > 0) {
            lt(lvVerbose, tag, "...End...");
        }
    }

    public void lt(final int level, final String tag, final String logText) {
        if (loggerVerbosity > 0) {
            if (logText != null) loggerSwitchTagged(level, tag, logText);
            else loggerSwitch(level, "NULL");
        }
    }

    public void onDestroy() {
        if (logWriter != null) logWriter.onDestroy();
    }

    public void setLoggerVisiblity(int loggerVisiblity) {
        this.loggerVisiblity = loggerVisiblity;
        if (logWriter == null) Log.d(TAG, "No LogWriter Specified!");
    }

    /**
     * Sets the minimum level of log verbosity/severity that should be logged.
     *
     * @param loggerVerbosity
     */
    public void setVerbosity(int loggerVerbosity) {
        this.loggerVerbosity = loggerVerbosity;
    }

//    void checkGlError(String op) {
//        int error;
//        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
//            l(Logger.lvError, op + ": glError " + error);
//            throw new RuntimeException(op + ": glError " + error);
//        }
//    }

    private void logByLevel(int level, String logText, StackTraceElement callerSTE) {
        String callerString;
        if (logWriter == null)
            callerString = PREFIX_COMMON + callerSTE.getClassName() + "." + callerSTE.getMethodName();
        else
            callerString = callerSTE.getClassName() + "." + callerSTE.getMethodName();
        if (loggerVisiblity >= level) switch (level) {
            case 1:
                Log.wtf(callerString, logText);
                break;
            case 2:
                Log.e(callerString, logText);
                break;
            case 3:
                Log.d(callerString, logText);
                break;
            case 4:
                Log.i(callerString, logText);
                break;
            case 5:
                Log.v(callerString, logText);
                break;

        }
        if (logWriter != null) logWriter.onLog(level, callerString, logText);
    }

    private void logByLevelTagged(int level, String logText, String tag) {
        String callerString;
        if (logWriter == null)
            callerString = PREFIX_COMMON + tag;
        else callerString = tag;
        if (loggerVisiblity >= level) switch (level) {
            case 1:
                Log.wtf(callerString, logText);
                break;
            case 2:
                Log.e(callerString, logText);
                break;
            case 3:
                Log.d(callerString, logText);
                break;
            case 4:
                Log.i(callerString, logText);
                break;
            case 5:
                Log.v(callerString, logText);
                break;

        }
        if (logWriter != null) logWriter.onLog(level, callerString, logText);
    }

    private void loggerSwitch(final int level, final String logText) {
        switch (loggerVerbosity) {
            case 1: // all + fatal
                if (level == 1) logByLevel(level, logText, Thread.currentThread().getStackTrace()[stackTraceLevel]);
                break;
            case 2: // error
                if (level <= 2) logByLevel(level, logText, Thread.currentThread().getStackTrace()[stackTraceLevel]);
                break;
            case 3: // debug
                if (level <= 3) logByLevel(level, logText, Thread.currentThread().getStackTrace()[stackTraceLevel]);
                break;
            case 4: // info
                if (level <= 4) logByLevel(level, logText, Thread.currentThread().getStackTrace()[stackTraceLevel]);
                break;
            case 5: // verbose
                if (level <= 5) logByLevel(level, logText, Thread.currentThread().getStackTrace()[stackTraceLevel]);
                break;
        }
    }

    private void loggerSwitchTagged(int level, String tag, String logText) {
        switch (loggerVerbosity) {
            case 1: // all + fatal
                if (level == 1) logByLevelTagged(level, logText, tag);
                break;
            case 2: // error
                if (level <= 2) logByLevelTagged(level, logText, tag);
                break;
            case 3: // debug
                if (level <= 3) logByLevelTagged(level, logText, tag);
                break;
            case 4: // info
                if (level <= 4) logByLevelTagged(level, logText, tag);
                break;
            case 5: // verbose
                if (level <= 5) logByLevelTagged(level, logText, tag);
                break;
        }
    }

    interface LogWriter {
        void onDestroy();

        void onFlushHint();

        void onLog(final int level, final String tag, final String logText);
    }
}
