/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp;

import android.app.ActionBar;
import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.RemoteException;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.meowsbox.vosp.common.Logger;
import com.meowsbox.vosp.service.Prefs;
import com.meowsbox.vosp.service.SipCall;
import com.meowsbox.vosp.service.SipService;
import com.meowsbox.vosp.service.SipServiceEvents;
import com.meowsbox.vosp.service.licensing.LicensingManager;
import com.meowsbox.vosp.widget.DialogCallRecordLegalConfirm;
import com.meowsbox.vosp.widget.DialogPremiumUpgrade;
import com.meowsbox.vosp.widget.FloatingActionButtonController;
import com.meowsbox.vosp.widget.UiMessageController;

import java.util.Timer;
import java.util.TimerTask;

import static android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK;

public class InCallActivity extends Activity implements ServiceBindingController.ServiceConnectionEvents, SipServiceEvents, View.OnClickListener, CallButtonFragment.InteractionListener, AnswerFragment.InteractionListener {
    public final static boolean DEBUG = DialerApplication.DEBUG;
    public final static String EXTRA_BIND_PJSIPCALLID = "bind_pjsipcallid";
    private final static long AFTER_CALL_END_DELAY = 1000;
    public final String TAG = this.getClass().getName();
    public Logger gLog;
    Timer timerCallEnd = new Timer();
    boolean isFinishQueued = false;
    CallButtonFragment callButtonFragment;
    View mDialpadContainer;
    FragmentInCallDialpad fragmentDialpad;
    View mAnswerFragment;
    private ServiceBindingController mServiceController = null;
    private IRemoteSipService sipService = null;
    private int boundCallId = -1;
    //    private SipCall boundSipCall = null;
    private Animation mPulseAnimation;
    // Primary caller info
    private TextView mPhoneNumber;
    private TextView mNumberLabel;
    private TextView mPrimaryName;
    private View mCallStateButton;
    private ImageView mCallStateIcon;
    private ImageView mCallStateVideoCallIcon;
    private TextView mCallStateLabel;
    private TextView mCallTypeLabel;
    private ImageView mHdAudioIcon;
    private ImageView mForwardIcon;
    private View mCallNumberAndLabel;
    private ImageView mPhoto;
    private TextView mElapsedTime;
    private Drawable mPrimaryPhotoDrawable;
    //    // Secondary caller info
//    private View mSecondaryCallInfo;
//    private TextView mSecondaryCallName;
//    private View mSecondaryCallProviderInfo;
//    private TextView mSecondaryCallProviderLabel;
//    private View mSecondaryCallConferenceCallIcon;
//    private View mSecondaryCallVideoCallIcon;
//    private View mProgressSpinner;
    private TextView mCallSubject;
    // Container view that houses the entire primary call card, including the call buttons
    private View mPrimaryCallCardContainer;
    // Container view that houses the primary call information
    private ViewGroup mPrimaryCallInfo;
    private View mCallButtonsContainer;
    // Dark number info bar
    private TextView mInCallMessageLabel;
    private FloatingActionButtonController mFloatingActionButtonController;
    private View mFloatingActionButtonContainer;
    private ImageButton mFloatingActionButton;
    private int mFloatingActionButtonVerticalOffset;
    private View mManageConferenceCallButton;
    private boolean isTimerCanceled = false;
    private boolean isDialpadVisible = false;
    private int mFabNormalDiameter;
    private int mFabSmallDiameter;
    private EventHandler callbackHandler = new EventHandler();
    private PowerManager.WakeLock wlProx = null;
    private boolean hasProxSensor = false;
    private ListView lvMessages;
    volatile private int localId = 0;
    private UiMessageController umc = null;
    private int primaryColor = Prefs.DEFAULT_UI_COLOR_PRIMARY;
    private AlertDialog dialogPremiumRequired;
    private boolean isPrem = false;

    @Override
    public void audioRouteSpeakerToggle() {
        try {
            sipService.audioRouteSpeakerToggle();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getAudioRoute() {
        try {
            return sipService.getAudioRoute();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public boolean isBlueoothScoAvailable() {
        try {
            return sipService.isBlueoothScoAvailable();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void onBtnAudioBluetoothClicked() {
        try {
            sipService.setAudioRoute(SipService.AUDIO_ROUTE_BLUETOOTH);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onBtnAudioEarClicked() {
        try {
            sipService.setAudioRoute(SipService.AUDIO_ROUTE_EAR);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onBtnAudioSpeakerClicked() {
        try {
            sipService.setAudioRoute(SipService.AUDIO_ROUTE_SPEAKER);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onBtnDialpadClicked() {
        if (isDialpadVisible)
            dialpadHide();
        else dialpadShow();
        callButtonFragment.setDialpadVisible(isDialpadVisible);
    }

    @Override
    public void onBtnHoldClicked() {
        try {
            int sipCallState = sipService.getSipCallState(boundCallId);
            if (sipCallState == SipCall.SIPSTATE_HOLD) {
                sipService.callHoldResume(boundCallId);
                callButtonFragment.setHold(false);
            } else if (sipCallState == SipCall.SIPSTATE_ACCEPTED) {
                sipService.callHold(boundCallId);
                callButtonFragment.setHold(true);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onBtnMuteClicked() {
        try {
            boolean isMute = sipService.callIsMute(boundCallId);
            if (isMute) sipService.callMuteResume(boundCallId);
            else sipService.callMute(boundCallId);
            callButtonFragment.setMute(!isMute);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onBtnRecordClicked() {
        if (DEBUG) gLog.l(TAG, Logger.lvDebug, "onBtnRecordClicked");

        if (!isPrem) try {
            final ContextThemeWrapper contextWrapper = new ContextThemeWrapper(this, R.style.Theme_AppCompat_Dialog_Alert);
            AlertDialog.Builder builder = new AlertDialog.Builder(contextWrapper);
            builder.setTitle(sipService.getLocalString("premium_feature", "Premium Feature"))
                    .setMessage(sipService.getLocalString("help_premium_feature", "Upgrade to unlock this and other features."))
                    .setCancelable(true)
                    .setPositiveButton(sipService.getLocalString("upgrade", "Upgrade"), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            try {
                                new DialogPremiumUpgrade().buildAndShow(getWindow().getContext(), sipService);
                            } catch (RemoteException e) {
                                if (DEBUG) e.printStackTrace();
                            }
                        }
                    })
                    .setNegativeButton(sipService.getLocalString("close", "Close"), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            dialogPremiumRequired = builder.create();
            dialogPremiumRequired.show();
            return;
        } catch (RemoteException e) {
            e.printStackTrace();
            return;
        }

        try {
            if (!sipService.rsGetBoolean(Prefs.KEY_BOOL_ACCEPT_CALL_RECORD_LEGAL, false)) {
                new DialogCallRecordLegalConfirm().buildAndShow(this, sipService);
                return;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            return;
        }
        try {
            boolean isRecord = sipService.callIsRecord(boundCallId);
            if (isRecord) sipService.callRecordStop(boundCallId);
            else sipService.callRecord(boundCallId);
            callButtonFragment.setRecord(!isRecord);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onAnswer() {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "onAnswer");
        mAnswerFragment.setVisibility(View.GONE);
        try {
            sipService.callAnswer(boundCallId);
            updateWlProxState();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        mFloatingActionButtonContainer.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDecline() {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "onDecline");
        try {
            sipService.callDecline(boundCallId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDeclineWithMessage(String textMessage) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "onDeclineWithMessage");

    }

    @Override
    public void onText() {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "onText");

    }

    @Override
    public void onCallMuteStateChanged(int pjsipCallId) {
        if (pjsipCallId != boundCallId) return; // not our call
        try {
            callButtonFragment.setMute(sipService.callIsMute(boundCallId));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCallRecordStateChanged(int pjsipCallId) {
        if (pjsipCallId != boundCallId) return; // not our call
        try {
            callButtonFragment.setRecord(sipService.callIsRecord(boundCallId));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCallStateChanged(int pjsipCallId) {
        if (pjsipCallId != boundCallId) return;
        try {
            int sipState = sipService.getSipCallState(boundCallId);
            if (sipState == SipCall.SIPSTATE_DISCONNECTED || sipState == SipCall.SIPSTATE_NOTFOUND) {
                gLog.l(TAG, Logger.lvVerbose, "Call Ended, InCallActivity Finishing");
                queueActivtyFinish();
            }
            if (sipState == SipCall.SIPSTATE_HOLD) {
                callButtonFragment.setHold(true);
            } else if (sipState == SipCall.SIPSTATE_ACCEPTED) {
                callButtonFragment.setHold(false);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onStackStateChanged(int stackState) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "onStackStateChanged");
        switch (stackState) {
            case SipService.STACK_STATE_STOPPED:
                finish();
                break;
        }
    }

    @Override
    public void onServiceConnectTimeout() {
        queueActivtyFinish();
    }

    @Override
    public void onServiceConnected(IRemoteSipService service) {
        sipService = service;
        processExtras(getIntent());
        setupView();
        try {
            if (!hasActiveCall(boundCallId)) {
                queueActivtyFinish();
                return;
            } else {
                refreshLicenseState(sipService);
                sipService.eventSubscribe(callbackHandler, boundCallId);
                updateWlProxState();
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onServiceDisconnected() {
        sipService = null;
    }

    public void setPrimaryName(String name, boolean nameIsNumber) {
        if (TextUtils.isEmpty(name)) {
            mPrimaryName.setText(null);
        } else {
            if (Build.VERSION.SDK_INT >= 23)
                mPrimaryName.setText(nameIsNumber ? PhoneNumberUtils.createTtsSpannable(name) : name);
            mPrimaryName.setText(name);

            // Set direction of the name field
            int nameDirection = View.TEXT_DIRECTION_INHERIT;
            if (nameIsNumber) {
                nameDirection = View.TEXT_DIRECTION_LTR;
            }
            mPrimaryName.setTextDirection(nameDirection);
        }
    }

    public void setPrimaryPhoneNumber(String number) {
        // Set the number
        if (TextUtils.isEmpty(number)) {
            mPhoneNumber.setText(null);
            mPhoneNumber.setVisibility(View.GONE);
        } else {
            if (Build.VERSION.SDK_INT >= 23) mPhoneNumber.setText(PhoneNumberUtils.createTtsSpannable(number));
            mPhoneNumber.setText(number);
            mPhoneNumber.setVisibility(View.VISIBLE);
            mPhoneNumber.setTextDirection(View.TEXT_DIRECTION_LTR);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        gLog = ((DialerApplication) getApplication()).getLoggerInstanceShared();
        super.onCreate(savedInstanceState);
        // set this flag so this activity will stay in front of the keyguard
        // Have the WindowManager filter out touch events that are "too fat".
        int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES;
//        int flags = WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES;

        getWindow().addFlags(flags);
        requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.hide();
        }
        mServiceController = new ServiceBindingController(gLog, this, this);

        final PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        hasProxSensor = pm.isWakeLockLevelSupported(PROXIMITY_SCREEN_OFF_WAKE_LOCK);
        if (hasProxSensor) {
            if (wlProx != null) wlProx.release();
            wlProx = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "PROXIMITY_SCREEN_OFF_WAKE_LOCK");
//            wlProx.acquire();
        }

        setContentView(R.layout.call_card_fragment);
    }

    @Override
    protected void onResume() { // BUG NOTE: Samsung devices may not call onResume, duplicate calls to onWindowfocusChanged
        mServiceController.bindToService();

        if (mFloatingActionButtonController != null)
            mFloatingActionButtonController.setScreenWidth(getWindow().getDecorView().getWidth());

        updateWlProxState();

        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isTimerCanceled = true;
        timerCallEnd.cancel();
        timerCallEnd.purge();
        if (sipService != null) try {
            sipService.eventUnSubscribe(callbackHandler, boundCallId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        mServiceController.unbindToService();
        if (wlProx != null && wlProx.isHeld()) {
            wlProx.release();
            wlProx = null;
        }
        gLog.flushHint();
    }

//BUGFIX: Samsung nonspec behavior, onPause on screen off - code moved to onWindowfocusChanged
//    @Override
//    protected void onPause() {
////        if (sipService != null) sipService.debugDisconnect();
//        if (wlProx != null && wlProx.isHeld())
//            wlProx.release(); // release proximity wakelock when activity not displayed
//        super.onPause();
//    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (boundCallId >= 0) // enable handling if a call has been bound
            switch (keyCode) {
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                case KeyEvent.KEYCODE_VOLUME_UP:
                case KeyEvent.KEYCODE_POWER:
                    try {
                        sipService.ignoreIncoming();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    break;
            }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (isDialpadVisible) { // if the dialpad fragment is visible, it will be removed first
            isDialpadVisible = false; // update the flag
            callButtonFragment.setDialpadVisible(isDialpadVisible); // update the dialpad button
            updateFabPosition();
        }
        if (sipService != null) try {
            sipService.eventUnSubscribe(callbackHandler, boundCallId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            mServiceController.bindToService();

            if (mFloatingActionButtonController != null)
                mFloatingActionButtonController.setScreenWidth(getWindow().getDecorView().getWidth());

            updateWlProxState();
        } else {
            if (wlProx != null && wlProx.isHeld())
                wlProx.release(); // release proximity wakelock when activity not displayed
        }
        super.onWindowFocusChanged(hasFocus);
    }

    void queueActivtyFinish() {
        if (isFinishQueued) return;
        isFinishQueued = true;
        gLog.flushHint();
        if (isTimerCanceled) finish();
        else
            timerCallEnd.schedule(new TimerTask() {
                @Override
                public void run() {
                    finish();
                }
            }, AFTER_CALL_END_DELAY);
        if (sipService != null) try {
            sipService.eventUnSubscribe(callbackHandler, boundCallId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void dialpadHide() {
        isDialpadVisible = false;
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.remove(fragmentDialpad);
        ft.commit();
        updateFabPosition();
    }

    private void dialpadShow() {
        isDialpadVisible = true;
        if (fragmentDialpad == null) {
            fragmentDialpad = new FragmentInCallDialpad();
            fragmentDialpad.setKeyListener(new InCallDialPadListener());
        }
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.add(R.id.answer_and_dialpad_container, fragmentDialpad);
        ft.addToBackStack("incall_dialpad");
        ft.commit();
        updateFabPosition();
    }

    private String getExtensionFromCallId(int pjsipCallId) {
//        SipCall sipCall = getCallFromId(pjsipCallId);
//        if (sipCall == null) return "Unknown";
//        return sipCall.getExtension();
        try {
            return sipService.getSipCallExtension(pjsipCallId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    private synchronized int getNextMessageId() {
        return localId++;
    }

    private boolean hasActiveCall(int boundCallId) {
        int sipCallState = -1;
        try {
            sipCallState = sipService.getSipCallState(boundCallId);
        } catch (RemoteException e) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
        }
        return !(boundCallId == -1 || sipCallState == SipCall.SIPSTATE_DISCONNECTED || sipCallState == SipCall.SIPSTATE_NOTFOUND);
    }

    private void populateUi() {
        String EXTENSION = getExtensionFromCallId(boundCallId); // get extension
        if (EXTENSION == null) if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "EXTENSION NULL");
        setPrimaryPhoneNumber(EXTENSION);
        Integer contactId = ContactProvider.getContactIdByNumber(this, EXTENSION); // lookup extension in user contacts
        if (contactId != null) {
            String displayName = ContactProvider.getContactName(this, contactId); // replace extension with friendly contact display_name if found
            mPrimaryName.setText(displayName);
        } else {
            String sipCallRemoteDisplayName = null;
            try {
                sipCallRemoteDisplayName = sipService.getSipCallRemoteDisplayName(boundCallId);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            if (sipCallRemoteDisplayName == null || sipCallRemoteDisplayName.isEmpty())
                try {
                    mPrimaryName.setText(sipService.getLocalString("unknown", "Unknown"));
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            else mPrimaryName.setText(sipCallRemoteDisplayName);

        }

        if (contactId != null) {
            Bitmap contactPhoto = ContactProvider.getContactPhoto(this, contactId);
            if (contactPhoto != null) mPhoto.setImageBitmap(contactPhoto);
        }


        // setup callbuttonfragment
        callButtonFragment.setPjsipCallId(boundCallId);
        callButtonFragment.setSupportedAudio(0);
        callButtonFragment.setEnabled(true);
//        if (boundSipCall != null) callButtonFragment.setEnabled(true); // enable ui interaction now setup is completed
//        else callButtonFragment.setEnabled(false);
        setupAnswerFragment();

        setupCallButtonFragment();

        // race condition - check if is still valid
        try {
            int sipCallState = sipService.getSipCallState(boundCallId);
            if (sipCallState == SipCall.SIPSTATE_DISCONNECTED || sipCallState == SipCall.SIPSTATE_NOTFOUND) {
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "Call Ended, InCallActivity Finishing");
                queueActivtyFinish();
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        lvMessages = (ListView) findViewById(R.id.lvInCallMessages);
        umc = new UiMessageController(sipService, lvMessages);
        umc.flagAdd(UiMessageController.FLAG_HANDLE_INCALL);
        umc.setBorderlessViews(true);
        setColors();
    }

    /**
     * Process Extra bundle from Intent and populate variables as required.
     *
     * @param intent
     * @return TRUE = some extras processed, FALSE = bare intent
     */
    private boolean processExtras(Intent intent) {
        if (intent == null) return false;
        Bundle extras = intent.getExtras();
        if (extras == null) return false;
        if (extras.containsKey(EXTRA_BIND_PJSIPCALLID))
            boundCallId = extras.getInt(EXTRA_BIND_PJSIPCALLID);
        return true;
    }

    private void refreshLicenseState(IRemoteSipService sipService) {
        Bundle licensingStateBundle = null;
        try {
            licensingStateBundle = sipService.getLicensingStateBundle();
            if (licensingStateBundle != null)
                isPrem = licensingStateBundle.getBoolean(LicensingManager.KEY_PREM, false);
        } catch (RemoteException e) {
            if (DEBUG) e.printStackTrace();
        }
    }

    private void setColors() {
        try {
            final int colorPrimary = sipService.rsGetInt(Prefs.KEY_UI_COLOR_PRIMARY, Prefs.DEFAULT_UI_COLOR_PRIMARY);
//            getWindow().setStatusBarColor(colorPrimary);
            ActionBar actionBar = getActionBar();
            if (actionBar != null) actionBar.setBackgroundDrawable(new ColorDrawable(colorPrimary));
            mPrimaryCallCardContainer.setBackgroundColor(colorPrimary);
            mCallButtonsContainer.setBackgroundColor(colorPrimary);
            callButtonFragment.setPrimaryColor(colorPrimary);
            if (umc != null) umc.setPrimaryColor(colorPrimary);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

    }

    private void setupAnswerFragment() {
        if (mAnswerFragment == null) mAnswerFragment = findViewById(R.id.answerFragment);
//        if (boundSipCall == null) return; // invalid call?
        try {
            if (sipService.callIsOutgoing(boundCallId)) return; // no need to show for outbound calls
            int sipState = sipService.getSipCallState(boundCallId);
            switch (sipState) {
                case SipCall.SIPSTATE_PROGRESS_IN:
                case SipCall.SIPSTATE_PROGRESS_OUT:
                    mAnswerFragment.setVisibility(View.VISIBLE);
                    mFloatingActionButtonContainer.setVisibility(View.GONE); // hide FAB for glowpad
                    break;
                case SipCall.SIPSTATE_DISCONNECTED:
                    mAnswerFragment.setVisibility(View.GONE);
                    break;
                case SipCall.SIPSTATE_ACCEPTED:
                case SipCall.SIPSTATE_HOLD:
                    mAnswerFragment.setVisibility(View.GONE);
                default:
                    if (DEBUG) {
                        gLog.l(TAG, Logger.lvDebug, "Unhandled sipstate for answerfragment " + sipState);
                        gLog.l(TAG, Logger.lvDebug, "boundCallId " + boundCallId);
                    }
                    break;
            }
        } catch (RemoteException e) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
        }

    }

    private void setupCallButtonFragment() {
        if (callButtonFragment == null || boundCallId == -1 || sipService == null) return;
        try {
            callButtonFragment.setMute(sipService.callIsMute(boundCallId));
            callButtonFragment.setHold(sipService.getSipCallState(boundCallId) == SipCall.SIPSTATE_HOLD);
            callButtonFragment.setRecord(sipService.callIsRecord(boundCallId));
            callButtonFragment.setSupportedAudio(0);
        } catch (RemoteException e) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
        }

    }

    private void setupView() {
//        if (DEBUG) gLog.lBegin();
        mPulseAnimation = AnimationUtils.loadAnimation(this, R.anim.call_status_pulse);

        mPhoneNumber = (TextView) findViewById(R.id.phoneNumber);
        mPrimaryName = (TextView) findViewById(R.id.name);
        mNumberLabel = (TextView) findViewById(R.id.label);
//        mSecondaryCallInfo = findViewById(R.id.secondary_call_info);
//        mSecondaryCallProviderInfo = findViewById(R.id.secondary_call_provider_info);
        mPhoto = (ImageView) findViewById(R.id.photo);
        mPhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                getPresenter().onContactPhotoClick();
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "mPhoto");
            }
        });
        mCallStateIcon = (ImageView) findViewById(R.id.callStateIcon);
        mCallStateVideoCallIcon = (ImageView) findViewById(R.id.videoCallIcon);
        mCallStateLabel = (TextView) findViewById(R.id.callStateLabel);
//        mHdAudioIcon = (ImageView) findViewById(R.id.hdAudioIcon);
//        mForwardIcon = (ImageView) findViewById(R.id.forwardIcon);
        mCallNumberAndLabel = findViewById(R.id.labelAndNumber);
        mCallTypeLabel = (TextView) findViewById(R.id.callTypeLabel);
        mElapsedTime = (TextView) findViewById(R.id.elapsedTime);
        mPrimaryCallCardContainer = findViewById(R.id.primary_call_info_container);
        mPrimaryCallInfo = (ViewGroup) findViewById(R.id.primary_call_banner);
        mCallButtonsContainer = findViewById(R.id.callButtonFragment);
        callButtonFragment = (CallButtonFragment) getFragmentManager().findFragmentById(R.id.callButtonFragment);
        mInCallMessageLabel = (TextView) findViewById(R.id.connectionServiceMessage);
//        mProgressSpinner = findViewById(R.id.progressSpinner);

        mFabNormalDiameter = getResources().getDimensionPixelOffset(R.dimen.end_call_floating_action_button_diameter);
        mFabSmallDiameter = getResources().getDimensionPixelOffset(R.dimen.end_call_floating_action_button_small_diameter);

        mFloatingActionButtonContainer = findViewById(R.id.floating_end_call_action_button_container);
        mFloatingActionButton = (ImageButton) findViewById(R.id.floating_end_call_action_button);

        mFloatingActionButtonController = new FloatingActionButtonController(this, mFloatingActionButtonContainer, mFloatingActionButton);
        mFloatingActionButtonController.setScreenWidth(getWindow().getDecorView().getWidth());
        mFloatingActionButton.setEnabled(true);
        mFloatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "mFloatingActionButton onClick");
                try {
                    sipService.callHangup(boundCallId);

                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                queueActivtyFinish();

            }
        });

//        mSecondaryCallInfo.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
////                getPresenter().secondaryInfoClicked();
////                updateFabPositionForSecondaryCallInfo();
//            }
//        });

        mCallStateButton = findViewById(R.id.callStateButton);
        mCallStateButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
//                getPresenter().onCallStateButtonTouched();
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "mCallStateButton");
                return false;
            }
        });

        mManageConferenceCallButton = findViewById(R.id.manage_conference_call_button);
//        mManageConferenceCallButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
////                InCallActivity activity = (InCallActivity) getActivity();
////                activity.showConferenceFragment(true);
//            }
//        });

        mPrimaryName.setElegantTextHeight(false);
        mCallStateLabel.setElegantTextHeight(false);
//        mCallSubject = (TextView) findViewById(R.id.callSubject);

        mDialpadContainer = findViewById(R.id.answer_and_dialpad_container);

//        if (DEBUG) gLog.lEnd();

        populateUi();
    }

    private void updateFabPosition() {
        mFloatingActionButtonController.resize(isDialpadVisible ? mFabSmallDiameter : mFabNormalDiameter, true);
    }

    /**
     * Acquire Proximity-aware wakelock when the call has been accepted.
     */
    private void updateWlProxState() {
        if (sipService == null) return;
        if (!hasActiveCall(boundCallId)) return;
        if (wlProx != null && !wlProx.isHeld()) {
            if (sipService != null) {
                int sipCallState = -1;
                try {
                    sipCallState = sipService.getSipCallState(boundCallId);
                } catch (RemoteException e) {
                    if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
                }
                if (sipCallState == SipCall.SIPSTATE_ACCEPTED || sipCallState == SipCall.SIPSTATE_PROGRESS_OUT)
                    wlProx.acquire(); // re-acquire proximity wakelock
            }
        }
    }

    class InCallDialPadListener implements FragmentInCallDialpad.KeyListener {

        @Override
        public void onKeyPressed(int keyCode) {
            try {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_1:
                        sipService.callPutDtmf(boundCallId, "1");
                        break;
                    case KeyEvent.KEYCODE_2:
                        sipService.callPutDtmf(boundCallId, "2");
                        break;
                    case KeyEvent.KEYCODE_3:
                        sipService.callPutDtmf(boundCallId, "3");
                        break;
                    case KeyEvent.KEYCODE_4:
                        sipService.callPutDtmf(boundCallId, "4");
                        break;
                    case KeyEvent.KEYCODE_5:
                        sipService.callPutDtmf(boundCallId, "5");
                        break;
                    case KeyEvent.KEYCODE_6:
                        sipService.callPutDtmf(boundCallId, "6");
                        break;
                    case KeyEvent.KEYCODE_7:
                        sipService.callPutDtmf(boundCallId, "7");
                        break;
                    case KeyEvent.KEYCODE_8:
                        sipService.callPutDtmf(boundCallId, "8");
                        break;
                    case KeyEvent.KEYCODE_9:
                        sipService.callPutDtmf(boundCallId, "9");
                        break;
                    case KeyEvent.KEYCODE_0:
                        sipService.callPutDtmf(boundCallId, "0");
                        break;
                    case KeyEvent.KEYCODE_POUND:
                        sipService.callPutDtmf(boundCallId, "#");
                        break;
                    case KeyEvent.KEYCODE_STAR:
                        sipService.callPutDtmf(boundCallId, "*");
                        break;
                    default:
                        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "Unhandled keycode " + keyCode);
                        break;
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    class EventHandler extends IRemoteSipServiceEvents.Stub {

        // reminder callbacks are external calls

        @Override
        public void onCallMuteStateChanged(int pjsipCallId) throws RemoteException {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "onCallMuteStateChanged");
            if (pjsipCallId != boundCallId) return; // not our call
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        boolean b = sipService.callIsMute(boundCallId);
                        callButtonFragment.setMute(b);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        @Override
        public void onCallRecordStateChanged(int pjsipCallId) throws RemoteException {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "onCallRecordStateChanged");
            if (pjsipCallId != boundCallId) return; // not our call
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        boolean b = sipService.callIsRecord(boundCallId);
                        callButtonFragment.setRecord(b);
                    } catch (RemoteException e) {
                        if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
                        e.printStackTrace();
                    }
                }
            });
        }

        @Override
        public void onCallStateChanged(int pjsipCallId) throws RemoteException {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "onCallStateChanged");
            if (pjsipCallId != boundCallId) return;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        int sipState = sipService.getSipCallState(boundCallId);
                        switch (sipState) {
                            case SipCall.SIPSTATE_DISCONNECTED:
                            case SipCall.SIPSTATE_NOTFOUND:
                                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "Call Ended, InCallActivity Finishing");
                                if (wlProx != null && wlProx.isHeld()) wlProx.release();
                                queueActivtyFinish();
                                break;
                            case SipCall.SIPSTATE_HOLD:
                                callButtonFragment.setHold(true);
                                break;
                            case SipCall.SIPSTATE_ACCEPTED:
                                updateWlProxState();
                                callButtonFragment.setHold(false);
                                break;
                        }
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });

        }

        @Override
        public void onStackStateChanged(int stackState) throws RemoteException {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "onStackStateChanged");
            switch (stackState) {
                case SipService.STACK_STATE_STOPPED:
                    finish();
                    break;
            }
        }

        @Override
        public void onExit() throws RemoteException {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "onExit");
            finish();
        }

        @Override
        public void onSettingsChanged() throws RemoteException {

        }

        @Override
        public void showMessage(final int iAnType, final Bundle itemMeta) throws RemoteException {
            if (isFinishQueued) return;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (umc != null) umc.messagePost(iAnType, itemMeta);
                }
            });
        }

        @Override
        public void clearMessageByType(final int iAnType) throws RemoteException {
            if (isFinishQueued) return;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (umc != null) umc.messageClearByType(iAnType);
                }
            });
        }

        @Override
        public void clearMessageBySmid(final int smid) throws RemoteException {
            if (isFinishQueued) return;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (umc != null) umc.itemRemoveWithSmId(smid);
                }
            });
        }

        @Override
        public void onLicenseStateUpdated(Bundle licenseData) throws RemoteException {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "onLicenseStateUpdated");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    refreshLicenseState(sipService);
                    populateUi();
                }
            });
        }

    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.dialpad_floating_action_button:
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
//                handleDialButtonPressed();
                break;
            case R.id.deleteButton: {
//                keyPressed(KeyEvent.KEYCODE_DEL);
                break;
            }
            case R.id.digits: {
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "onClick digits");
//                if (!isDigitsEmpty()) {
//                    mDigits.setCursorVisible(true);
//                }
                break;
            }
            case R.id.dialpad_overflow: {
//                mOverflowPopupMenu.show();
                break;
            }
            default: {
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "Unexpected onClick() event from: " + view);
                return;
            }
        }
    }


}
