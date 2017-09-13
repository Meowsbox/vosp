/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.meowsbox.vosp.common.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Wrapper for Android AlarmManager and Timer for simplified scheduled runnables.
 * Created by dhon on 2/14/2017.
 */

public class ScheduledRun extends WakefulBroadcastReceiver {
    public static final boolean DEBUG = SipService.DEBUG;
    public static final int MODE_AUTO = 0;
    public static final int MODE_SELFTIMER = 1;
    public static final int MODE_ALARMMANAGER = 2;
    private static final int SUBMODE_ALARMMANAGER_NORMAL = 0;
    private static final int SUBMODE_ALARMMANAGER_AC = 1;
    private static final String EXTRA_ENTRY_ID = "extra_entry_id";
    private static final String EXTRA_SERVICE_ID = "extra_service_id";
    private static final String ACTION_BASE = "com.meowsbox.ScheduledRun";
    public final String TAG = this.getClass().getName();
    private boolean useWorkaroundAc = false;
    private PowerManager.WakeLock partialWakeForTimer;
    private Timer timer;
    private AlarmManager alarmManager;
    private Logger gLog;
    private HashMap<Integer, Task> taskMap = new HashMap<>();
    private int mode = MODE_AUTO;
    private int submode = SUBMODE_ALARMMANAGER_NORMAL;
    private boolean isReady = false;
    private Context context;
    private int serviceId;
    private int entryCounter = 1;

    public ScheduledRun(Context context, final int prefMode, final int serviceId) {
        if (context == null) return;
        gLog = SipService.getInstance().getLoggerInstanceShared();
        this.context = context;
        this.serviceId = serviceId;
        if (prefMode == MODE_AUTO)
            mode = MODE_ALARMMANAGER;
        else mode = prefMode;
        init();
    }

    public boolean cancel(int entryId) {
        if (!isReady) return false;
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "E " + entryId);
        if (!taskMap.containsKey(entryId)) return false;
        switch (mode) {
            case MODE_ALARMMANAGER:
                PendingIntent alarmIntent = getAlarmPendingIntent(entryId);
                alarmManager.cancel(alarmIntent);
                synchronized (taskMap) {
                    taskMap.remove(entryId);
                }
                break;
            case MODE_SELFTIMER:
                synchronized (taskMap) {
                    Task task = taskMap.get(entryId);
                    if (task == null) return false;
                    TimerTask timerTask = task.timerTask;
                    if (timerTask != null) {
                        timerTask.cancel();
                        taskMap.remove(entryId);
                    }
                }
                break;
        }
        return true;
    }

    public void destroy() {
        isReady = false;
        context.unregisterReceiver(this);
        switch (mode) {
            case MODE_ALARMMANAGER:
                synchronized (taskMap) {
                    for (Map.Entry<Integer, Task> taskEntry : taskMap.entrySet()) {
                        alarmManager.cancel(getAlarmPendingIntent(taskEntry.getKey()));
                    }
                    taskMap.clear();
                }
                break;
            case MODE_SELFTIMER:
                timer.cancel();
                timer = null;
                synchronized (taskMap) {
                    taskMap.clear();
                }
                partialWakeForTimer.release();
                break;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (DEBUG) gLog.l(TAG,Logger.lvVerbose,"onReceive");
        if (!isReady) return;
        // remember this is an external thread from the system process
        final int serviceId = intent.getIntExtra(EXTRA_SERVICE_ID, -1);
        if (serviceId == -1 || serviceId != serviceId)
            return; // discard possible orphaned alarm from another SipService instance
        final int entryId = intent.getIntExtra(EXTRA_ENTRY_ID, -1);
        if (entryId == -1) {
            return;
        }

        Task task = taskMap.get(entryId);
        if (task == null) return;

        synchronized (taskMap) {
            taskMap.remove(entryId);
        }

        if (task.runnable != null) new Thread(task.runnable).start();
        completeWakefulIntent(intent);
    }

    /**
     * Call to notify this ScheduledRun about the state of the screen for useWorkaroundAc.
     *
     * @param screenOn
     */
    public void onScreenStateChanged(final boolean screenOn) {
        if (!isReady) return;
        if (!useWorkaroundAc) return;
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, screenOn);
        if (screenOn && mode == MODE_ALARMMANAGER) changeSubMode(SUBMODE_ALARMMANAGER_NORMAL);
        if (!screenOn && mode == MODE_ALARMMANAGER) changeSubMode(SUBMODE_ALARMMANAGER_AC);
    }

    /**
     * Schedule the provided runnable to be executed in the future from an external thread.
     *
     * @param delay    time in milliseconds in the future
     * @param runnable Runnable to be executed.
     * @return Reference token that can be used to cancel this event.
     */
    public int schedule(final int delay, final Runnable runnable) {
        if (!isReady) return -1;
        return schedule(++entryCounter, delay, runnable);
    }

    /**
     * Enable doze workaround with offscreen AlarmManager.AlarmClock
     *
     * @param enable
     */
    public void setUseWorkaroundAc(boolean enable) {
        useWorkaroundAc = enable;
        if (!enable) {
            switchToAmNormal();
        }
    }

    private synchronized void changeSubMode(int newSubMode) {
        if (mode != MODE_ALARMMANAGER) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, "SUBMODE only supported in MODE_ALARMMANAGER.");
            return;
        }
        if (newSubMode == SUBMODE_ALARMMANAGER_AC && Build.VERSION.SDK_INT < 23) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, "SUBMODE_ALARMMANAGER_AC unsupported on SDK_INT < 23.");
            return;
        }
        if (submode == newSubMode) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, "Nothing to change.");
            return;
        }
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, newSubMode);
        synchronized (taskMap) {
            switch (submode) {
                case SUBMODE_ALARMMANAGER_AC:
                    switchToAmNormal();
                    break;
                case SUBMODE_ALARMMANAGER_NORMAL:
                    switchToAmAc();
                    break;
            }
            submode = newSubMode;
        }
    }

    private Intent getAlarmIntent(int entryId) {
        Intent intent = new Intent();
        intent.setAction(ACTION_BASE + serviceId);
        intent.putExtra(EXTRA_ENTRY_ID, entryId);
        intent.putExtra(EXTRA_SERVICE_ID, serviceId);
        return intent;
    }

    private PendingIntent getAlarmPendingIntent(int entryId) {
        Intent intent = new Intent();
        intent.setAction(ACTION_BASE + serviceId);
        intent.putExtra(EXTRA_ENTRY_ID, entryId);
        intent.putExtra(EXTRA_SERVICE_ID, serviceId);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(context, entryId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        return alarmIntent;
    }

    /**
     * Returns a TimerTask that retrieves and executes the runnable associated entryId.
     *
     * @param entryId
     * @return
     */
    private TimerTask getTimerTask(final int entryId) {
        return new TimerTask() {
            @Override
            public void run() {
                synchronized (taskMap) {
                    Task task = taskMap.get(entryId);
                    if (task == null) return;
                    taskMap.remove(entryId);
                    if (task.runnable != null) task.runnable.run();
                }
            }
        };
    }

    private void init() {
        if (isReady) return;
        switch (mode) {
            case MODE_ALARMMANAGER:
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "MODE_ALARMMANAGER");
                // retrieve AlarmManager instance from context of SipService.
                if (alarmManager == null)
                    alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                register();
                break;
            case MODE_SELFTIMER:
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "MODE_SELFTIMER");
                if (partialWakeForTimer == null) {
                    PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                    partialWakeForTimer = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "partialWakeForTimer");
                    partialWakeForTimer.acquire();
                }
                if (timer == null) timer = new Timer("partialWakeTimer");
                break;
        }
        isReady = true;
    }

    private void register() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_BASE + serviceId);
        context.registerReceiver(this, intentFilter);
    }

    private int schedule(final int entryId, final int delay, final Runnable runnable) {
        if (!isReady) return -1;
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "ID " + entryId + " T " + delay);
        switch (mode) {
            case MODE_ALARMMANAGER:
                final PendingIntent alarmIntent = getAlarmPendingIntent(entryId);
                alarmManager.cancel(alarmIntent);
                synchronized (taskMap) {
                    taskMap.put(entryId, Task.getNew(delay, null, runnable));
                }
                if (Build.VERSION.SDK_INT >= 23) {
                    if (mode == MODE_ALARMMANAGER && useWorkaroundAc)
                        alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(System.currentTimeMillis() + delay, alarmIntent), alarmIntent);
                    if (mode == MODE_ALARMMANAGER)
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delay, alarmIntent);
                } else
                    alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delay, alarmIntent);
                break;
            case MODE_SELFTIMER:
                TimerTask timerTask = getTimerTask(entryId);
                synchronized (taskMap) {
                    taskMap.put(entryId, Task.getNew(delay, timerTask, runnable));
                }
                timer.schedule(timerTask, delay);
                break;
        }
        return entryId;
    }

    private void switchToAmAc() {
        if (submode == SUBMODE_ALARMMANAGER_AC) return;
        if (DEBUG) gLog.l(TAG,Logger.lvVerbose,"switchToAmAc");
        for (Map.Entry<Integer, Task> taskEntry : taskMap.entrySet()) {
            PendingIntent alarmIntent = getAlarmPendingIntent(taskEntry.getKey());
            alarmManager.cancel(alarmIntent);
            alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(System.currentTimeMillis() + taskEntry.getValue().getTimeLeft(), alarmIntent), alarmIntent);
        }
    }

    private void switchToAmNormal() {
        if (submode == SUBMODE_ALARMMANAGER_NORMAL) return;
        if (DEBUG) gLog.l(TAG,Logger.lvVerbose,"switchToAmNormal");
        for (Map.Entry<Integer, Task> taskEntry : taskMap.entrySet()) {
            PendingIntent alarmIntent = getAlarmPendingIntent(taskEntry.getKey());
            alarmManager.cancel(alarmIntent);
            if (Build.VERSION.SDK_INT >= 23)
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + taskEntry.getValue().getTimeLeft(), alarmIntent);
            else
                alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + taskEntry.getValue().getTimeLeft(), alarmIntent);
        }
    }

    private void unregister() {
        context.unregisterReceiver(this);
    }

    /**
     * Object for holding scheduled task meta
     */
    public static class Task {
        long createTime = System.currentTimeMillis();
        int delay;
        TimerTask timerTask;
        Runnable runnable;

        static Task getNew(Integer delay, TimerTask task, Runnable runnable) {
            Task task1 = new Task();
            if (delay != null) task1.delay = delay;
            if (task != null) task1.timerTask = task;
            if (runnable != null) task1.runnable = runnable;
            return task1;
        }


        int getTimeLeft() {
            long l = System.currentTimeMillis() - createTime;
            return (int) (delay - l);
        }
    }
}
