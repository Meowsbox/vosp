/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.common;

import com.meowsbox.vosp.service.SipService;

/**
 * Created by dhon on 6/20/2016.
 */
public class StatsCollector {
    private static final String PREFIX_STAT_INLINE = "stat_il_";
    private static final String KEY_TS_APP_FIRST_RUN = "stat_first_app_timestamp";
    private static final String KEY_TS_SVC_FIRST_RUN = "stat_first_service_timestamp";
    private static final String KEY_CNT_APP_RUN = "stat_count_app_run";
    private static final String KEY_CNT_SVC_RUN = "stat_count_svc_run";
    private static final String KEY_CNT_SOT = "stat_screenOnTime";
    private static final String KEY_CNT_CALL_ANS = "stat_call_ans";
    private static final String KEY_CNT_CALL_ANS_FAIL = "stat_call_ans_fail";
    private static final String KEY_CNT_CALL_OUT = "stat_call_out";
    private static final String KEY_CNT_CALL_OUT_FAIL = "stat_call_out_fail";
    private static final String KEY_CNT_CALL_DECLINE = "stat_call_dec";
    private static final String KEY_CNT_CALL_DECLINE_FAIL = "stat_call_dec_fail";
    private static final String KEY_CNT_CALL_MISSED = "stat_call_missed";
    private static final String KEY_CNT_CALL_TIME_TOTAL = "stat_call_time_total";
    private static final String KEY_CNT_QUEUE_EXIT = "stat_queue_exit";
    private static final boolean DEBUG = SipService.DEBUG;
    public final String TAG = this.getClass().getName();
    Logger gLog;
    LocalStore lStor;
    private boolean isFirstRunApp = false;
    private boolean isFirstRunService = false;
    private long startTimeApp = 0;
    private long startTimeSvc = 0;
    private long countCallAnswered;
    private long countCallOutFailed;
    private long countCallAnswerFailed;
    private long countCallDeclined;
    private long countCallDeclineFailed;
    private long countCallOut;
    private long countCallMissed;
    private long countCallTimeTotal;
    private long countQueueExit;

    public StatsCollector(Logger gLog, LocalStore lStor) {
        this.gLog = gLog;
        this.lStor = lStor;

        // load flags
        isFirstRunApp = lStor.getLong(KEY_TS_APP_FIRST_RUN, 0) == 0;
        isFirstRunService = lStor.getLong(KEY_TS_SVC_FIRST_RUN, 0) == 0;
        countCallAnswered = lStor.getLong(KEY_CNT_CALL_ANS, 0);
        countCallAnswerFailed = lStor.getLong(KEY_CNT_CALL_ANS_FAIL, 0);
        countCallOut = lStor.getLong(KEY_CNT_CALL_OUT, 0);
        countCallOutFailed = lStor.getLong(KEY_CNT_CALL_OUT_FAIL, 0);
        countCallDeclined = lStor.getLong(KEY_CNT_CALL_DECLINE, 0);
        countCallDeclineFailed = lStor.getLong(KEY_CNT_CALL_DECLINE_FAIL, 0);
        countCallMissed = lStor.getLong(KEY_CNT_CALL_MISSED, 0);
        countCallTimeTotal = lStor.getLong(KEY_CNT_CALL_TIME_TOTAL, 0);
        countQueueExit = lStor.getLong(KEY_CNT_QUEUE_EXIT, 0);
    }

    public void commitStats() {
        lStor.setLong(KEY_CNT_CALL_ANS, countCallAnswered);
        lStor.setLong(KEY_CNT_CALL_ANS_FAIL, countCallAnswerFailed);
        lStor.setLong(KEY_CNT_CALL_OUT, countCallOut);
        lStor.setLong(KEY_CNT_CALL_OUT_FAIL, countCallOutFailed);
        lStor.setLong(KEY_CNT_CALL_DECLINE, countCallDeclined);
        lStor.setLong(KEY_CNT_CALL_DECLINE_FAIL, countCallDeclineFailed);
        lStor.setLong(KEY_CNT_CALL_MISSED, countCallMissed);
        lStor.setLong(KEY_CNT_CALL_TIME_TOTAL, countCallTimeTotal);
        lStor.setLong(KEY_CNT_QUEUE_EXIT, countQueueExit);
    }

    public void debugPrintStats() {
        if (isFirstRunApp) gLog.l(TAG, Logger.lvVerbose, "isFirstRunApp");
        if (isFirstRunService) gLog.l(TAG, Logger.lvVerbose, "isFirstRunSvc");
        gLog.l(TAG, Logger.lvVerbose, "RC " + getRunCountApp());
    }

    public long getCountCallAnswerFailed() {
        return countCallAnswerFailed;
    }

    public long getCountCallAnswered() {
        return countCallAnswered;
    }

    public long getCountCallDeclineFailed() {
        return countCallDeclineFailed;
    }

    public long getCountCallDeclined() {
        return countCallDeclined;
    }

    public long getCountCallMissed() {
        return countCallMissed;
    }

    public long getCountCallOut() {
        return countCallOut;
    }

    public long getCountCallOutFailed() {
        return countCallOutFailed;
    }

    public long getCountCallTimeTotal() {
        return countCallTimeTotal;
    }

    public long getCountQueueExit() {
        return countQueueExit;
    }

    /**
     * Get number of times app has been opened
     *
     * @return
     */
    public int getRunCountApp() {
        return lStor.getInt(KEY_CNT_APP_RUN, 0);
    }

    /**
     * Returns TRUE if this is the hard-first run the app.
     *
     * @return
     */
    public boolean isFirstRunApp() {
        return isFirstRunApp;
    }

    /**
     * Returns TRUE if this is the hard-first run the service.
     *
     * @return
     */
    public boolean isFirstRunSvc() {
        return isFirstRunService;
    }

    /**
     * Call within the onCreate of your main application Activity
     */
    public void onActivityVisible() {
        // Start Time
        startTimeApp = System.currentTimeMillis();
        // First Run flag and TS
        if (isFirstRunApp) lStor.setLong(KEY_TS_APP_FIRST_RUN, startTimeApp);
        // Run Count
        int runCount = lStor.getInt(KEY_CNT_APP_RUN, 0) + 1;
        lStor.setInt(KEY_CNT_APP_RUN, runCount);
        if (DEBUG) {
            gLog.l(TAG, Logger.lvVerbose, "RC " + getRunCountApp());
            if (isFirstRunApp) gLog.l(TAG, Logger.lvVerbose, "isFirstRunApp");
        }
    }

    public void onCallAnswerFailed() {
        countCallAnswerFailed++;
    }

    public void onCallAnswered() {
        countCallAnswered++;
    }

    public void onCallDeclineFailed() {
        countCallDeclineFailed++;
    }

    public void onCallDeclined() {
        countCallDeclined++;
    }

    public void onCallMissed() {
        countCallMissed++;
    }

    public void onCallOut() {
        countCallOut++;
    }

    public void onCallOutFailed() {
        countCallOutFailed++;
    }

    public void onCallTimeAdd(long seconds) {
        countCallTimeTotal += seconds;
    }

    public void onDestroy() {
        commitStats();
    }

    public void onQueueExit() {
        countQueueExit++;
    }

    public void onServiceStart() {
        // First Run flag and TS
        startTimeSvc = System.currentTimeMillis();
        if (isFirstRunService) lStor.setLong(KEY_TS_SVC_FIRST_RUN, startTimeSvc);
        // Run Count
        int runCount = lStor.getInt(KEY_CNT_APP_RUN, 0) + 1;
        lStor.setInt(KEY_CNT_APP_RUN, runCount);
        if (DEBUG) {
            gLog.l(TAG, Logger.lvVerbose, "RC " + getRunCountApp());
            if (isFirstRunSvc()) gLog.l(TAG, Logger.lvVerbose, "isFirstRunSvc");
        }
    }


}
