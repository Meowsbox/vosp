/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.meowsbox.vosp.common.Logger;
import com.meowsbox.vosp.service.SipService;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Wrapper for remote service binding. Create an instance, call bindToService and implement ServiceConnectionEvents to receive remote service interface.
 * Created by dhon on 7/19/2016.
 */
public class ServiceBindingController implements ServiceConnection {
    public final static boolean DEBUG = DialerApplication.DEBUG;
    public static final int STATUS_NOT_BOUND = 0;
    public static final int STATUS_BIND_IN_PROGRESS = 1;
    public static final int STATUS_BOUND = 2;
    private static final int WD_TIMEOUT = 3000;
    public final String TAG = this.getClass().getName();
    private Context mContext = null;
    private IRemoteSipService mService = null;
    private Logger gLog;
    private ServiceConnectionEvents callback;
    private volatile int isBound = 0; // 0 not, 1 in progress, 2 bound
    private Timer wdConnect = null;

    public ServiceBindingController(Logger gLog, ServiceConnectionEvents serviceConnectionEvents, Context context) {
        this.gLog = gLog;
        this.callback = serviceConnectionEvents;
        this.mContext = context;

        // start persistent service if not already running
        Intent serviceIntent = new Intent(context, SipService.class);
        context.startService(serviceIntent);
    }

    public boolean bindToService() {
        if (isBound > STATUS_NOT_BOUND) return true;
        isBound = STATUS_BIND_IN_PROGRESS; // in progress
        // flagging for logging puposes, can happen if app is in progress of exiting and opens another fragment via the back key.
        if (DEBUG && gLog != null) gLog.l(TAG,Logger.lvVerbose,"bindToService");

        wdSchedule();
        Intent intent = new Intent(mContext, SipService.class);
//        boolean bindResult = mContext.bindService(intent, this, Context.BIND_AUTO_CREATE | Context.BIND_ABOVE_CLIENT);
        boolean bindResult = mContext.bindService(intent, this, Context.BIND_ABOVE_CLIENT);
        if (!bindResult) if (DEBUG && gLog != null) gLog.l(TAG, Logger.lvDebug, "Bind failed!");
        return bindResult;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        if (DEBUG && gLog != null) gLog.l(TAG,Logger.lvVerbose,"onServiceConnected");

        isBound = STATUS_BOUND;
        wdCancel();
        mService = IRemoteSipService.Stub.asInterface(service);
        callback.onServiceConnected(mService);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        if (DEBUG && gLog != null) gLog.l(TAG,Logger.lvVerbose,"onServiceDisconnected");
        wdCancel();
        isBound = STATUS_NOT_BOUND;
        callback.onServiceDisconnected();
        mService = null;
    }

    public void unbindToService() {
        if (isBound > STATUS_NOT_BOUND) {
            try {
                mContext.unbindService(this);
            } catch (Exception e) {
                if (DEBUG && gLog != null) gLog.l(TAG, Logger.lvDebug, "unbindToService " + e);
            }
            isBound = STATUS_NOT_BOUND;
        }
    }

    boolean isBound() {
        if (isBound == STATUS_BOUND) return true;
        return false;
    }

    int isBoundSpecific() {
        return isBound;
    }

    private void wdCancel() {
        if (wdConnect != null) {
            wdConnect.cancel();
            wdConnect = null;
        }
    }

    private void wdSchedule() {
        if (wdConnect != null)
            wdConnect.cancel();
        wdConnect = new Timer();
        wdConnect.schedule(new TimerTask() {
            @Override
            public void run() {
                callback.onServiceConnectTimeout();
            }
        }, WD_TIMEOUT);
    }


    /***************************************************************************************************************/

    public interface ServiceConnectionEvents {

        void onServiceConnectTimeout();

        void onServiceConnected(IRemoteSipService remoteService);

        void onServiceDisconnected();
    }

}
