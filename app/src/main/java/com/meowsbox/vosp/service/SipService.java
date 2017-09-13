/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.service;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.getkeepsafe.relinker.ReLinker;
import com.meowsbox.internal.siptest.PjSipTimerWrapper;
import com.meowsbox.vosp.ContactProvider;
import com.meowsbox.vosp.DialerApplication;
import com.meowsbox.vosp.IRemoteSipServiceEvents;
import com.meowsbox.vosp.InCallActivity;
import com.meowsbox.vosp.common.DeviceUuid;
import com.meowsbox.vosp.common.LocalStore;
import com.meowsbox.vosp.common.LocalStoreImpl;
import com.meowsbox.vosp.common.LogWriterImpl;
import com.meowsbox.vosp.common.Logger;
import com.meowsbox.vosp.common.StatsCollector;
import com.meowsbox.vosp.common.i18nProvider;
import com.meowsbox.vosp.common.i18nProviderImpl;
import com.meowsbox.vosp.service.licensing.ILicensingManagerEvents;
import com.meowsbox.vosp.service.licensing.LicensingManager;
import com.meowsbox.vosp.service.receivers.DozeStatusReceiver;
import com.meowsbox.vosp.service.receivers.NetworkStatusReceiver;
import com.meowsbox.vosp.service.receivers.NotificationReceiver;
import com.meowsbox.vosp.service.receivers.PackageReplacedReceiver;
import com.meowsbox.vosp.service.receivers.PowerSaveModeReceiver;
import com.meowsbox.vosp.service.receivers.ScreenStateReceiver;
import com.meowsbox.vosp.service.receivers.ServiceStateReceiver;
import com.meowsbox.vosp.utility.UriSip;

import org.pjsip.pjsua2.AccountInfo;
import org.pjsip.pjsua2.CallInfo;
import org.pjsip.pjsua2.pjsip_status_code;
import org.sqlite.database.sqlite.SQLiteDatabase;

import java.util.Enumeration;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;


public class SipService extends Service {
    public static final boolean DEBUG = DialerApplication.DEBUG;
    public static final boolean DEV = DialerApplication.DEV;
    public static final int LOGGER_VERBOSITY = DialerApplication.LOGGER_VERBOSITY;
    public static final int AUDIO_ROUTE_EAR = 0; // default
    public static final int AUDIO_ROUTE_SPEAKER = 1;
    public static final int AUDIO_ROUTE_BLUETOOTH = 2;
    public static final int SERVICE_FOREGROUND_ID = 2017;
    public static final int STACK_STATE_STOPPED = 0;
    public static final int STACK_STATE_STARTED = 1;
    public static final String EXTRA_ONBOOT = "EXTRA_ONBOOT";
    public static final String EXTRA_ON_PKG_REPLACED = "EXTRA_PKG_REPLACED";
    public static final String TAG = SipService.class.getName();
    static final long[] VIBE_PATTERN = {0, 250, 250};
    private static final long PROMO_INTERVAL = 3600 * 1000; // refresh interval promo UI message
    private static final long PROMO_PRE_DELAY = 30 * 1000; // delay before showing promo UI message
    private static final long PROMO_CALL_COUNT_THRESHOLD = 10; // minimum calls made before showing promo UI message
    private static final long RATEUS_INTERVAL = 3600 * 1000;
    private static final long RATEUS_PRE_DELAY = 30 * 1000;
    public static volatile int serviceId; // instance identifier
    static volatile SipService instance = null;
    private static volatile Logger gLog = null;
    private static ExecutorThreadFactory pjsipThreadFactory = null;
    int sampleRateOptimal;
    ConnectivityManager connectivityManager;
    WifiManager.WifiLock wifilocksipservice;
    DozeStatusReceiver dozeStatusReceiver;
    NetworkStatusReceiver networkStatusReceiver;
    PowerSaveModeReceiver powerSaveModeReceiver;
    ServiceStateReceiver serviceStateReceiver;
    ScreenStateReceiver screenStateReceiver;
    Handler handler = null;
    volatile boolean isExitQueued = false;
    private UUID deviceUuid;
    private long tsPromoDismissed = 0; // ts when last promo ui message was shown
    private long tsRateusDismissed = 0; // ts when last rateus ui message was shown (shared between local and Google Play)
    private volatile RemoteSipServiceImpl serviceBinder = null;
    /**
     * Flag TRUE when any critical native library fails to load
     */
    private volatile boolean nativeLibLoadFailure = false;

    private StatsCollector mStat;
    private int audioRoute = 0;
    private SipEndpoint sipEndpoint;
    private NotificationReceiver notificationReceiver = null;
    private NotificationController notificationController = null;
    private LinkedList<SipAccount> sipAccounts = new LinkedList<>();
    private ConcurrentHashMap<Integer, SipCall> sipCalls = new ConcurrentHashMap<>();
    private ConcurrentLinkedDeque<SipServiceEvents> eventSubs = new ConcurrentLinkedDeque<>();
    private ConcurrentLinkedDeque<IRemoteSipServiceEvents> eventSubsRemote = new ConcurrentLinkedDeque<>();
    private boolean isServiceStarted = false;
    private ExecutorService pjsipExec = null;
    private AudioManager audioManager;
    private Vibrator vibrator;
    private SipCallLogController sipCallLogController = null;
    private Looper mServiceLooper;
    private SipServiceHandler mServiceHandler;
    private HandlerThread handlerThread;
    private AlarmManager alarmManager;
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLockPartial; // reference counted
    private boolean isNetworkAvailable = false;
    private Integer networkType = null;
    private int retryCount = 0;
    private int retryCountLimit = 3;
    private volatile int uiMessageKeyIndex = 0;
    private volatile LocalStore localStore;
    private ScheduledRun scheduledRun;
    private i18nProvider i18n;
    private DozeDisabler dozeDisabler = null;
    private AudioAttributes vibeAudioAttrib;
    private Uri uriRingToneDefault = null;
    private Ringtone ringTone = null;
    private MediaSession mediaSession;
    private Timer timerPromo;
    private Timer timerRateus;
    private PlaybackState.Builder playbackStateBuilder;
    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {

        }
    };
    private ConcurrentHashMap<Integer, Bundle> hmUiMessages = new ConcurrentHashMap<>();
    private LicensingListener licensingListener;
    private LicensingManager mLicensing;

    /**
     * Try to return the SipService singleton. Note the SipService instance returned may not be ready or running or already stopped.
     *
     * @return SipService or NULL.
     */
    public static SipService getInstance() {
        return instance;
    }

    /**
     * Submit blocking Runnable to PJSIP thread. Be sure not to call this method from within the PJSIP thread - deadlock.
     *
     * @param executor
     * @param action
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public static void runBlocking(ExecutorService executor, Runnable action) throws ExecutionException, InterruptedException {
        if (Thread.currentThread() == pjsipThreadFactory.pjsipthread) { // check if already on same thread
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, "Same thread deadlock workaround.");
            action.run();
        } else
            executor.submit(action).get();
    }

    /**
     * Submit blocking Callable to PJSIP thread.
     * Be sure not to call this method from within the PJSIP thread - deadlock.
     *
     * @param action the action to run on the PJSIP thread
     */
    public static <V> V runBlockingForValue(ExecutorService executor, Callable<V> action) throws ExecutionException, InterruptedException {
        if (Thread.currentThread() == pjsipThreadFactory.pjsipthread) { // check if already on same thread
            try {
                if (DEBUG) gLog.l(TAG, Logger.lvDebug, "Same thread deadlock workaround.");
                return action.call();
            } catch (Exception e) {
                if (DEBUG) e.printStackTrace();
            }
        }
        return executor.submit(action).get();
    }

    /**
     * Submit Callable to PJSIP thread for Future
     *
     * @param executor
     * @param action
     * @param <T>
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public static <T> Future<T> runForFutureType(ExecutorService executor, Callable<T> action) throws ExecutionException, InterruptedException {
        return executor.submit(action);
    }

    /**
     * Submit non-blocking Runnable to ExecutorService.
     *
     * @param executor
     * @param action
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public static void runOnExecThread(ExecutorService executor, Runnable action) throws ExecutionException, InterruptedException {
        executor.submit(action);
    }

    /**
     * Initiate an outgoing call with the default/primary sipAccount
     *
     * @param number
     * @return
     */
    public int callOutDefault(String number) {
        if (!localStore.getBoolean(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_ENABLED), false)) { // check if primary account is present and enabled
            UiMessagesCommon.showSetupRequired(this);
            return -1; // account disabled or not yet setup
        }
        String numberClean = PhoneNumberUtilsSimple.normalizeNumber2(number);
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, numberClean);
        try {
            SipAccount account = sipAccounts.getFirst();
            if (!isAccountRegActive(account.getId())) {
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "!AccountRegActive, call failed");
                UiMessagesCommon.showAccountRegFailed(this);
                refreshRegistrationsIfRequired();
                return -1;
            }
            showTipFindCall(false);
            return callOut(account.getSipUriFromExtension(numberClean), account).getId();
        } catch (Exception e) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
            UiMessagesCommon.showCallFailed(this);
            return -1;
        }
    }

    public void debugLookup() {
        ContactProvider contactProvider = new ContactProvider();
    }

    public void eventSubscribeRemote(final IRemoteSipServiceEvents clientEventCallback, int sipCallId) {
        try {
            runOnServiceThread(new Runnable() {
                @Override
                public void run() {
                    // check if previously subscribed
                    // note: compare actual ibinder and not ibinder.proxy, the proxy will vary
                    IBinder iBinder = clientEventCallback.asBinder();
                    for (IRemoteSipServiceEvents remoteSipServiceEvents : eventSubsRemote) {
                        if (iBinder.equals(remoteSipServiceEvents.asBinder())) {
                            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "RemoteSub duplicate discarded");
                            return;
                        }
                    }
                    eventSubsRemote.add(clientEventCallback); // add remote callback to subs
                    try { // push sticky ui messages to new sub only
                        for (Bundle bMessage : hmUiMessages.values()) {
                            clientEventCallback.showMessage(bMessage.getInt(InAppNotifications.IAN_TYPE), bMessage);
                        }
                    } catch (RemoteException e) {
                        if (DEBUG) e.printStackTrace();
                    }
                    showPromo(false);
                    showBatterySaverUim(false);
                }
            });
        } catch (ExecutionException e) {
            if (DEBUG) e.printStackTrace();
        } catch (InterruptedException e) {
            if (DEBUG) e.printStackTrace();
        }
    }

    /**
     * Subscribe to SipServiceEvents call backs
     *
     * @param sipServiceEvents
     */
    public void eventsSubscribed(SipServiceEvents sipServiceEvents) {
        if (!eventSubs.contains(sipServiceEvents)) eventSubs.add(sipServiceEvents);
    }

    /**
     * Unsubscribe from SipServiceEvents call backs
     *
     * @param sipServiceEvents
     */
    public void eventsUnsubscribe(SipServiceEvents sipServiceEvents) {
        eventSubs.remove(sipServiceEvents);
    }

    public void eventsUnsubscribeRemote(final IRemoteSipServiceEvents clientEventCallback, int sipCall) {
        try {
            runOnServiceThread(new Runnable() {
                @Override
                public void run() {
                    // note: compare actual ibinder and not ibinder.proxy, the proxy will vary
                    IBinder iBinder = clientEventCallback.asBinder();
                    for (IRemoteSipServiceEvents remoteSipServiceEvents : eventSubsRemote) {
                        if (iBinder.equals(remoteSipServiceEvents.asBinder())) {
                            boolean result = eventSubsRemote.remove(remoteSipServiceEvents);
                            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "RemoteSub Removal: " + result);
                            return;
                        }
                    }
                    if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "RemoteSub Removal: not found");
                }
            });
        } catch (ExecutionException e) {
            if (DEBUG) e.printStackTrace();
        } catch (InterruptedException e) {
            if (DEBUG) e.printStackTrace();
        }
    }

    public int getAudioRoute() {
        return audioRoute;
    }

    void setAudioRoute(final int audioRoute) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, audioRoute);
        try {
            runOnServiceThread(new Runnable() {
                @Override
                public void run() {
                    switch (audioRoute) {
                        case AUDIO_ROUTE_EAR:
                            audioManager.stopBluetoothSco();
                            audioManager.setSpeakerphoneOn(false);
                            audioManager.setBluetoothScoOn(false);
                            break;
                        case AUDIO_ROUTE_SPEAKER:
                            audioManager.stopBluetoothSco();
                            audioManager.setSpeakerphoneOn(true);
                            audioManager.setBluetoothScoOn(false);
                            break;
                        case AUDIO_ROUTE_BLUETOOTH:
                            if (audioManager.isBluetoothScoAvailableOffCall()) {
                                audioManager.startBluetoothSco();
                                audioManager.setBluetoothScoOn(true);
                                audioManager.setSpeakerphoneOn(false);
                            } else {
                                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "Bluetooth SCO unavailable");
                                UiMessagesCommon.showBluetoothScoUnavailable(getInstance());
                            }
                            break;
                        default:
                            if (DEBUG) gLog.l(TAG, Logger.lvDebug, "Unhandled audioRoute " + audioRoute);
                            break;
                    }
                    SipService.getInstance().audioRoute = audioRoute;
                }
            });
        } catch (ExecutionException e) {
            if (DEBUG) e.printStackTrace();
        } catch (InterruptedException e) {
            if (DEBUG) e.printStackTrace();
        }

    }

    public String getDeviceId() {
        return deviceUuid == null ? null : deviceUuid.toString();
    }

    public i18nProvider getI18n() {
        return i18n;
    }

    public Bundle getLicensingStateBundle() {
        if (mLicensing != null) return mLicensing.getStateBundle();
        else return null;
    }

    public LocalStore getLocalStore() {
        return localStore;
    }

    public Logger getLoggerInstanceShared() {
        return gLog;
    }

    public Integer getNetworkType() {
        return networkType;
    }

    public ScheduledRun getScheduledRun() {
        return scheduledRun;
    }

    /**
     * A random integer representing this particular instance of SipService.
     *
     * @return
     */
    public int getServiceId() {
        return serviceId;
    }

    public boolean getSipCallIsOutgoing(final int pjSipCallId) {
        boolean isOutgoing = false;
        try {
            isOutgoing = runBlockingForValue(pjsipExec, new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    SipCall sipCallById = getSipCallById(pjSipCallId);
                    if (sipCallById != null) {
                        return sipCallById.isOutgoing();
                    } else return false;
                }
            });
        } catch (ExecutionException e) {
            if (DEBUG) e.printStackTrace();
        } catch (InterruptedException e) {
            if (DEBUG) e.printStackTrace();
        }
        return isOutgoing;
    }

    public boolean getSipCallMuteState(final int pjSipCallId) {
        boolean isMute = false;
        try {
            isMute = runBlockingForValue(pjsipExec, new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    SipCall sipCallById = getSipCallById(pjSipCallId);
                    if (sipCallById != null) {
                        return sipCallById.isMute();
                    } else return false;
                }
            });
        } catch (ExecutionException e) {
            if (DEBUG) e.printStackTrace();
        } catch (InterruptedException e) {
            if (DEBUG) e.printStackTrace();
        }
        return isMute;
    }

    public int getSipCallState(final int pjSipCallId) {
        int callstate = SipCall.SIPSTATE_NOTFOUND;
        try {
            callstate = runBlockingForValue(pjsipExec, new Callable<Integer>() {
                @Override
                public Integer call() throws Exception {
                    SipCall sipCallById = getSipCallById(pjSipCallId);
                    if (sipCallById != null) {
                        return sipCallById.getSipState();
                    } else return SipCall.SIPSTATE_NOTFOUND;
                }
            });
        } catch (ExecutionException e) {
            if (DEBUG) e.printStackTrace();
        } catch (InterruptedException e) {
            if (DEBUG) e.printStackTrace();
        }
        return callstate;
    }

    public Bundle getSkuInfoBundle() {
        if (mLicensing != null) return mLicensing.getSkuInfoBundle();
        else return null;
    }

    public StatsCollector getStatsProvider() {
        return mStat;
    }

    public boolean isStackReady() {
        return getSipEndpoint().isAlive();
    }

    public void onAirplaneModeChanged() {
        if (Settings.Global.getInt(getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0) {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "No Connectivity - Airplane Mode");
            isNetworkAvailable = false; // no networks available at all
            Message message = new Message();
            message.arg1 = SipServiceMessages.MSG_CONNECTIVITY_CHANGED;
            message.arg2 = 1; // disable wakelock release in handler
            queueCommand(message);
        }
    }

    @Override
    public void onCreate() {
        gLog = new Logger(DialerApplication.LOGGER_VERBOSITY);
        gLog.setLoggerVisiblity(DialerApplication.LOGGER_VISIBILITY);
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "onCreate");
        instance = this;
        ReLinker.Logger relinkerLogger = new ReLinker.Logger() {
            @Override
            public void log(String message) {
                gLog.l(TAG, Logger.lvDebug, message);
            }
        };
        nativeLibLoadFailure |= !ReLinker.log(relinkerLogger).loadLibraryBlocking(this, "sqliteX", null);
        nativeLibLoadFailure |= !ReLinker.log(relinkerLogger).loadLibraryBlocking(this, "pjsua2", null);
        gLog.setLogWriter(new LogWriterImpl(this));
        isServiceStarted = false;
        super.onCreate();
    }

    @SuppressWarnings("WrongConstant")
    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "onStartCommand");
        if (nativeLibLoadFailure) {
            gLog.l(TAG, Logger.lvError, "Can not start service - one or more native libraries failed to load");
            toast("Failed to load native libraries", Toast.LENGTH_LONG);
            toast("This app may not be compatible with your device", Toast.LENGTH_LONG);
            stopSelf();
            return START_NOT_STICKY;
        }
        final Context contextAnchor = this;
        if (pjsipThreadFactory == null) pjsipThreadFactory = new ExecutorThreadFactory();
        if (pjsipExec == null)
            pjsipExec = Executors.newSingleThreadExecutor(pjsipThreadFactory); // allocate exclusive thread for PJSIP
        try {
            int returnVal = runBlockingForValue(pjsipExec, new Callable<Integer>() {
                @Override
                public Integer call() throws Exception {
                    if (isServiceStarted) {
                        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "already running...");
                        return START_NOT_STICKY; // prevent reinit
                    }
                    isServiceStarted = true;

                    Thread.currentThread().setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                        @Override
                        public void uncaughtException(Thread t, Throwable e) {
                            if (gLog != null) {
                                gLog.l(TAG, Logger.lvError, e.getMessage());
                                gLog.l(TAG, Logger.lvError, e.getStackTrace());
                                gLog.flushHint();
                                e.printStackTrace();
                            }
                            else {
                                e.printStackTrace();
                            }
                        }
                    });


                    // pull in platform services
                    connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                    alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                    powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                    vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    wakeLockPartial = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SipService");
                    wakeLockPartial.setReferenceCounted(true);

                    generateServiceId();

                    localStore = new LocalStoreImpl();
                    int initResult = localStore.init(contextAnchor);
                    if (initResult != LocalStore.INIT_RESULT_OK)
                        gLog.l(TAG, Logger.lvDebug, "localStore.init " + initResult);

                    deviceUuid = DeviceUuid.get(gLog, localStore);
                    gLog.l(TAG, Logger.lvDebug, "DeviceId " + deviceUuid.toString());

                    mStat = new StatsCollector(gLog, localStore);
                    mStat.onServiceStart();
                    if (mStat.isFirstRunSvc()) firstRunSetup();

                    if (intent != null) {
                        // check if service has been started via ONBOOT
                        if (intent.getBooleanExtra(EXTRA_ONBOOT, false)) {
                            boolean onBootEnabled = localStore.getBoolean(Prefs.KEY_ONBOOT_ENABLE, false);
                            if (!onBootEnabled) {
                                stopSelf();
                                return START_NOT_STICKY;
                            }
                        }
                        // check if the service has been started because package replaced / upgraded
                        if (intent.getBooleanExtra(EXTRA_ON_PKG_REPLACED, false)) {
                            boolean onBootEnabled = localStore.getBoolean(Prefs.KEY_ONBOOT_ENABLE, false);
                            if (!onBootEnabled) {
                                stopSelf();
                                return START_NOT_STICKY;
                            }
                        }
                    }

                    // setup locale
                    Locale locale = getResources().getConfiguration().locale;
                    i18n = new i18nProviderImpl();
                    i18n.init(contextAnchor);
                    i18n.setLocale(locale);

                    // setup doze listener
                    dozeStatusReceiver = new DozeStatusReceiver();
                    DozeStatusReceiver.register(contextAnchor, dozeStatusReceiver);

                    // setup power save mode listener
                    powerSaveModeReceiver = new PowerSaveModeReceiver();
                    PowerSaveModeReceiver.register(contextAnchor, powerSaveModeReceiver);

                    // setup network state listener
                    networkStatusReceiver = new NetworkStatusReceiver();
                    NetworkStatusReceiver.register(contextAnchor, networkStatusReceiver);

                    // setup airplane modew listener
                    serviceStateReceiver = new ServiceStateReceiver();
                    ServiceStateReceiver.register(contextAnchor, serviceStateReceiver);

                    // setup screen state listener
                    screenStateReceiver = new ScreenStateReceiver();
                    ScreenStateReceiver.register(contextAnchor, screenStateReceiver);

                    scheduledRun = new ScheduledRun(contextAnchor, ScheduledRun.MODE_AUTO, serviceId);
//                    scheduledRun.setUseWorkaroundAc(true);

                    notificationController = new NotificationController(getInstance());
                    notificationController.cancelNotificationAll();

                    setupAndroidAudio();

                    sipCallLogController = new SipCallLogController(getInstance());

                    serviceBinder = new RemoteSipServiceImpl();

                    if (sipEndpoint == null) sipEndpoint = new SipEndpoint();
                    sipEndpoint.init();

//                    debugConnect();
                    populateAccounts();

                    // Start up a separate thread for handling messages only
                    handlerThread = new HandlerThread("SipServiceHandlerThread");
                    handlerThread.start();

                    // Get the HandlerThread's Looper and use it for our Handler
                    mServiceLooper = handlerThread.getLooper();
                    mServiceHandler = new SipServiceHandler(mServiceLooper);

                    Message message = new Message();
                    message.arg1 = SipServiceMessages.MSG_REQ_BATTERY_SAVER_EXEMPT;
                    queueCommand(message);

                    WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                    wifilocksipservice = wifiManager.createWifiLock("wifilocksipservice");
                    updateWifiLockState();

                    setupDozeDisablerAsync();

                    if (timerPromo == null) timerPromo = new Timer();
                    timerPromo.scheduleAtFixedRate(new TimerTask() {
                        @Override
                        public void run() {
                            showPromo(false);
                        }
                    }, PROMO_PRE_DELAY, PROMO_INTERVAL);

                    mLicensing = new LicensingManager(contextAnchor);
                    mLicensing.listenerAdd(licensingListener = new LicensingListener());
                    mLicensing.init();

                    showAlternateLauncherIcons(localStore.getBoolean(Prefs.KEY_SHOW_ALTERNATE_LAUNCHERS, false));

                    startForeground(SERVICE_FOREGROUND_ID, notificationController.getForegroundServiceNotification(null));

                    return START_STICKY;
                }
            });
            setupMediaButtonHandler();
            return returnVal;
        } catch (Exception e) {
            gLog.l(TAG, Logger.lvError, e);
            if (DEBUG) e.printStackTrace();
            return START_NOT_STICKY;
        }
    }

    @Override
    public void onDestroy() {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "onDestroy");
        if (mStat != null) mStat.onDestroy();
        if (mLicensing != null) {
            if (licensingListener != null) mLicensing.listenerRemove(licensingListener);
            mLicensing.onDestroy();
        }
        if (timerPromo != null) {
            timerPromo.cancel();
            timerPromo.purge();
            timerPromo = null;
        }
        if (pjsipExec != null) try {
            runBlocking(pjsipExec, new Runnable() {
                @Override
                public void run() {
                    if (sipEndpoint != null) sipEndpoint.destroy();
                    sipEndpoint = null;
                    eventSubs.clear();
                    eventSubsRemote.clear();
                    isServiceStarted = false;
                }
            });
        } catch (Exception e) {
            gLog.l(TAG, Logger.lvError, e);
        }
        PjSipTimerWrapper pjSipTimerWrapper = PjSipTimerWrapper.getInstance();
        pjSipTimerWrapper.onDestroy();
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        if (wifilocksipservice != null && wifilocksipservice.isHeld()) wifilocksipservice.release();
        if (dozeStatusReceiver != null) DozeStatusReceiver.unregister(this, dozeStatusReceiver);
        if (powerSaveModeReceiver != null) PowerSaveModeReceiver.unregister(this, powerSaveModeReceiver);
        if (networkStatusReceiver != null) NetworkStatusReceiver.unregister(this, networkStatusReceiver);
        if (serviceStateReceiver != null) ServiceStateReceiver.unregister(this, serviceStateReceiver);
        if (screenStateReceiver != null) ScreenStateReceiver.unregister(this, screenStateReceiver);
        if (scheduledRun != null) scheduledRun.destroy();
        if (i18n != null) {
            i18n.destroy();
            i18n = null;
        }
        if (localStore != null) {
            localStore.commit(true);
            localStore.destroy();
            localStore = null;
        }
        if (pjsipExec != null) {
            pjsipExec.shutdownNow();
            pjsipExec = null;
        }
        mServiceHandler = null;
        if (mServiceLooper != null)
            mServiceLooper.quit();
        if (handlerThread != null) {
            handlerThread.quit();
            handlerThread = null;
        }
        gLog.onDestroy();
        instance = null; // very important
        System.runFinalization();
        System.exit(0);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // onBind is called ONCE on the FIRST bindService request and cached by the OS
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "onBind");
        if (serviceBinder == null) onStartCommand(intent, 0, 0); // service not yet started - start service
        return serviceBinder;
    }

    /**
     * Called by platform on transition of Doze state. Platform time sensitive.
     */
    public void onDozeStateChanged() {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "onDozeStateChanged");

        // send command
        Message message = new Message();
        message.arg1 = SipServiceMessages.MSG_DOZE_STATE_CHANGED;
        queueCommand(message);
    }

    /**
     * Called by platform on change of network availability. Platform time sensitive.
     */
    public void onNetworkStateChanged() {
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        if (activeNetworkInfo != null) { // NULL = no connectivity available
            boolean connected = activeNetworkInfo.isConnected();
            int type = activeNetworkInfo.getType();

            if (DEBUG) {
                gLog.l(TAG, Logger.lvVerbose, "connected " + connected);
                gLog.l(TAG, Logger.lvVerbose, "isConnectedOrConnecting " + activeNetworkInfo.isConnectedOrConnecting());
                gLog.l(TAG, Logger.lvVerbose, "isFailover " + activeNetworkInfo.isFailover());
                gLog.l(TAG, Logger.lvVerbose, "isAvailable " + activeNetworkInfo.isAvailable());
                gLog.l(TAG, Logger.lvVerbose, "isRoaming " + activeNetworkInfo.isRoaming());
                gLog.l(TAG, Logger.lvVerbose, "type " + (type == 0 ? "Mobile" : "Wifi"));
            }

            if (networkType == null) networkType = type;
            if (networkType != type && connected) { // network type changed
                wakelockPartialAcquire("onNetworkStateChanged"); // will be released by MSG_WAKELOCK_RELEASE handler
                networkType = type;
                isNetworkAvailable = connected;
                Message message = new Message();
                message.arg1 = SipServiceMessages.MSG_CONNECTIVITY_CHANGED;
                message.arg2 = 0; // enable wakelock release in handler
                queueCommand(message);

            } else if (networkType == type && connected) { // same network type but updated?
                wakelockPartialAcquire("onNetworkStateChanged"); // will be releaseed by MSG_CONNECTIVITY_CHANGED handler
                networkType = type;
                isNetworkAvailable = connected;
                // send command, handler will release wakelock
                Message message = new Message();
                message.arg1 = SipServiceMessages.MSG_CONNECTIVITY_CHANGED;
                message.arg2 = 0; // enable wakelock release in handler
                queueCommand(message);
            } else {
                // we don't care about disconnection events at all
            }
            networkType = type;
            isNetworkAvailable = connected;
        } else {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "No Connectivity");
            isNetworkAvailable = false; // no networks available at all
            wakelockPartialAcquire("onNetworkStateChanged"); // will be releaseed by MSG_CONNECTIVITY_CHANGED handler
            Message message = new Message();
            message.arg1 = SipServiceMessages.MSG_CONNECTIVITY_CHANGED;
            message.arg2 = 0; // enable wakelock release in handler
            queueCommand(message);
        }

        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, isNetworkAvailable);
    }

    public void onPowerSaveModeChanged() {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "onPowerSaveModeChanged");
        showBatterySaverUim(false);
    }

    /**
     * Called by platform on change of screen state. Platform time sensitive.
     *
     * @param screenOn
     */
    public void onScreenStateChanged(boolean screenOn) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, screenOn);

        // send command
        Message message = new Message();
        message.arg1 = SipServiceMessages.MSG_SCREEN_STATE_CHANGED;
        message.arg2 = screenOn ? 1 : 0;
        queueCommand(message);
    }

    public boolean putSipCallDtmf(final int pjSipCallId, final String digit) {
        if (!isStackReady()) return false;
        boolean isMute = false;
        try {
            isMute = runBlockingForValue(pjsipExec, new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    SipCall sipCallById = getSipCallById(pjSipCallId);
                    if (sipCallById != null) {
                        return sipCallById.putInCallDTMF(digit);
                    } else return false;
                }
            });
        } catch (ExecutionException e) {
            if (DEBUG) e.printStackTrace();
        } catch (InterruptedException e) {
            if (DEBUG) e.printStackTrace();
        }
        return isMute;
    }

    /**
     * Clear all queued command messages. Use with caution!
     *
     * @return
     */
    public boolean queueClearAll() {
        if (mServiceHandler == null) return false;
        mServiceHandler.removeCallbacksAndMessages(null);
        return true;
    }

    public boolean queueCommand(Message msg) {
        if (msg == null) return false;
        if (mServiceHandler == null) return false;
        return mServiceHandler.sendMessage(msg);
    }

    public boolean queueCommand(Message msg, int delay) {
        if (msg == null) return false;
        if (mServiceHandler == null) return false;
        return mServiceHandler.sendMessageDelayed(msg, delay);
    }

    public void resetAccountRetryCountAll() {
        for (SipAccount sipAccount : sipAccounts) {
            sipAccount.resetRetryCount();
        }
    }

    public void ringAndVibe(final boolean enable) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, enable);
        if (uriRingToneDefault == null)
            uriRingToneDefault = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        if (ringTone == null) ringTone = RingtoneManager.getRingtone(this, uriRingToneDefault);
        if (vibeAudioAttrib == null)
            vibeAudioAttrib = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build();
        boolean vibrate_when_ringing = localStore.getBoolean(Prefs.KEY_VIBRATE_ON_RING, true);
        int ringerMode = audioManager.getRingerMode();
        if (enable) {
            if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "RINGER_MODE_VIBRATE");
                if (vibrator.hasVibrator() && vibrate_when_ringing) vibrator.vibrate(VIBE_PATTERN, 0, vibeAudioAttrib);
            } else if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "RINGER_MODE_NORMAL");
                ringTone.play();
                if (vibrator.hasVibrator() && vibrate_when_ringing) vibrator.vibrate(VIBE_PATTERN, 0, vibeAudioAttrib);
            }
        } else {
            if (vibrator.hasVibrator()) vibrator.cancel();
            if (ringTone.isPlaying()) ringTone.stop();
        }
    }

    public void runOnServiceThread(Runnable action) throws ExecutionException, InterruptedException {
        if (pjsipExec == null || pjsipExec.isShutdown()) { // sanity check
            if (gLog != null) gLog.l(TAG, Logger.lvError, "pjsipExec not ready, runnable skipped!");
            else Log.e("SipService", "pjsipExec not ready, runnable skipped!");
        }
        if (Thread.currentThread() == pjsipThreadFactory.pjsipthread) { // check if already on same thread
            action.run();
        } else pjsipExec.submit(action);
    }

    public void wakelockPartialAcquire(String ident) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, ident);
        wakeLockPartial.acquire();
    }

    public void wakelockPartialRelease(String ident) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, ident);
        if (wakeLockPartial.isHeld()) wakeLockPartial.release();
        if (wakeLockPartial.isHeld()) if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "other wakelocks still acquired...");
    }

    /**
     * Release all acquired partial wakelocks, regardless of source.
     */
    public void wakelockPartialReleaseAll() {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "wakelockPartialReleaseAll");
        while (wakeLockPartial.isHeld()) {
            wakeLockPartial.release();
        }
    }

    /**
     * Called from UI by remote thread to inform service that account details have changed and to restart the stack.
     */
    void accountChangesCommit() {
        try {
            runOnServiceThread(new Runnable() {
                @Override
                public void run() {
                    uiMessageDismissByType(InAppNotifications.TYPE_SETTINGS);
                    showAlternateLauncherIcons(localStore.getBoolean(Prefs.KEY_SHOW_ALTERNATE_LAUNCHERS, false));
                    queueClearAll();
                    stackRestart();
                    if (isNetworkAvailable())
                        accountsRegisterAll(true);
                    pushEventOnSettingsChanged();
                }
            });
        } catch (ExecutionException e) {
            if (DEBUG) e.printStackTrace();
        } catch (InterruptedException e) {
            if (DEBUG) e.printStackTrace();
        }
    }

    /**
     * Enable or disable registration on all accounts
     *
     * @param enable
     */
    void accountsRegisterAll(final boolean enable) {
        if (sipAccounts.isEmpty()) return;
        for (SipAccount sipAccount : sipAccounts) {
            if (sipAccount.isEnabled()) try {
                sipAccount.setRegistration(enable); // enable == renew
            } catch (Exception e) {
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, e);
            }
        }
    }

    void audioRouteSpeakerToggle() {
        try {
            runOnServiceThread(new Runnable() {
                @Override
                public void run() {
                    if (audioRoute == AUDIO_ROUTE_EAR) setAudioRoute(AUDIO_ROUTE_SPEAKER);
                    else if (audioRoute == AUDIO_ROUTE_SPEAKER) setAudioRoute(AUDIO_ROUTE_EAR);
                }
            });
        } catch (ExecutionException e) {
            if (DEBUG) e.printStackTrace();
        } catch (InterruptedException e) {
            if (DEBUG) e.printStackTrace();
        }
    }

    /**
     * Answer incoming call
     *
     * @param callId
     * @return
     */
    boolean callAnswer(final int callId) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, callId);
        if (!isStackReady()) return false;
        boolean result;
        try {
            result = runBlockingForValue(pjsipExec, new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    SipCall sipCall = sipCalls.get(callId);
                    if (sipCall == null) {
                        if (DEBUG) gLog.l(TAG, Logger.lvDebug, "Callid not found " + callId);
                        if (mStat != null) mStat.onCallAnswerFailed();
                        return false;
                    }
                    boolean result = sipCall.putAnswer();
                    if (mStat != null) mStat.onCallAnswered();
                    notificationController.updateOnCallState(callId);
                    return result;
                }
            });
        } catch (Exception e) {
            gLog.l(TAG, Logger.lvError, e);
            if (mStat != null) mStat.onCallAnswerFailed();
            return false;
        }
        pushEventOnCallStateChanged(callId);
        return result;
    }

    boolean callDecline(final int callId) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, callId);
        if (!isStackReady()) return false;
        boolean result;
        try {
            result = runBlockingForValue(pjsipExec, new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    SipCall sipCall = sipCalls.get(callId);
                    if (sipCall == null) {
                        if (DEBUG) gLog.l(TAG, Logger.lvDebug, "Callid not found " + callId);
                        if (mStat != null) mStat.onCallDeclineFailed();
                        return false;
                    }
                    sipCall.setWasDeclined(true);
                    if (mStat != null) mStat.onCallDeclined();
                    notificationController.cancelNotificationById(NotificationController.TAG_INCOMING, callId);
                    return sipCall.putDecline();
                }
            });
        } catch (Exception e) {
            gLog.l(TAG, Logger.lvError, e);
            if (mStat != null) mStat.onCallDeclineFailed();
            return false;
        }
        pushEventOnCallStateChanged(callId);
        return result;
    }

    boolean callHangup(final int callId) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, callId);
        if (!isStackReady()) return false;
        boolean result;
        try {
            result = runBlockingForValue(pjsipExec, new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    SipCall sipCall = sipCalls.get(callId);
                    if (sipCall == null) {
                        if (DEBUG) gLog.l(TAG, Logger.lvDebug, "Callid not found " + callId);
                        return false;
                    }
                    notificationController.cancelNotificationById(NotificationController.TAG_ONGOING, callId);
                    return sipCall.putHangup();
                }
            });
        } catch (Exception e) {
            gLog.l(TAG, Logger.lvError, e);
            return false;
        }
        pushEventOnCallStateChanged(callId);
        return result;
    }

    boolean callHold(final int callId) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, callId);
        if (!isStackReady()) return false;
        boolean result;
        try {
            result = runBlockingForValue(pjsipExec, new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    SipCall sipCall = sipCalls.get(callId);
                    if (sipCall == null) {
                        if (DEBUG) gLog.l(TAG, Logger.lvDebug, "Callid not found " + callId);
                        return false;
                    }
                    boolean result = sipCall.putHold();
                    notificationController.updateOnCallState(sipCall.getId());
                    return result;
                }
            });
        } catch (Exception e) {
            gLog.l(TAG, Logger.lvError, e);
            return false;
        }
        pushEventOnCallStateChanged(callId);
        return result;
    }

    boolean callHoldResume(final int callId) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, callId);
        if (!isStackReady()) return false;

        boolean result;
        try {
            result = runBlockingForValue(pjsipExec, new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    SipCall sipCall = sipCalls.get(callId);
                    if (sipCall == null) {
                        if (DEBUG) gLog.l(TAG, Logger.lvDebug, "Callid not found " + callId);
                        return false;
                    }
                    boolean b = sipCall.putHoldResume();
                    notificationController.updateOnCallState(sipCall.getId());
                    return b;
                }
            });
        } catch (Exception e) {
            gLog.l(TAG, Logger.lvError, e);
            return false;
        }
        pushEventOnCallStateChanged(callId);
        pushEventOnCallMuteStateChanged(callId);
        return result;
    }

    boolean callMute(final int callId) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, callId);
        boolean result;
        try {
            result = runBlockingForValue(pjsipExec, new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    SipCall sipCall = sipCalls.get(callId);
                    if (sipCall == null) {
                        if (DEBUG) gLog.l(TAG, Logger.lvDebug, "Callid not found " + callId);
                        return false;
                    }
                    boolean result = sipCall.putMute(true);
                    //TODO update notification to show call mute status
                    return result;
                }
            });
        } catch (Exception e) {
            gLog.l(TAG, Logger.lvError, e);
            return false;
        }
        pushEventOnCallMuteStateChanged(callId);
        return result;
    }

    boolean callMuteResume(final int callId) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, callId);
        if (!isStackReady()) return false;
        boolean result;
        try {
            result = runBlockingForValue(pjsipExec, new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    SipCall sipCall = sipCalls.get(callId);
                    if (sipCall == null) {
                        if (DEBUG) gLog.l(TAG, Logger.lvDebug, "Callid not found " + callId);
                        return false;
                    }
                    boolean result = sipCall.putMute(false);
                    //TODO update notification to show call mute status
                    return result;
                }
            });
        } catch (Exception e) {
            gLog.l(TAG, Logger.lvError, e);
            return false;
        }
        pushEventOnCallMuteStateChanged(callId);
        return result;
    }

//    void debugConnect() {
//        if (DEBUG) gLog.l(TAG ,Logger.lvVerbose);
//        SipAccount sipAccount = new SipAccount(getInstance(), null);
//        sipAccounts.add(sipAccount);
//        sipAccount.init();
//    }

    /**
     * Initiate a call on the specific sipAccount
     *
     * @param uri
     * @param sipAccount
     * @return
     */
    SipCall callOut(final String uri, final SipAccount sipAccount) {
        if (!isStackReady()) return null;
        SipCall callResult = null;
        try {
            callResult = runBlockingForValue(pjsipExec, new Callable<SipCall>() {
                @Override
                public SipCall call() throws Exception {
                    SipCall sipCall = new SipCall(sipAccount);
                    try {
                        int pjsipCallId = sipCall.putCall(uri);
                        if (pjsipCallId < 0) {
                            final CallInfo info = sipCall.getInfo();
                            if (info != null) {
                                final String lastReason = info.getLastReason();
                                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, lastReason);
                                if (mStat != null) mStat.onCallOutFailed();
                                info.delete();
                            }
                            sipCall.delete();
                            return sipCall; // call failed
                        }
                        sipCalls.put(pjsipCallId, sipCall); // insert into call registry
                        if (mStat != null) mStat.onCallOut();
                        notificationController.updateOnCallState(pjsipCallId); // create notification
                        return sipCall;
                    } catch (Exception e) {
                        gLog.l(TAG, Logger.lvError, e);
                        return sipCall;
                    }
                }
            });
        } catch (Exception e) {
            gLog.l(TAG, Logger.lvError, e);
            if (mStat != null) mStat.onCallOutFailed();
            return null;
        }
        pushEventOnCallStateChanged(callResult.getId());
        return callResult;
    }

    void debugDisconnect() {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "debugDisconnect");
    }

    boolean debugIsThreadRegistered() {
        return sipEndpoint.isPjsipThread();
    }

    LicensingManager getLicensing() {
        return mLicensing;
    }

    NotificationController getNotificationController() {
        return notificationController;
    }

    Context getServiceContext() {
        return this;
    }

    SipAccount getSipAccountById(final int id) {
        for (SipAccount sipAccount : sipAccounts) {
            if (sipAccount.getId() == id) return sipAccount;
        }
        return null;
    }

    SipCall getSipCallById(int callId) {
        return sipCalls.get(callId);
    }

    String getSipCallExtension(final int callId) {
        String ext = null;
        try {
            ext = runBlockingForValue(pjsipExec, new Callable<String>() {
                @Override
                public String call() throws Exception {
                    SipCall sipCall = getSipCallById(callId);
                    if (sipCall != null) {
                        return sipCall.getExtension();
                    }
                    return null;
                }
            });
        } catch (ExecutionException e) {
            if (DEBUG) e.printStackTrace();
        } catch (InterruptedException e) {
            if (DEBUG) e.printStackTrace();
        }
        return ext;
    }

    String getSipCallRemoteDisplayName(final int callId) {
        String ext = null;
        try {
            ext = runBlockingForValue(pjsipExec, new Callable<String>() {
                @Override
                public String call() throws Exception {
                    SipCall sipCall = getSipCallById(callId);
                    if (sipCall != null) {
                        return sipCall.getRemoteDisplayName();
                    }
                    return null;
                }
            });
        } catch (ExecutionException e) {
            if (DEBUG) e.printStackTrace();
        } catch (InterruptedException e) {
            if (DEBUG) e.printStackTrace();
        }
        return ext;
    }

    SipEndpoint getSipEndpoint() {
        return sipEndpoint;
    }

    boolean hasActiveCalls() {
        for (SipCall sipCall : sipCalls.values()) {
            if (sipCall.getSipState() == SipCall.SIPSTATE_ACCEPTED ||
                    sipCall.getSipState() == SipCall.SIPSTATE_HOLD ||
                    sipCall.getSipState() == SipCall.SIPSTATE_PROGRESS_IN ||
                    sipCall.getSipState() == SipCall.SIPSTATE_PROGRESS_OUT) return true;
        }
        return false;
    }

    void insertCall(final int sipCallId, final SipCall sipCall) {
        sipCalls.put(sipCallId, sipCall);
    }

    boolean isAccountRegActive(int accountId) {
        SipAccount sipAccount = sipAccounts.get(accountId);
        if (sipAccount == null) return false;
        try {
            AccountInfo info = sipAccount.getInfo();
            if (info == null) return false;
            return info.getRegIsActive();

        } catch (Exception e) {
            if (DEBUG) e.printStackTrace();
            return false;
        }
    }

    boolean isBlueoothScoAvailable() {
        boolean avail = false;
        try {
            avail = runBlockingForValue(pjsipExec, new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return audioManager.isBluetoothScoAvailableOffCall();
                }
            });
        } catch (ExecutionException e) {
            if (DEBUG) e.printStackTrace();
        } catch (InterruptedException e) {
            if (DEBUG) e.printStackTrace();
        }
        return avail;
    }

    boolean isNetworkAvailable() {
        return isNetworkAvailable;
    }

    void msgNewOutgoingCall(String number) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, number);
    }

    /**
     * Called by SipAccount
     *
     * @param accountId
     */
    void onAccountRegStateChanged(int accountId) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, accountId);
    }

    /**
     * Called by PJSIP Call when call is disconnected
     *
     * @param pjsipCallId
     */
    void onCallEventDisconnected(final int pjsipCallId) {
        pushEventOnCallStateChanged(pjsipCallId);
        try {
            runOnExecThread(pjsipExec, new Runnable() {
                @Override
                public void run() {
                    sipCalls.get(pjsipCallId).delete();
                }
            });
        } catch (Exception e) {
            gLog.l(TAG, Logger.lvError, e);
        }
        sipCalls.remove(pjsipCallId);
        notificationController.cancelNotificationById(NotificationController.TAG_INCOMING, pjsipCallId);
        notificationController.cancelNotificationById(NotificationController.TAG_ONGOING, pjsipCallId);
    }

    void onCallEventStateChanged(final int pjsipCallId) {
        pushEventOnCallStateChanged(pjsipCallId);
    }

    /**
     * Called by PJSIP Account on incoming call.
     */
    void onCallIncoming(final int sipCallId) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "onCallIncoming");
        final SipCall sipCall = getSipCallById(sipCallId);
        try { // do not block or a deadlock will occur. (PJSIP thread blocking on itself)
            runOnExecThread(pjsipExec, new Runnable() {
                @Override
                public void run() {
//                    sipCalls.put(sipCall.getId(), sipCall);
                    sipCall.putProgressIn();
                    notificationController.updateOnCallState(sipCall.getId());
                }
            });
        } catch (Exception e) {
            gLog.l(TAG, Logger.lvError, e);
        }
        pushEventOnCallStateChanged(sipCall.getId());
    }

    void populateAccounts() {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "populateAccounts");
        SipAccount sipAccount = new SipAccount(getInstance(), null);
        sipAccounts.add(sipAccount);
        int result = sipAccount.initAccountFromPref(0);
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "initAccountFromPref " + result);
        switch (result) {
            case SipAccount.ACCOUNT_CREATE_ERR_NOT_ENABLED:
                sipAccounts.remove(sipAccount);
                UiMessagesCommon.showSetupRequired(this);
                return;
            case SipAccount.ACCOUNT_CREATE_ERR_NOT_VALID:
                sipAccounts.remove(sipAccount);
                UiMessagesCommon.showSettingsProblem(this);
                return;
        }
        try {
            AccountInfo info = sipAccount.getInfo();
            gLog.l(TAG, Logger.lvDebug, info.getRegLastErr());
        } catch (Exception e) {
            if (DEBUG) e.printStackTrace();
        }
    }

    void pushEventOnCallMuteStateChanged(int pjsipCallId) {
        synchronized (eventSubs) {
            for (SipServiceEvents subs : eventSubs) {
                if (subs != null) subs.onCallMuteStateChanged(pjsipCallId);
            }
        }
        LinkedList<IRemoteSipServiceEvents> deadRemoteSubs = null;
        synchronized (eventSubsRemote) {
            for (IRemoteSipServiceEvents subsr : eventSubsRemote) {
                if (subsr != null) try {
                    subsr.onCallMuteStateChanged(pjsipCallId);
                } catch (RemoteException e) {
                    if (deadRemoteSubs == null) deadRemoteSubs = new LinkedList<>();
                    deadRemoteSubs.add(subsr);
                    if (DEBUG) e.printStackTrace();
                }
            }
            if (deadRemoteSubs != null) for (IRemoteSipServiceEvents deadRemoteSub : deadRemoteSubs) {
                eventSubsRemote.remove(deadRemoteSub);
            }
        }
    }

    void pushEventOnCallStateChanged(int pjsipCallId) {
        synchronized (eventSubs) {
            for (SipServiceEvents subs : eventSubs) {
                if (subs != null) subs.onCallStateChanged(pjsipCallId);
            }
        }
        LinkedList<IRemoteSipServiceEvents> deadRemoteSubs = null;
        synchronized (eventSubsRemote) {
            for (IRemoteSipServiceEvents subsr : eventSubsRemote) {
                if (subsr != null) try {
                    subsr.onCallStateChanged(pjsipCallId);
                } catch (RemoteException e) {
                    if (deadRemoteSubs == null) deadRemoteSubs = new LinkedList<>();
                    deadRemoteSubs.add(subsr);
                    if (DEBUG) e.printStackTrace();
                }
            }
            if (deadRemoteSubs != null) for (IRemoteSipServiceEvents deadRemoteSub : deadRemoteSubs) {
                eventSubsRemote.remove(deadRemoteSub);
            }

        }
        updateAudioState();
    }

    void pushEventOnExit() {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "pushEventOnExit");
        synchronized (eventSubsRemote) {
            for (IRemoteSipServiceEvents subsr : eventSubsRemote) {
                if (subsr != null) try {
                    subsr.onExit();
                } catch (RemoteException e) {
                    if (DEBUG) e.printStackTrace();
                }
            }
            eventSubsRemote.clear();
        }
    }

    void pushEventOnLicenseStateChanged(Bundle licenseData) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "pushEventOnLicenseStateChanged");
        LinkedList<IRemoteSipServiceEvents> deadRemoteSubs = null;
        synchronized (eventSubsRemote) {
            for (IRemoteSipServiceEvents subsr : eventSubsRemote) {
                if (subsr != null) try {
                    subsr.onLicenseStateUpdated(licenseData);
                } catch (RemoteException e) {
                    if (deadRemoteSubs == null) deadRemoteSubs = new LinkedList<>();
                    deadRemoteSubs.add(subsr);
                    if (DEBUG) e.printStackTrace();
                }
            }
            if (deadRemoteSubs != null) for (IRemoteSipServiceEvents deadRemoteSub : deadRemoteSubs) {
                eventSubsRemote.remove(deadRemoteSub);
            }
        }
    }

    void pushEventOnSettingsChanged() {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "pushEventOnSettingsChanged");
        LinkedList<IRemoteSipServiceEvents> deadRemoteSubs = null;
        synchronized (eventSubsRemote) {
            for (IRemoteSipServiceEvents subsr : eventSubsRemote) {
                if (subsr != null) try {
                    subsr.onSettingsChanged();
                } catch (RemoteException e) {
                    if (deadRemoteSubs == null) deadRemoteSubs = new LinkedList<>();
                    deadRemoteSubs.add(subsr);
                    if (DEBUG) e.printStackTrace();
                }
            }
            if (deadRemoteSubs != null) for (IRemoteSipServiceEvents deadRemoteSub : deadRemoteSubs) {
                eventSubsRemote.remove(deadRemoteSub);
            }
        }
    }

    void pushEventOnStackStateChanged(int stackState) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "pushEventOnStackStateChanged");
        synchronized (eventSubs) {
            for (SipServiceEvents subs : eventSubs) {
                if (subs != null) subs.onStackStateChanged(stackState);
            }
        }
        LinkedList<IRemoteSipServiceEvents> deadRemoteSubs = null;
        synchronized (eventSubsRemote) {
            for (IRemoteSipServiceEvents subsr : eventSubsRemote) {
                if (subsr != null) try {
                    subsr.onStackStateChanged(stackState);
                } catch (RemoteException e) {
                    if (deadRemoteSubs == null) deadRemoteSubs = new LinkedList<>();
                    deadRemoteSubs.add(subsr);
                    if (DEBUG) e.printStackTrace();
                }
            }
            if (deadRemoteSubs != null) for (IRemoteSipServiceEvents deadRemoteSub : deadRemoteSubs) {
                eventSubsRemote.remove(deadRemoteSub);
            }
        }
    }

    void pushMissedCall(final int pjsipCallId) {
        SipCall sipCall = sipCalls.get(pjsipCallId);
        if (sipCall == null) return;
        if (sipCall.isAccepted() || sipCall.isOutgoing()) return; // not an incoming missed call
        notificationController.createNotificationCallIncomingMissed(pjsipCallId);
        showDozeExemptRecommend(false);
    }

    /**
     * Call immediately after a call terminates, even if not accepted by either party, to log the call details to the Android telecom CallLog.
     *
     * @param pjsipCallId valid pjSipCallId
     */
    void pushToCallLog(final int pjsipCallId) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "pushToCallLog");
        SipCall sipCall = sipCalls.get(pjsipCallId);
        if (sipCall == null) return;
        // getStatCallStartTime = 0 when call was not accepted/answered
        long duration = sipCall.getStatCallStartTime() > 0 ?
                (sipCall.getStatCallStopTime() - sipCall.getStatCallStartTime()) / 1000 : 0;
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "duration " + duration);
        if (sipCall.isOutgoing()) {
            sipCallLogController.putCallLog(SipCallLogController.CALL_TYPE_OUTGOING, sipCall.getStatCallStartTime(), duration, null, sipCall.getExtension());
        } else {
            try {
                CallInfo info = sipCall.getInfo();
                if (info == null) return;
                pjsip_status_code lastStatusCode = info.getLastStatusCode();
                if (lastStatusCode == null) return;
                String contactDisplayName = UriSip.getContactDisplayName(info.getRemoteUri());
                if (sipCall.isAccepted())
                    sipCallLogController.putCallLog(SipCallLogController.CALL_TYPE_INCOMING, sipCall.getStatCallStartTime(), duration, contactDisplayName, sipCall.getExtension());
                else if (lastStatusCode == pjsip_status_code.PJSIP_SC_DECLINE)
                    sipCallLogController.putCallLog(SipCallLogController.CALL_TYPE_INCOMING_DECLINED, System.currentTimeMillis(), duration, contactDisplayName, sipCall.getExtension());
                else if (lastStatusCode == pjsip_status_code.PJSIP_SC_BUSY_HERE)
                    sipCallLogController.putCallLog(SipCallLogController.CALL_TYPE_INCOMING_BUSY, System.currentTimeMillis(), duration, contactDisplayName, sipCall.getExtension());
                else if (lastStatusCode == pjsip_status_code.PJSIP_SC_REQUEST_TERMINATED)
                    sipCallLogController.putCallLog(SipCallLogController.CALL_TYPE_INCOMING_MISSED, System.currentTimeMillis(), duration, contactDisplayName, sipCall.getExtension());
            } catch (Exception e) {
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, e);
            }
        }
    }

    /**
     * Call to initiate the orderly destruction of the service and the backing PJSip instance.
     */
    void queueExit() {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "queueExit");
        if (mStat != null) mStat.onQueueExit();
        if (isExitQueued) {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "already queued");
            return;
        }
        isExitQueued = true;
        queueClearAll();
        Message message = new Message();
        message.arg1 = SipServiceMessages.MSG_SERVICE_STOP;
        queueCommand(message);
    }

    void refreshRegistrationOnDoze() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        try {
            runOnServiceThread(new Runnable() {
                @SuppressLint("NewApi")
                @Override
                public void run() {
                    if (!powerManager.isDeviceIdleMode()) refreshRegistrationsIfRequired();
                }
            });
        } catch (ExecutionException e) {
        } catch (InterruptedException e) {
            gLog.l(TAG, Logger.lvError, e);
        }
    }

    synchronized void refreshRegistrationsIfRequired() {
        for (SipAccount sipAccount : sipAccounts) {
            sipAccount.refreshIfRequired();
        }
    }

    /**
     * Show app specific battery optimization exemption dialog.
     * Warning: the intent action is subject to Google Play Distribution policies and may lead to an app suspension if included in Play store releases.
     */
    void showBatteryExemptRequest() {
        gLog.l(TAG, Logger.lvDebug, "onReqBatterySaverExempt not supported in public releases");
//        if (Build.VERSION.SDK_INT < 23) return;
//        Intent intent = new Intent();
//        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        String packageName = getPackageName();
//        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
//            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
//            intent.setData(Uri.parse("package:" + packageName));
//            startActivity(intent);
//        }
    }

    /**
     * Push UI Message to all event subscribed remotes
     *
     * @param iAnType
     * @param bundle
     * @return service message id
     */
    int showUiMessage(int iAnType, Bundle bundle) {
        int i = uiMessageGetNextIndex(); // get next unique message identifier
        bundle.putInt(InAppNotifications.SMId, i); // tag bundle with msg id
        bundle.putInt(InAppNotifications.IAN_TYPE, iAnType); // tag bundle with ianType
        if (bundle.getBoolean(InAppNotifications.FLAG_STICKY, false)) {
            hmUiMessages.put(i, bundle); // retain message
        }
        synchronized (eventSubsRemote) {
            for (IRemoteSipServiceEvents r : eventSubsRemote) {
                try {
                    r.showMessage(iAnType, bundle);
                } catch (RemoteException e) {
                    if (DEBUG) {
                        gLog.l(TAG, Logger.lvVerbose, e);
                        e.printStackTrace();
                    }
                    return -1;
                }
            }
        }
        return i;
    }

    /**
     * Call to initiate the orderly destruction and initiation of a new PJSip instance. This method must be called from the main service thread.
     */
    void stackRestart() {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "stackRestart");
        stackStop();
        stackStart();
    }

    /**
     * Call to initiate the PJSip instance after previously called stackStop. This method must be called from the main service thread.
     */
    void stackStart() {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "stackStart");
        if (isStackReady()) return;
        if (sipEndpoint == null) sipEndpoint = new SipEndpoint();
        sipEndpoint.init();
//        debugConnect();
        populateAccounts();
        pushEventOnStackStateChanged(STACK_STATE_STARTED);
        setupDozeDisablerAsync();
    }

    /**
     * Call to initiate the orderly destruction of the PJSip instance. This method must be called from the main service thread.
     */
    void stackStop() {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "stackStop");
//        if (!isStackReady()) return;
        if (mStat != null) mStat.commitStats();
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "purging promo timer");
        if (timerPromo != null) {
            timerPromo.cancel();
            timerPromo.purge();
            timerPromo = null;
        }
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "purging rateus timer");
        if (timerRateus != null) {
            timerRateus.cancel();
            timerRateus.purge();
            timerRateus = null;
        }
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "stopping ringtone gen if active");
        if (ringTone != null && ringTone.isPlaying()) ringTone.stop();
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "canceling vibrator if active");
        if (vibrator != null) vibrator.cancel();
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "shutting down dozedisabler");
        if (dozeDisabler != null) {
            dozeDisabler.disable();
            dozeDisabler.destroy();
            dozeDisabler = null;
        }
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "canceling all notifications");
        notificationController.cancelNotificationAll();
        // purge accounts
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "purging accounts...");
        for (SipAccount sa : sipAccounts) {
            try {
                sa.setRegistration(false);
            } catch (Exception e) {
                if (DEBUG) e.printStackTrace();
            }
            sa.delete();
        }
        sipAccounts.clear();
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "accounts cleared.");
        sipEndpoint.destroy();
        generateServiceId();

        // purge calls
        for (SipCall sc : sipCalls.values()) {
            sc.delete();
        }
        sipCalls.clear();
        PjSipTimerWrapper.getInstance().purgeAll();
        if (localStore != null) localStore.commit(true);
        pushEventOnStackStateChanged(STACK_STATE_STOPPED);
    }

    void toast(final String text, final int timeLength) {
        if (handler == null) handler = new Handler(getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast toast = Toast.makeText(getApplicationContext(), text, timeLength);
                toast.show();
            }
        });
    }

    void toastLocal(final String key, final String defaultValue, final int timeLength) {
        if (handler == null) handler = new Handler(getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast toast = Toast.makeText(getApplicationContext(), i18n.getString(key, defaultValue), timeLength);
                toast.show();
            }
        });
    }

    void uiMessageDismiss(int smid) {
        Bundle bundle = hmUiMessages.get(smid);
        if (bundle != null) {
            if (bundle.getBoolean(InAppNotifications.FLAG_TOUCH_TS_PROMO, false))
                tsPromoDismissed = System.currentTimeMillis();
            if (bundle.getBoolean(InAppNotifications.FLAG_TOUCH_TS_RATEUS, false))
                tsRateusDismissed = System.currentTimeMillis();
            if (bundle.containsKey(InAppNotifications.KEY_PREF_ON_DISMISS_BOOL))
                localStore.setBoolean(bundle.getString(InAppNotifications.KEY_PREF_ON_DISMISS_BOOL), true);
            hmUiMessages.remove(smid);
        }
        synchronized (eventSubsRemote) {
            for (IRemoteSipServiceEvents r : eventSubsRemote) {
                try {
                    r.clearMessageBySmid(smid);
                } catch (RemoteException e) {
                    if (DEBUG) {
                        gLog.l(TAG, Logger.lvVerbose, e);
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    void uiMessageDismissByType(int iAnType) {
        synchronized (eventSubsRemote) {
            for (IRemoteSipServiceEvents r : eventSubsRemote) {
                try {
                    r.clearMessageByType(iAnType);
                } catch (RemoteException e) {
                    if (DEBUG) {
                        gLog.l(TAG, Logger.lvVerbose, e);
                        e.printStackTrace();
                    }
                }
            }
        }
        for (Map.Entry<Integer, Bundle> b : hmUiMessages.entrySet()) {
            if (b.getValue().getInt(InAppNotifications.IAN_TYPE) == iAnType) hmUiMessages.remove(b.getKey());
        }
    }

    /**
     * Acquire or release Wifi radio lock when network connection is ConnectivityManager.TYPE_WIFI
     */
    void updateWifiLockState() {
        boolean isMobileConnected = false;
        boolean isWifiConnected = false;
        Network[] allNetworks = connectivityManager.getAllNetworks();
        for (Network n : allNetworks) {
            NetworkInfo networkInfo = connectivityManager.getNetworkInfo(n);
            if (networkInfo.getType() == ConnectivityManager.TYPE_MOBILE)
                isMobileConnected = networkInfo.isConnectedOrConnecting();
            if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI)
                isWifiConnected = networkInfo.isConnectedOrConnecting();
        }
        if (isMobileConnected && !isWifiConnected) wifiLock(false); // mob only - no wifi lock
        else if (!isMobileConnected && isWifiConnected) wifiLock(true); // wifi only - wifi lock
        else if (!isMobileConnected && !isWifiConnected) wifiLock(false); // neither mob nor wifi - no wifi lock
        else if (isMobileConnected && isWifiConnected) wifiLock(false); // both mob and wifi - no wifi lock
    }

    private void clearPromo() {
        if (mLicensing == null && !mLicensing.isPrem()) return;
        uiMessageDismissByType(InAppNotifications.TYPE_PROMO_1);
    }

    /**
     * Do all first run initialization here
     */
    private void firstRunSetup() {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "firstRunSetup");
        Prefs.initPrefs(localStore);
    }

    private void generateServiceId() {
        serviceId = (new Random()).nextInt(999999999);
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "serviceId " + serviceId);
    }

    private void setupAndroidAudio() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // Note the optimal sample rate may reduce audio back end latency however, but will rarely match the sip codec rate in use
        // sample rate conversions at the codec may be more CPU intensive than the Android audio backend where it is often hardware assisted
        String sampleRateString = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        sampleRateOptimal = sampleRateString == null ? 16000 : Integer.parseInt(sampleRateString);

        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "isBlueoothScoAvailable " + isBlueoothScoAvailable());
        setAudioRoute(AUDIO_ROUTE_EAR);

        audioManager.registerMediaButtonEventReceiver(new ComponentName(getPackageName(), MediaSessionHandler.class.getName()));
    }

    private void setupDozeDisablerAsync() {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "setupDozeDisablerAsync");
        if (localStore.getBoolean(Prefs.KEY_DOZE_DISABLE, false) || localStore.getBoolean(Prefs.KEY_DOZE_DISABLE_SU, false))
            new Thread(new Runnable() {
                @Override
                public void run() {
                    dozeDisabler = new DozeDisabler(SipService.getInstance());
                    if (!(dozeDisabler.isRootAvaialble() || dozeDisabler.hasPermissionDump())) {
                        UiMessagesCommon.showDozeReqPerm(getInstance());
                        return;
                    }
                    boolean result = dozeDisabler.enable(localStore.getBoolean(Prefs.KEY_DOZE_DISABLE_SU, false));
                    if (!result) {
                        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "Doze disable: failed");
                        UiMessagesCommon.showDozeDisableFailed(getInstance());
                    }
                }
            }).start();
    }

    private void setupGoogleBilling() {
    }

    /**
     * Must be called from a looper thread
     */
    private void setupMediaButtonHandler() {
        mediaSession = new MediaSession(this, "sip_mediasession");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        playbackStateBuilder = new PlaybackState.Builder();
        playbackStateBuilder.setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS);
        playbackStateBuilder.setState(PlaybackState.STATE_STOPPED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1);
        mediaSession.setPlaybackState(playbackStateBuilder.build());
        mediaSession.setCallback(new MediaSessionHandler());
        mediaSession.setActive(true);
    }

    /**
     * Create the {@link Timer} that periodically checks to see the user meets criteria to be shown RateUs related UiMessages
     */
    private void setupTimerRateus() {
        final long rateLocalTs = localStore.getLong(Prefs.KEY_FLAG_RATE_LOCAL_TS, 0L);
        final long rateLocal = localStore.getInt(Prefs.KEY_FLAG_RATE_LOCAL, 0);
        final long ratePlayappTs = localStore.getLong(Prefs.KEY_FLAG_RATE_PLAYAPP_TS, 0L);

        if ((rateLocalTs != 0L && ratePlayappTs != 0L) || (rateLocalTs != 0L && rateLocal <= 3)) {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "Skip TimerRateUs ");
//            if (DEV) {
//                localStore.delKey(Prefs.KEY_FLAG_RATE_LOCAL_TS);
//                localStore.delKey(Prefs.KEY_FLAG_RATE_LOCAL);
//                localStore.delKey(Prefs.KEY_FLAG_RATE_PLAYAPP_TS);
//                localStore.commit(true);
//                gLog.l(TAG, Logger.lvVerbose, "FLAG_RATE cleared");
//            }
            return;
        }

        if (timerRateus == null) timerRateus = new Timer();
        timerRateus.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                showRateusLocal(false);
                showRateusGooglePlay(false);
                final long rateLocalTs = localStore.getLong(Prefs.KEY_FLAG_RATE_LOCAL_TS, 0L);
                final long rateLocal = localStore.getInt(Prefs.KEY_FLAG_RATE_LOCAL, 0);
                final long ratePlayappTs = localStore.getLong(Prefs.KEY_FLAG_RATE_PLAYAPP_TS, 0L);
                if ((rateLocalTs != 0L && ratePlayappTs != 0L) || (rateLocalTs != 0L && rateLocal <= 3)) {
                    if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "Terminating TimerRateUs");
                    timerRateus.cancel();
                    timerRateus = null;
//                    if (DEV) {
//                        localStore.delKey(Prefs.KEY_FLAG_RATE_LOCAL_TS);
//                        localStore.delKey(Prefs.KEY_FLAG_RATE_LOCAL);
//                        localStore.delKey(Prefs.KEY_FLAG_RATE_PLAYAPP_TS);
//                        localStore.commit(true);
//                        gLog.l(TAG, Logger.lvVerbose, "FLAG_RATE cleared");
//                    }
                }
            }
        }, RATEUS_PRE_DELAY, RATEUS_INTERVAL);
    }

    private void showAlternateLauncherIcons(boolean enable) {
        PackageManager pm = getApplicationContext().getPackageManager();
        String[] alternateIconAliases = new String[]{".alt_launcher_2", ".alt_launcher_3", ".alt_launcher_4"};
        for (String cname : alternateIconAliases) {
            ComponentName componentName = new ComponentName(getPackageName(), getPackageName() + cname);
            pm.setComponentEnabledSetting(componentName, enable ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }


    }

    private void showBatterySaverUim(boolean force) {
        if (force) {
            UiMessagesCommon.showBatterySaveModeEnabled(getInstance());
            return;
        }
        if (!powerManager.isPowerSaveMode()) return;
        UiMessagesCommon.showBatterySaveModeEnabled(getInstance());
    }

    /**
     * Insert battery optimization exemption recommendation into ui messages
     *
     * @param force
     */
    private void showDozeExemptRecommend(boolean force) {
        if (Build.VERSION.SDK_INT < 23) return;
        if (powerManager.isIgnoringBatteryOptimizations(getPackageName())) return;
        UiMessagesCommon.showDozeWhitelistRecommend(getInstance());
    }

    private void showInCallUiAsNewTask(int pjsipCallId) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "showInCallUiAsNewTask");
        Intent intent = new Intent(this, InCallActivity.class);
        intent.putExtra(InCallActivity.EXTRA_BIND_PJSIPCALLID, pjsipCallId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    /**
     * Insert promo into ui messages if interval has expired, and primary account is enabled, and minimum calls made.
     *
     * @param force
     */
    private void showPromo(boolean force) {
        if (force) UiMessagesCommon.showProAd(getInstance());
        else if (mLicensing != null && !mLicensing.isPrem())
            if (System.currentTimeMillis() - tsPromoDismissed > PROMO_INTERVAL
                    && localStore.getBoolean(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_ENABLED), false))
                if (mStat != null) {
                    final long callCount = mStat.getCountCallAnswered() + mStat.getCountCallDeclined() + mStat.getCountCallOut();
                    if (callCount > PROMO_CALL_COUNT_THRESHOLD) UiMessagesCommon.showProAd(getInstance());
                    else if (DEBUG)
                        gLog.l(TAG, Logger.lvVerbose, "Skip showPromo, below PROMO_CALL_COUNT_THRESHOLD " + callCount);
                } else UiMessagesCommon.showProAd(getInstance());
    }

    /**
     * Insert RateusGooglePlay into ui messages if applicable.
     *
     * @param force
     */
    private void showRateusGooglePlay(boolean force) {
        if (force) {
            UiMessagesCommon.showRateUsGooglePlay(getInstance());
            return;
        }
        if (localStore.getLong(Prefs.KEY_FLAG_RATE_LOCAL_TS, 0l) == 0l) return; // local rating not yet completed
        if (localStore.getInt(Prefs.KEY_FLAG_RATE_LOCAL, 0) <= 3) return; // local rating threshold not met
        if (localStore.getLong(Prefs.KEY_FLAG_RATE_PLAYAPP_TS, 0l) != 0l)
            return; // Google Play click through previously completed
        if (mLicensing == null || !mLicensing.isPrem()) return; // licencing not present or not premium
        if (System.currentTimeMillis() - tsRateusDismissed < RATEUS_INTERVAL) return; // too soon since last view
        if (!localStore.getBoolean(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_ENABLED), false))
            return; // primary account not enabled
        UiMessagesCommon.showRateUsGooglePlay(getInstance());
    }

    /**
     * Insert RateusLocal into ui messages if applicable.
     *
     * @param force
     */
    private void showRateusLocal(boolean force) {
        if (force) {
            UiMessagesCommon.showRateUsLocal(getInstance());
            return;
        }

        if (localStore.getLong(Prefs.KEY_FLAG_RATE_LOCAL_TS, 0l) != 0l) return; // local rating already completed
        if (mLicensing == null || !mLicensing.isPrem()) return; // licencing not present or not premium
        if (System.currentTimeMillis() - tsRateusDismissed < RATEUS_INTERVAL) return; // too soon since last view
        if (!localStore.getBoolean(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_ENABLED), false))
            return; // primary account not enabled
        UiMessagesCommon.showRateUsLocal(getInstance());
    }

    /**
     * Insert ui tip find background call if not yet swiped away
     *
     * @param force
     */
    private void showTipFindCall(boolean force) {
        if (force) UiMessagesCommon.showInCallTipFindCall(getInstance(), Prefs.KEY_TIP_SEEN_FIND_CALL);
        else if (!localStore.getBoolean(Prefs.KEY_TIP_SEEN_FIND_CALL, false)) {
            UiMessagesCommon.showInCallTipFindCall(getInstance(), Prefs.KEY_TIP_SEEN_FIND_CALL);
        }
    }

    private synchronized int uiMessageGetNextIndex() {
        return uiMessageKeyIndex++;
    }

    /**
     * Sets the AudioMode, AudioFocus and PlayBackState based on active call(s).
     */
    private void updateAudioState() {
        boolean activeCall = false;
        Enumeration<SipCall> elements = sipCalls.elements();
        while (elements.hasMoreElements()) {
            SipCall sipCall = elements.nextElement();
            switch (sipCall.getSipState()) {
                case SipCall.SIPSTATE_ACCEPTED:
                case SipCall.SIPSTATE_HOLD:
                    activeCall = true;
            }
        }
        if (DEBUG) if (activeCall) gLog.l(TAG, Logger.lvVerbose, "MODE_IN_COMMUNICATION");
        else gLog.l(TAG, Logger.lvVerbose, "MODE_NORMAL");
        if (activeCall) audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        else audioManager.setMode(AudioManager.MODE_NORMAL);

        // grab audio focus - STREAM_VOICE_CALL should automatically pause any currently playing audio
        if (activeCall) {
            audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN);
        } else {
            audioManager.abandonAudioFocus(audioFocusChangeListener);
        }

        if (activeCall) {
            playbackStateBuilder.setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1);
            mediaSession.setPlaybackState(playbackStateBuilder.build());
        } else {
            playbackStateBuilder.setState(PlaybackState.STATE_STOPPED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1);
            mediaSession.setPlaybackState(playbackStateBuilder.build());
        }


    }

    private void wifiLock(boolean acquire) {
        if (acquire && localStore.getBoolean(Prefs.KEY_WIFI_LOCK_ENABLE, false)) {
            wifilocksipservice.acquire();
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "acquired");
        } else {
            if (wifilocksipservice.isHeld()) {
                wifilocksipservice.release();
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "released");
            }
        }
    }

    class MediaSessionHandler extends MediaSession.Callback {

        @Override
        public void onPlay() {
            Log.v("mscb", "onPlay");
            try {
                runOnServiceThread(new Runnable() {
                    @Override
                    public void run() {
                        if (hasActiveCalls()) { // ongoing active call
                            if (audioManager.isBluetoothScoAvailableOffCall() && !audioManager.isBluetoothScoOn()) {
                                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "setAudioRoute AUDIO_ROUTE_BLUETOOTH");
                                setAudioRoute(SipService.AUDIO_ROUTE_BLUETOOTH);
                            } else if (DEBUG) {
                                // else not needed, BTSCO automatically disconnects itself on hookbutton pressed
                                gLog.l(TAG, Logger.lvVerbose, "isBluetoothScoAvailableOffCall " + audioManager.isBluetoothScoAvailableOffCall());
                                gLog.l(TAG, Logger.lvVerbose, "isBluetoothScoOn " + audioManager.isBluetoothScoOn());
                            }
                        } else {
                            int sipCallId = getSipCallActiveMostRecent();
                            if (sipCallId >= 0) {
                                switch (getSipCallState(sipCallId)) {
                                    case SipCall.SIPSTATE_PROGRESS_IN: // aka ringing in or out
                                    case SipCall.SIPSTATE_PROGRESS_OUT: // aka ringing in or out
                                        callAnswer(sipCallId);
                                        setAudioRoute(AUDIO_ROUTE_BLUETOOTH);
                                        break;
//                        case SipCall.SIPSTATE_ACCEPTED: // active call
//                            callHold(sipCallId);
//                            break;
//                        case SipCall.SIPSTATE_HOLD: // active call on hold
//                            callHoldResume(sipCallId);
//                            break;
                                }

                            } // else nohting to do...
                        }
                    }
                });
            } catch (ExecutionException e) {
                if (DEBUG) e.printStackTrace();
            } catch (InterruptedException e) {
                if (DEBUG) e.printStackTrace();
            }

        }

        @Override
        public void onSkipToNext() {
            Log.v("mscb", "onSkipToNext");
            ringAndVibe(false);
        }

        // We ignore onPause and onStop because VOIP shares the same exclusive use case as an actual phone call

        @Override
        public void onSkipToPrevious() {
            Log.v("mscb", "onSkipToPrevious");
            ringAndVibe(false);
        }

        private int getSipCallActiveMostRecent() {
            int returnVal = -1;
            Enumeration<SipCall> elements = sipCalls.elements();
            while (elements.hasMoreElements()) {
                SipCall sipCall = elements.nextElement();
                switch (sipCall.getSipState()) {
                    case SipCall.SIPSTATE_PROGRESS_IN:
                    case SipCall.SIPSTATE_PROGRESS_OUT:
                    case SipCall.SIPSTATE_ACCEPTED:
                    case SipCall.SIPSTATE_HOLD:
                        returnVal = sipCall.getId();
                }
            }
            return returnVal;
        }
    }

    class RelinkerLoadListener implements ReLinker.LoadListener {

        @Override
        public void success() {

        }

        @Override
        public void failure(Throwable t) {
            gLog.l(TAG, Logger.lvError, "Failed to load native library!");
            nativeLibLoadFailure = true;
        }
    }

    /**
     * ThreadFactory for PJSIP Executor
     * Simple thread naming and reference keeping.
     */
    class ExecutorThreadFactory implements ThreadFactory {
        Thread pjsipthread;

        @Override
        public Thread newThread(Runnable r) {
            if (pjsipthread == null || !pjsipthread.isAlive()) pjsipthread = new Thread(r, "pjsipThread");
            return pjsipthread;
        }
    }

    class LicensingListener implements ILicensingManagerEvents {

        @Override
        public void onPurchaseResult() {
            if (mLicensing.isPrem()) clearPromo();
            pushEventOnLicenseStateChanged(mLicensing.getStateBundle());
        }

        @Override
        public void onState(int state) {
            gLog.l(TAG, Logger.lvVerbose, "State: " + state);
            if (state == LicensingManager.STATE_OK) {
                gLog.l(TAG, Logger.lvVerbose, "Premium: " + mLicensing.isPrem());
                gLog.l(TAG, Logger.lvVerbose, "Patron: " + mLicensing.isPatron());
                if (mLicensing.isPrem()) {
                    clearPromo();
                    setupTimerRateus();
                }
            }
            pushEventOnLicenseStateChanged(mLicensing.getStateBundle());
        }
    }

}
