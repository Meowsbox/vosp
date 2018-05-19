/*
 * Copyright (c) 2018. Darryl Hon
 * Modifications Copyright (c) 2018. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.internal.siptest;

import android.media.MediaRecorder;

/**
 * Direct override for PJMedia Android JNI capture device audio input source routing for PJSUA2 API.
 */
public class PjsipAndroidJniAudio {
    /**
     * Specify one of MediaRecorder.AudioSource
     * Value is applied on media stream init only.
     */
    public static int micSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION;

    public static int toggleMicSource() {
        switch (micSource) {
            case MediaRecorder.AudioSource.VOICE_RECOGNITION:
                return micSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION;
            case MediaRecorder.AudioSource.VOICE_COMMUNICATION:
                return micSource = MediaRecorder.AudioSource.VOICE_RECOGNITION;
        }
        return MediaRecorder.AudioSource.VOICE_COMMUNICATION;
    }
}
