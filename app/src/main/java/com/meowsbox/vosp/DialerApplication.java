/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.meowsbox.vosp;

import android.app.Application;

import com.getkeepsafe.relinker.ReLinker;
import com.meowsbox.vosp.android.common.ContactPhotoManager;
import com.meowsbox.vosp.common.LogWriterImpl;
import com.meowsbox.vosp.common.Logger;


public class DialerApplication extends Application implements ServiceBindingController.ServiceConnectionEvents {
    public static final String TAG = DialerApplication.class.getName();
    public static final boolean DEV = false; // development flag
    public static final int LOGGER_VERBOSITY = Logger.lvVerbose; // minimum level to log
    public static final int LOGGER_VISIBILITY = DEV ? Logger.lvVerbose : Logger.lvDebug; // minimum log level before output to LogCat
    public static boolean DEBUG = true; // debug flag - setting to false will disable almost all logging
    public IRemoteSipService sipService;
    private volatile Logger gLog = new Logger(LOGGER_VERBOSITY);
    private ServiceBindingController mServiceController = null;
    private ContactPhotoManager mContactPhotoManager;

//    @Override
//    public void onCreate() {
//        super.onCreate();
//        ExtensionsFactory.init(getApplicationContext());
//    }

    public DialerApplication() {
        gLog.setLoggerVisiblity(LOGGER_VISIBILITY);
        ReLinker.Logger relinkerLogger = new ReLinker.Logger() {
            @Override
            public void log(String message) {
                gLog.l(TAG, Logger.lvDebug, message);
            }
        };
        ReLinker.log(relinkerLogger).loadLibraryBlocking(this, "sqliteX", null);
    }

    public Logger getLoggerInstanceShared() {
        return gLog;
    }

    @Override
    public Object getSystemService(String name) {
        if (ContactPhotoManager.CONTACT_PHOTO_SERVICE.equals(name)) {
            if (mContactPhotoManager == null) {
                mContactPhotoManager = ContactPhotoManager.createContactPhotoManager(this);
                registerComponentCallbacks(mContactPhotoManager);
                mContactPhotoManager.preloadPhotosInBackground();
            }
            return mContactPhotoManager;
        }

        return super.getSystemService(name);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        gLog.setLogWriter(new LogWriterImpl(this));
        mServiceController = new ServiceBindingController(gLog, this, this);
    }

    @Override
    public void onServiceConnectTimeout() {
    }

    @Override
    public void onServiceConnected(IRemoteSipService service) {
        sipService = service;
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "onServiceConnected");
    }

    @Override
    public void onServiceDisconnected() {
        sipService = null;
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "onServiceDisconnected");
    }

}
