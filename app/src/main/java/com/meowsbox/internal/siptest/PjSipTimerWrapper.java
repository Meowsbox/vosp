/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.internal.siptest;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

import com.meowsbox.vosp.common.Logger;
import com.meowsbox.vosp.service.SipService;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

/**
 * Singleton wrapper class for transforming pjlib timer.c calls to Android AlarmManager.
 * Reminder - JNI calls from the library may be string mapped by class and function/method name. Changes may need to be propagated to the native library.
 * Theory: pjlip timer.c has been extended with jni.h to lookup this class, retrieve this jobject instance, and make the necessary equiv calls here.
 * A really ugly hack is included to effectively handle Doze by rescheduling alarms as AlarmClock events when screen is off. (Thank Google for specifically NOT
 * providing a reasonable code path for common use-cases like using your phone as a phone w/ort power consumption)
 * HeapIndex is a simple identifier to timer.c to distinguish between any number of pj_timer_heap_t instances that pjlib may instantiate.
 * EntryId is the native _timer_id and is recycled once fired and canceled. High values may be an indicate a leak.
 * <p>
 * Created by dhon on 1/20/2017.
 */

public class PjSipTimerWrapper extends WakefulBroadcastReceiver {
    //    public static final boolean DEBUG = SipService.DEBUG;
    public static final boolean DEBUG = false;
    public static final boolean DEBUG_VM = false; // logcat VM reference table
    public static final int MODE_AUTO = 0;
    public static final int MODE_SELFTIMER = 1;
    public static final int MODE_ALARMMANAGER = 2;
    public static final int MODE_ALARMMANAGER_AC = 3;
    static public final String TAG = PjSipTimerWrapper.class.getName();
    private static final int TIMER_OFFSET = 500;
    private static final String EXTRA_ENTRY_ID = "extra_entry_id";
    private static final String EXTRA_HEAP_INDEX = "extra_heap_index";
    private static final String EXTRA_SERVICE_ID = "extra_service_id";
    private static PjSipTimerWrapper instance = null;
    private static int mode = MODE_AUTO;
    private static boolean switchOnScreenState = false;
    private static boolean useTimerOffset = false;
    private static PowerManager.WakeLock partialWakeForTimerThread;
    private static volatile PowerManager.WakeLock partialWakeLock;
    private static Timer timer;
    private static AlarmManager alarmManager;
    private static SipService sipService;
    private static Logger gLog;
    private static HashMap<Integer, Task> taskMap = new HashMap<>(); // key = getTaskMapIndex(heapIndex,entryId)
    private static boolean initFinished = false;

    public static void enableSwitchOnScreenState(boolean switchOnScreenState) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "enableSwitchOnScreenState " + switchOnScreenState);
        PjSipTimerWrapper.switchOnScreenState = switchOnScreenState;
    }

    public static PjSipTimerWrapper getInstance() {
        if (instance == null) {
            PjSipTimerWrapper pjSipTimerWrapper = new PjSipTimerWrapper();
            pjSipTimerWrapper.init(mode);
            return pjSipTimerWrapper;
        }
        return instance;
    }

    public static PjSipTimerWrapper getInstanceInit(int mode) {
        if (instance == null) {
            PjSipTimerWrapper pjSipTimerWrapper = new PjSipTimerWrapper();
            pjSipTimerWrapper.init(mode);
            return pjSipTimerWrapper;
        }
        return instance;
    }

    /**
     * Cancel an existing alarm
     *
     * @param entryId
     * @return number of alarms cancelled, typically 1. Returning 0 will cause the native library NOT to free the timer heap entry and may lead to a memory leak.
     */
    public int cancel(int heapIndex, int entryId) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "cancel H " + heapIndex + " E " + entryId);
        if (heapIndex < 0 || entryId < 0) {
            if (DEBUG) gLog.l(TAG, Logger.lvError, "cancel Invalid parameters");
            return 0;
        }
        switch (mode) {
            case MODE_ALARMMANAGER_AC:
            case MODE_ALARMMANAGER:
                synchronized (taskMap) { // remove the entry first to reduce race exposure
                    taskMap.remove(getTaskMapIndex(heapIndex, entryId));
                }
                PendingIntent alarmIntent = getAlarmPendingIntent(heapIndex, entryId);
                alarmManager.cancel(alarmIntent);
                break;
            case MODE_SELFTIMER:
                synchronized (taskMap) {
                    Task task = taskMap.get(getTaskMapIndex(heapIndex, entryId));
                    if (task == null) return 0;
                    TimerTask timerTask = task.timerTask;
                    if (timerTask != null) {
                        timerTask.cancel();
                        taskMap.remove(getTaskMapIndex(heapIndex, entryId));
                    } else return 0;
                }
                break;
        }

        if (DEBUG_VM) debugDumpReferenceTables();

        return 1;
    }

    /**
     * Change the scheduled task infrastructure
     *
     * @param newMode
     */
    public synchronized void changeMode(int newMode) {
        if (newMode == MODE_ALARMMANAGER_AC && Build.VERSION.SDK_INT < 23) {
            gLog.l(TAG, Logger.lvDebug, "MODE_ALARMMANAGER_AC unsupported on SDK_INT < 23. Mode unchanged.");
            return;
        }
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "changeMode " + newMode);
        synchronized (taskMap) {
            switch (mode) {
                case MODE_ALARMMANAGER_AC:
                    switch (newMode) {
                        case MODE_ALARMMANAGER:
                            switchAmAcToAm();
                            break;
                        case MODE_SELFTIMER:
                            switchAmToSelf();
                            break;
                    }
                    break;
                case MODE_ALARMMANAGER:
                    switch (newMode) {
                        case MODE_ALARMMANAGER_AC:
                            switchAmToAmAc();
                            break;
                        case MODE_SELFTIMER:
                            switchAmToSelf();
                            break;
                    }
                    break;
                case MODE_SELFTIMER:
                    switch (newMode) {
                        case MODE_ALARMMANAGER:
                            switchSelfToAm();
                            break;
                        case MODE_ALARMMANAGER_AC:
                            switchSelfToAmAc();
                            break;
                    }
                    partialWakeForTimerThread.release();
                    break;
            }
            mode = newMode;
        }
        init(mode);
    }

    public PowerManager.WakeLock getLocalWakeLock() {
        return partialWakeLock;
    }

    /**
     * Call to clean up any outstanding alarms
     */

    public void onDestroy() {
        if (initFinished) switch (mode) {
            case MODE_ALARMMANAGER_AC:
            case MODE_ALARMMANAGER:
                synchronized (taskMap) {
                    Set<Map.Entry<Integer, Task>> entries = taskMap.entrySet();
                    for (Map.Entry<Integer, Task> e : entries) {
                        alarmManager.cancel(getAlarmPendingIntent(e.getValue().heapIndex, e.getKey()));
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
                partialWakeForTimerThread.release();
                break;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {         // Remember this method is called by the OS is an anonymous thread
        // check SipService instance
        final SipService localSipServiceInstance = SipService.getInstance();
        if (localSipServiceInstance == null) {
            Log.e("PjSipTimerWrapper", "SipService NULL, broadcast discarded.");
            completeWakefulIntent(intent);
            return;
        }
        // check for expected intent extras
        final int serviceId = intent.getIntExtra(EXTRA_SERVICE_ID, -1);
        if (serviceId == -1 || serviceId != localSipServiceInstance.getServiceId()) {
            completeWakefulIntent(intent);
            return; // discard possible orphaned alarm from another SipService instance
        }
        final int entryId = intent.getIntExtra(EXTRA_ENTRY_ID, -1);
        final int heapIndex = intent.getIntExtra(EXTRA_HEAP_INDEX, -1);
        if (entryId == -1) {
            completeWakefulIntent(intent);
            return;
        }
        PjSipTimerWrapper.getInstance().getLocalWakeLock().acquire(); // to be released after servicing timer on the service thread
        try { // receiver is called from an anonymous OS thread, post runnable to SipThread
            localSipServiceInstance.runOnServiceThread(new Runnable() {
                @Override
                public void run() {
                    if (DEBUG)
                        localSipServiceInstance.getLoggerInstanceShared().l(TAG, Logger.lvVerbose, "H " + heapIndex + " E " + entryId);
                    Task remove = null;
                    switch (mode) {
                        case MODE_ALARMMANAGER_AC:
                        case MODE_ALARMMANAGER:
                            synchronized (taskMap) {
                                remove = taskMap.remove(getTaskMapIndex(heapIndex, entryId));
                            }
                            break;
                        case MODE_SELFTIMER:
                            synchronized (taskMap) {
                                remove = taskMap.remove(getTaskMapIndex(heapIndex, entryId));
                            }
                            break;
                    }
                    if (remove != null) {
                        if (DEBUG) {
                            SipService.getInstance().getLoggerInstanceShared().l(TAG, Logger.lvVerbose, "Firing " + heapIndex + " " + entryId);
                            SipService.getInstance().getLoggerInstanceShared().l(TAG, Logger.lvVerbose, "Remaining " + taskMap.size());
                        }
                        pjsip_timer_fire(heapIndex, entryId); // fire native callback
                    } else
                        localSipServiceInstance.getLoggerInstanceShared().l(TAG, Logger.lvVerbose, "stale");
                    PjSipTimerWrapper.getInstance().getLocalWakeLock().release();
                }
            });
        } catch (ExecutionException e) {
            PjSipTimerWrapper.getInstance().getLocalWakeLock().release();
            e.printStackTrace();
        } catch (InterruptedException e) {
            PjSipTimerWrapper.getInstance().getLocalWakeLock().release();
            e.printStackTrace();
        }
        completeWakefulIntent(intent);
    }

    public void onScreenStateChanged(final boolean screenOn) {
        if (!switchOnScreenState) return;
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "onScreenStateChanged " +screenOn);
        if (screenOn && mode == MODE_ALARMMANAGER_AC) changeMode(MODE_ALARMMANAGER);
        if (!screenOn && mode == MODE_ALARMMANAGER) changeMode(MODE_ALARMMANAGER_AC);
    }

    /**
     * Purge all event timers
     */
    public void purgeAll() {
//        if (DEBUG) gLog.lBegin();
        switch (mode) {
            case MODE_ALARMMANAGER_AC:
            case MODE_ALARMMANAGER:
                synchronized (taskMap) { // skip cancelling actual alarms
                    taskMap.clear();
                }
                break;
            default:
                throw new java.lang.UnsupportedOperationException("Not supported yet.");
        }
        final PowerManager.WakeLock localWakeLock = PjSipTimerWrapper.getInstance().getLocalWakeLock();
        localWakeLock.setReferenceCounted(false);
        localWakeLock.release();
        localWakeLock.setReferenceCounted(true);
//        if (DEBUG) gLog.lEnd();
    }

    /**
     * Schedule a new alarm.
     *
     * @param entryId Unique indexAn existing alarm with matching entryId will be replaced.
     * @param time
     * @return the return value is not actually used but should probably be the count of alarms scheduled
     */
    public int schedule(final int heapIndex, final int entryId, int time) {
        if (useTimerOffset && time > 0) time += TIMER_OFFSET;// guard time to ensure native event is expired.
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "H " + heapIndex + " E " + entryId + " T " + time);
        switch (mode) {
            case MODE_ALARMMANAGER_AC:
            case MODE_ALARMMANAGER:
                final PendingIntent alarmIntent = getAlarmPendingIntent(heapIndex, entryId);
                alarmManager.cancel(alarmIntent);
                if (time == 0) { // if time delay is zero, bypass AlarmManager and immediately queue a broadcast
                    if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "Rapid intent...");
                    synchronized (taskMap) {
                        taskMap.put(getTaskMapIndex(heapIndex, entryId), Task.getNew(heapIndex, entryId, time, null));
                    }
                    sipService.sendBroadcast(getAlarmIntent(heapIndex, entryId));
                    return 0;
                }
                if (Build.VERSION.SDK_INT >= 23) {
                    if (mode == MODE_ALARMMANAGER_AC)
                        alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(System.currentTimeMillis() + time, alarmIntent), alarmIntent); // doze workaround to prevent sleep
                    if (mode == MODE_ALARMMANAGER)
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + time, alarmIntent); // preserve order w/o coalesce
                } else
                    alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + time, alarmIntent);
                synchronized (taskMap) {
                    taskMap.put(getTaskMapIndex(heapIndex, entryId), Task.getNew(heapIndex, entryId, time, null));
                }
                break;
            case MODE_SELFTIMER:
                TimerTask timerTask = new TimerTask() {
                    @Override
                    public void run() {
                        sipService.sendBroadcast(getAlarmIntent(heapIndex, entryId));
                    }
                };
                timer.schedule(timerTask, time);
                synchronized (taskMap) {
                    taskMap.put(getTaskMapIndex(heapIndex, entryId), Task.getNew(null, timerTask));
                }
                break;
        }

        if (DEBUG_VM) debugDumpReferenceTables();

        return 0;
    }

    @SuppressWarnings("unchecked")
    private void debugDumpReferenceTables() {
        Class c;
        try {
            c = Class.forName("android.os.Debug");
            Method m = c.getMethod("dumpReferenceTables");
            Object o = m.invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private Intent getAlarmIntent(int heapIndex, int entryId) {
        Intent intent = new Intent(sipService, PjSipTimerWrapper.class);
        intent.putExtra(EXTRA_ENTRY_ID, entryId);
        intent.putExtra(EXTRA_HEAP_INDEX, heapIndex);
        intent.putExtra(EXTRA_SERVICE_ID, sipService.getServiceId());
        return intent;
    }

    private PendingIntent getAlarmPendingIntent(int heapIndex, int entryId) {
        Intent intent = new Intent(sipService, PjSipTimerWrapper.class);
        intent.putExtra(EXTRA_ENTRY_ID, entryId);
        intent.putExtra(EXTRA_HEAP_INDEX, heapIndex);
        intent.putExtra(EXTRA_SERVICE_ID, sipService.getServiceId());
        PendingIntent alarmIntent = PendingIntent.getBroadcast(sipService, entryId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        return alarmIntent;
    }

    private PendingIntent getAlarmPendingIntentC(int heapIndex, int entryId) {
        Intent intent = new Intent(sipService, PjSipTimerWrapper.class);
        intent.putExtra(EXTRA_ENTRY_ID, entryId);
        intent.putExtra(EXTRA_HEAP_INDEX, heapIndex);
        intent.putExtra(EXTRA_SERVICE_ID, sipService.getServiceId());
        PendingIntent alarmIntent = PendingIntent.getBroadcast(sipService, entryId, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        return alarmIntent;
    }

    private int getTaskMapIndex(int heapIndex, int entryId) {
        int hash = (heapIndex + entryId) * (heapIndex + entryId + 1) / 2 + heapIndex; //Cantor pairing function
//        if (DEBUG) gLog.l(Logger.lvVerbose, heapIndex + " " + entryId + " = " + hash);
        return hash;
    }

    private void init(int prefMode) {
        if (initFinished) return;
        initFinished = true;
        sipService = SipService.getInstance();
        gLog = sipService.getLoggerInstanceShared();

        if (prefMode == MODE_AUTO)
            mode = MODE_ALARMMANAGER;
        else mode = prefMode;

        switch (mode) {
            case MODE_ALARMMANAGER_AC:
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "MODE_ALARMMANAGER_AC");
                // retrieve AlarmManager instance from context of SipService.
                if (alarmManager == null)
                    alarmManager = (AlarmManager) sipService.getSystemService(Context.ALARM_SERVICE);
                break;
            case MODE_ALARMMANAGER:
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "MODE_ALARMMANAGER");
                // retrieve AlarmManager instance from context of SipService.
                if (alarmManager == null)
                    alarmManager = (AlarmManager) sipService.getSystemService(Context.ALARM_SERVICE);
                break;
            case MODE_SELFTIMER:
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "MODE_SELFTIMER");
                if (partialWakeForTimerThread == null) {
                    PowerManager powerManager = (PowerManager) sipService.getSystemService(Context.POWER_SERVICE);
                    partialWakeForTimerThread = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "partialWakeForTimerThread");
                    partialWakeForTimerThread.acquire();
                }
                if (timer == null) timer = new Timer("partialWakeTimer");
                break;
        }
        final SipService localSipServiceInstance = SipService.getInstance();
        final PowerManager localPowerManager = (PowerManager) localSipServiceInstance.getSystemService(Context.POWER_SERVICE);
        partialWakeLock = localPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PjSipTimerWrapper");
        partialWakeLock.setReferenceCounted(true);
    }

    /**
     * Native callback to pjlib
     *
     * @param entry_id
     */
    @SuppressWarnings("JniMissingFunction")
    private native void pjsip_timer_fire(int heap_index, int entry_id);

    private void switchAmAcToAm() {
        if (DEBUG) gLog.l(TAG,Logger.lvVerbose,"switchAmAcToAm");
        synchronized (taskMap) {
            for (Map.Entry<Integer, Task> taskEntry : taskMap.entrySet()) {
                PendingIntent alarmIntent = getAlarmPendingIntent(taskEntry.getValue().heapIndex, taskEntry.getValue().entryId);
                alarmManager.cancel(alarmIntent);
                if (Build.VERSION.SDK_INT >= 23)
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + taskEntry.getValue().getTimeLeft(), alarmIntent);
                else
                    alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + taskEntry.getValue().getTimeLeft(), alarmIntent);
            }
        }
    }

    private void switchAmToAmAc() {
        if (DEBUG) gLog.l(TAG,Logger.lvVerbose,"switchAmToAm");
        synchronized (taskMap) {
            for (Map.Entry<Integer, Task> taskEntry : taskMap.entrySet()) {
                PendingIntent alarmIntent = getAlarmPendingIntent(taskEntry.getValue().heapIndex, taskEntry.getValue().entryId);
                alarmManager.cancel(alarmIntent);
                alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(System.currentTimeMillis() + taskEntry.getValue().getTimeLeft(), alarmIntent), alarmIntent);
            }
        }
    }

    private void switchAmToSelf() {
        if (DEBUG) gLog.l(TAG,Logger.lvVerbose,"switchAmToSelf");
        for (final Map.Entry<Integer, Task> taskEntry : taskMap.entrySet()) {
            PendingIntent alarmIntent = getAlarmPendingIntent(taskEntry.getValue().heapIndex, taskEntry.getKey());
            alarmManager.cancel(alarmIntent);
            TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    sipService.sendBroadcast(getAlarmIntent(taskEntry.getValue().heapIndex, taskEntry.getKey()));
                }
            };
            timer.schedule(timerTask, taskEntry.getValue().getTimeLeft());
            taskMap.put(taskEntry.getKey(), Task.getNew(null, timerTask));
        }
    }

    private void switchSelfToAm() {
        if (DEBUG) gLog.l(TAG,Logger.lvVerbose,"switchSelfToAm");
        for (Map.Entry<Integer, Task> taskEntry : taskMap.entrySet()) {
            taskEntry.getValue().timerTask.cancel();
            PendingIntent alarmIntent = getAlarmPendingIntent(taskEntry.getValue().heapIndex, taskEntry.getKey());
            if (Build.VERSION.SDK_INT >= 23)
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + taskEntry.getValue().getTimeLeft(), alarmIntent);
            else
                alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + taskEntry.getValue().getTimeLeft(), alarmIntent);
        }
        timer.purge();
    }

    private void switchSelfToAmAc() {
        if (DEBUG) gLog.l(TAG,Logger.lvVerbose,"switchSelfToAmAc");
        for (Map.Entry<Integer, Task> taskEntry : taskMap.entrySet()) {
            taskEntry.getValue().timerTask.cancel();
            PendingIntent alarmIntent = getAlarmPendingIntent(taskEntry.getValue().heapIndex, taskEntry.getKey());
            alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(System.currentTimeMillis() + taskEntry.getValue().getTimeLeft(), alarmIntent), alarmIntent);
        }
        timer.purge();
    }

    /**
     * Object for holding scheduled task meta
     */
    public static class Task {
        long createTime = System.currentTimeMillis();
        int delay;
        int heapIndex;
        int entryId;
        TimerTask timerTask;

        static Task getNew(Integer delay, TimerTask task) {
            Task task1 = new Task();
            if (delay != null) task1.delay = delay;
            if (task != null) task1.timerTask = task;
            return task1;
        }

        static Task getNew(Integer heapIndex, Integer entryId, Integer delay, TimerTask task) {
            Task task1 = new Task();
            if (delay != null) task1.delay = delay;
            if (task != null) task1.timerTask = task;
            if (heapIndex != null) task1.heapIndex = heapIndex;
            if (entryId != null) task1.entryId = entryId;
            return task1;
        }

        int getTimeLeft() {
            long l = System.currentTimeMillis() - createTime;
            return (int) (delay - l);
        }
    }

}
