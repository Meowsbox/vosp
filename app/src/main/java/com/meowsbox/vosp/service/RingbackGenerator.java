/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.service;

import android.media.AudioManager;
import android.media.ToneGenerator;

import com.meowsbox.vosp.common.Logger;

/**
 * Created by dhon on 1/6/2017.
 */

public class RingbackGenerator {
    public final static boolean DEBUG = SipService.DEBUG;
    static private final int RING_INTERVAL = 2000;
    static private final int RING_VOLUME_MAX = 50;
    public final String TAG = this.getClass().getName();
    Logger gLog;
    private ToneGenerator toneGenerator;
    private RingBackThread threadRingBack;

    public RingbackGenerator() {
        gLog = SipService.getInstance().getLoggerInstanceShared();
        toneGenerator = new ToneGenerator(AudioManager.STREAM_VOICE_CALL, RING_VOLUME_MAX);
        threadRingBack = new RingBackThread();
    }

    public boolean isPlaying() {
        if (threadRingBack == null) return false;
        return threadRingBack.isRunning;
    }

    public void start() {
        if (isPlaying()) return;
        threadRingBack.reset();
        threadRingBack.start();
        if (DEBUG) gLog.l(TAG,Logger.lvVerbose,"start");
    }

    public void stop() {
        threadRingBack.stopNow();
    }

    class RingBackThread extends Thread {
        volatile public boolean isRunning = false;

        @Override
        public void run() {
            while (isRunning) {
                if (DEBUG) gLog.l(TAG,Logger.lvVerbose,"run");
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_NETWORK_USA_RINGBACK, RING_INTERVAL); // returns immediately, plays async
                try {
                    Thread.sleep(RING_INTERVAL * 3);
                } catch (InterruptedException e) {
                }
            }

        }

        void reset() {
            isRunning = true;
        }

        void stopNow() {
            isRunning = false;
            toneGenerator.stopTone();
            Thread.currentThread().interrupt();
        }

    }

}
