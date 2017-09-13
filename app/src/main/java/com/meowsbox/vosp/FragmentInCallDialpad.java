/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp;

import android.app.Fragment;
import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;

import com.meowsbox.vosp.android.common.animation.AnimUtils;
import com.meowsbox.vosp.android.common.dialpad.DialpadKeyButton;
import com.meowsbox.vosp.android.common.dialpad.DialpadView;
import com.meowsbox.vosp.android.common.util.PhoneNumberFormatter;
import com.meowsbox.vosp.common.Logger;
import com.meowsbox.vosp.widget.FloatingActionButtonController;

import java.util.HashSet;

/**
 * Customized FragmentInCallDialpad from AOSP Marshmallow
 */
public class FragmentInCallDialpad extends Fragment implements DialpadKeyButton.OnPressedListener, View.OnLongClickListener, View.OnClickListener, PopupMenu.OnMenuItemClickListener, View.OnKeyListener {
    public final static boolean DEBUG = DialerApplication.DEBUG;
    private static final char PAUSE = ',';
    private static final char WAIT = ';';
    /**
     * The length of DTMF tones in milliseconds
     */
    private static final int TONE_LENGTH_MS = 150;
    private static final int TONE_LENGTH_INFINITE = -1;
    /**
     * The DTMF tone volume relative to other sounds in the stream
     */
    private static final int TONE_RELATIVE_VOLUME = 80;
    /**
     * Stream type used to play the DTMF tones off call, and mapped to the volume control keys
     */
    private static final int DIAL_TONE_STREAM_TYPE = AudioManager.STREAM_DTMF;
    static Logger gLog;
    public final String TAG = this.getClass().getName();
    /**
     * Set of dialpad keys that are currently being pressed
     */
    private final HashSet<View> mPressedDialpadKeys = new HashSet<View>(12);
    private final Object mToneGeneratorLock = new Object();
    EditText mDigits;
    Context mContext = null;
    DialpadView mDialpadView;
    KeyListener mKeyListener;
    private View mDelete;
    private FloatingActionButtonController mFloatingActionButtonController;
    //    private OnFragmentInteractionListener mListener;
    private View mOverflowMenuButton;
    private PopupMenu mOverflowPopupMenu;
    private boolean mWasEmptyBeforeTextChange;
    private boolean mDTMFToneEnabled = true;
    private ToneGenerator mToneGenerator;
//    private ViewGroup mRateContainer;
//    private TextView mIldCountry;
//    private TextView mIldRate;

    public FragmentInCallDialpad() {
        // Required empty public constructor
    }

    /**
     * Returns true of the newDigit parameter can be added at the current selection
     * point, otherwise returns false.
     * Only prevents input of WAIT and PAUSE digits at an unsupported position.
     * Fails early if start == -1 or start is larger than end.
     */
    /* package */
    static boolean canAddDigit(CharSequence digits, int start, int end,
                               char newDigit) {
        if (newDigit != WAIT && newDigit != PAUSE) {
            throw new IllegalArgumentException(
                    "Should not be called for anything other than PAUSE & WAIT");
        }

        // False if no selection, or selection is reversed (end < start)
        if (start == -1 || end < start) {
            return false;
        }

        // unsupported selection-out-of-bounds state
        if (start > digits.length() || end > digits.length()) return false;

        // Special digit cannot be the first digit
        if (start == 0) return false;

        if (newDigit == WAIT) {
            // preceding char is ';' (WAIT)
            if (digits.charAt(start - 1) == WAIT) return false;

            // next char is ';' (WAIT)
            if ((digits.length() > end) && (digits.charAt(end) == WAIT)) return false;
        }

        return true;
    }

    public static FragmentInCallDialpad newInstance() {
        FragmentInCallDialpad fragment = new FragmentInCallDialpad();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    public void clearDialpad() {
        if (mDigits != null) {
            mDigits.getText().clear();
        }
    }

//    @Override
//    public void onAttach(Context context) {
//        super.onAttach(context);
//        mContext = context;
//        if (context instanceof OnFragmentInteractionListener) {
//            mListener = (OnFragmentInteractionListener) context;
//        } else {
//            throw new RuntimeException(context.toString() + " must implement OnFragmentInteractionListener");
//        }
//    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.dialpad_floating_action_button:
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                handleDialButtonPressed();
                break;
            case R.id.deleteButton: {
                keyPressed(KeyEvent.KEYCODE_DEL);
                break;
            }
            case R.id.digits: {
                if (!isDigitsEmpty()) {
                    mDigits.setCursorVisible(true);
                }
                break;
            }
            case R.id.dialpad_overflow: {
                mOverflowPopupMenu.show();
                break;
            }
            default: {
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "Unexpected onClick() event from: " + view);
                return;
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        gLog = ((DialerApplication) getActivity().getApplication()).getLoggerInstanceShared();

        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
//        if (DEBUG) gLog.lBegin();
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.activity_test, container, false);


        mDialpadView = (DialpadView) view.findViewById(R.id.dialpad_view);
        mDialpadView.setCanDigitsBeEdited(true);
        mDialpadView.setShowVoicemailButton(false);

//        mRateContainer = (ViewGroup) view.findViewById(R.id.rate_container);
//        mIldCountry = (TextView) mRateContainer.findViewById(R.id.ild_country);
//        mIldRate = (TextView) mRateContainer.findViewById(R.id.ild_rate);

        mDigits = mDialpadView.getDigits();
        mDigits.setOnClickListener(this);
        mDigits.setOnKeyListener(this);
        mDigits.setOnLongClickListener(this);
        mDigits.addTextChangedListener(new DialpadTextWatcher());
        mDigits.setElegantTextHeight(false);

        configureKeypadListeners(mDialpadView);

        mDelete = mDialpadView.getDeleteButton();

        if (mDelete != null) {
            mDelete.setOnClickListener(this);
            mDelete.setOnLongClickListener(this);
        }

        PhoneNumberFormatter.setPhoneNumberFormattingTextWatcher(mContext, mDigits);

        final View floatingActionButtonContainer = view.findViewById(R.id.dialpad_floating_action_button_container);
        final ImageButton floatingActionButton = (ImageButton) view.findViewById(R.id.dialpad_floating_action_button);
        floatingActionButton.setOnClickListener(this);
        mFloatingActionButtonController = new FloatingActionButtonController(getActivity(), floatingActionButtonContainer, floatingActionButton);
        mFloatingActionButtonController.setVisible(false); // this dialpad is only used with InCallActivity, which has its own FAB

//        if (DEBUG) gLog.lEnd();
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        synchronized (mToneGeneratorLock) {
            if (mToneGenerator == null) {
                try {
                    mToneGenerator = new ToneGenerator(DIAL_TONE_STREAM_TYPE, TONE_RELATIVE_VOLUME);
                } catch (RuntimeException e) {
                    if (DEBUG)
                        gLog.l(TAG, Logger.lvVerbose, "Exception caught while creating local tone generator: " + e);
                    mToneGenerator = null;
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Populate the overflow menu in onResume instead of onCreate, so that if the SMS activity
        // is disabled while Dialer is paused, the "Send a text message" option can be correctly
        // removed when resumed.
        mOverflowMenuButton = mDialpadView.getOverflowMenuButton();
        mOverflowPopupMenu = buildOptionsMenu(mOverflowMenuButton);
        mOverflowMenuButton.setOnTouchListener(mOverflowPopupMenu.getDragToOpenListener());
        mOverflowMenuButton.setOnClickListener(this);
        mOverflowMenuButton.setVisibility(isDigitsEmpty() ? View.INVISIBLE : View.VISIBLE);

    }

//    @Override
//    public void onDetach() {
//        super.onDetach();
//        mListener = null;
//    }


    @Override
    public void onDestroy() {
        gLog.flushHint();
        super.onDestroy();
    }

    @Override
    public void onStop() {
        super.onStop();
        synchronized (mToneGeneratorLock) {
            if (mToneGenerator != null) {
                mToneGenerator.release();
                mToneGenerator = null;
            }
        }
    }

    @Override
    public boolean onKey(View view, int keyCode, KeyEvent event) {
        switch (view.getId()) {
            case R.id.digits:
                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    handleDialButtonPressed();
                    return true;
                }
                break;
        }
        return false;
    }

    @Override
    public boolean onLongClick(View view) {
        final Editable digits = mDigits.getText();
        final int id = view.getId();
        switch (id) {
            case R.id.deleteButton: {
                digits.clear();
                return true;
            }
//            case R.id.one: {
//                // '1' may be already entered since we rely on onTouch() event for numeric buttons.
//                // Just for safety we also check if the digits field is empty or not.
//                if (isDigitsEmpty() || TextUtils.equals(mDigits.getText(), "1")) {
//                    // We'll try to initiate voicemail and thus we want to remove irrelevant string.
//                    removePreviousDigitIfPossible();
//
//                    List<PhoneAccountHandle> subscriptionAccountHandles =
//                            PhoneAccountUtils.getSubscriptionPhoneAccounts(getActivity());
//                    boolean hasUserSelectedDefault = subscriptionAccountHandles.contains(
//                            getTelecomManager().getDefaultOutgoingPhoneAccount(
//                                    PhoneAccount.SCHEME_VOICEMAIL));
//                    boolean needsAccountDisambiguation = subscriptionAccountHandles.size() > 1
//                            && !hasUserSelectedDefault;
//
//                    if (needsAccountDisambiguation || isVoicemailAvailable()) {
//                        // On a multi-SIM phone, if the user has not selected a default
//                        // subscription, initiate a call to voicemail so they can select an account
//                        // from the "Call with" dialog.
//                        callVoicemail();
//                    } else if (getActivity() != null) {
//                        // Voicemail is unavailable maybe because Airplane mode is turned on.
//                        // Check the current status and show the most appropriate error message.
//                        final boolean isAirplaneModeOn =
//                                Settings.System.getInt(getActivity().getContentResolver(),
//                                        Settings.System.AIRPLANE_MODE_ON, 0) != 0;
//                        if (isAirplaneModeOn) {
//                            DialogFragment dialogFragment = ErrorDialogFragment.newInstance(
//                                    R.string.dialog_voicemail_airplane_mode_message);
//                            dialogFragment.show(getFragmentManager(),
//                                    "voicemail_request_during_airplane_mode");
//                        } else {
//                            DialogFragment dialogFragment = ErrorDialogFragment.newInstance(
//                                    R.string.dialog_voicemail_not_ready_message);
//                            dialogFragment.show(getFragmentManager(), "voicemail_not_ready");
//                        }
//                    }
//                    return true;
//                }
//                return false;
//            }
//            case R.id.zero: {
//                // Remove tentative input ('0') done by onTouch().
//                removePreviousDigitIfPossible();
//                keyPressed(KeyEvent.KEYCODE_PLUS);
//
//                // Stop tone immediately
//                stopTone();
//                mPressedDialpadKeys.remove(view);
//
//                return true;
//            }
            case R.id.digits: {
                // Right now EditText does not show the "paste" option when cursor is not visible.
                // To show that, make the cursor visible, and return false, letting the EditText
                // show the option by itself.
                mDigits.setCursorVisible(true);
                return false;
            }
        }
        return false;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_2s_pause:
                updateDialString(PAUSE);
                return true;
            case R.id.menu_add_wait:
                updateDialString(WAIT);
                return true;
            default:
                return false;
        }
    }

    /**
     * When a key is pressed, we start playing DTMF tone, do vibration, and enter the digit
     * immediately. When a key is released, we stop the tone. Note that the "key press" event will
     * be delivered by the system with certain amount of delay, it won't be synced with user's
     * actual "touch-down" behavior.
     */
    @Override
    public void onPressed(View view, boolean pressed) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "onPressed(). view: " + view + ", pressed: " + pressed);
        if (pressed) {
            switch (view.getId()) {
                case R.id.one: {
                    keyPressed(KeyEvent.KEYCODE_1);
                    break;
                }
                case R.id.two: {
                    keyPressed(KeyEvent.KEYCODE_2);
                    break;
                }
                case R.id.three: {
                    keyPressed(KeyEvent.KEYCODE_3);
                    break;
                }
                case R.id.four: {
                    keyPressed(KeyEvent.KEYCODE_4);
                    break;
                }
                case R.id.five: {
                    keyPressed(KeyEvent.KEYCODE_5);
                    break;
                }
                case R.id.six: {
                    keyPressed(KeyEvent.KEYCODE_6);
                    break;
                }
                case R.id.seven: {
                    keyPressed(KeyEvent.KEYCODE_7);
                    break;
                }
                case R.id.eight: {
                    keyPressed(KeyEvent.KEYCODE_8);
                    break;
                }
                case R.id.nine: {
                    keyPressed(KeyEvent.KEYCODE_9);
                    break;
                }
                case R.id.zero: {
                    keyPressed(KeyEvent.KEYCODE_0);
                    break;
                }
                case R.id.pound: {
                    keyPressed(KeyEvent.KEYCODE_POUND);
                    break;
                }
                case R.id.star: {
                    keyPressed(KeyEvent.KEYCODE_STAR);
                    break;
                }
                default: {
                    if (DEBUG) gLog.l(TAG, Logger.lvError, "Unexpected onTouch(ACTION_DOWN) event from: " + view);
                    break;
                }
            }
            mPressedDialpadKeys.add(view);
        } else {
            mPressedDialpadKeys.remove(view);
            if (mPressedDialpadKeys.isEmpty()) {
                stopTone();
            }
        }
    }

    public void setKeyListener(KeyListener mListener) {
        this.mKeyListener = mListener;
    }

    /**
     * Called by the containing Activity to tell this Fragment to build an overflow options
     * menu for display by the container when appropriate.
     *
     * @param invoker the View that invoked the options menu, to act as an anchor location.
     */
    private PopupMenu buildOptionsMenu(View invoker) {
        final PopupMenu popupMenu = new PopupMenu(getActivity(), invoker) {
            @Override
            public void show() {
                final Menu menu = getMenu();

                boolean enable = !isDigitsEmpty();
                for (int i = 0; i < menu.size(); i++) {
                    MenuItem item = menu.getItem(i);
                    item.setEnabled(enable);
                }
                super.show();
            }
        };
        popupMenu.inflate(R.menu.dialpad_options);
        popupMenu.setOnMenuItemClickListener(this);
        return popupMenu;
    }

    private void configureKeypadListeners(View fragmentView) {
        final int[] buttonIds = new int[]{R.id.one, R.id.two, R.id.three, R.id.four, R.id.five, R.id.six, R.id.seven, R.id.eight, R.id.nine, R.id.star, R.id.zero, R.id.pound};

        DialpadKeyButton dialpadKey;

        for (int i = 0; i < buttonIds.length; i++) {
            dialpadKey = (DialpadKeyButton) fragmentView.findViewById(buttonIds[i]);
            dialpadKey.setOnPressedListener(this);
        }

        // Long-pressing one button will initiate Voicemail.
        final DialpadKeyButton one = (DialpadKeyButton) fragmentView.findViewById(R.id.one);
        one.setOnLongClickListener(this);

        // Long-pressing zero button will enter '+' instead.
        final DialpadKeyButton zero = (DialpadKeyButton) fragmentView.findViewById(R.id.zero);
        zero.setOnLongClickListener(this);
    }

    private void handleDialButtonPressed() {

        // this method never actually gets called as the FAB is always covered by the parent layout with its own FAB (usually the hang up button)

//        final String number = mDigits.getText().toString();
//        SipService sipService = SipService.getInstance();
//        setMessage(null);
//
//        if (DEBUG) gLog.l(Logger.lvVerbose, "Calling: " + number);
//
//
//        // Clear the digits just in case.
//        clearDialpad();
////        final Intent intent = IntentUtil.getCallIntent(number, (getActivity() instanceof DialtactsActivity ? ((DialtactsActivity) getActivity()).getCallOrigin() : null));
////        DialerUtils.startActivityWithErrorToast(getActivity(), intent);
////        hideAndClearDialpad(false);
//
//        int sipCallId = sipService.callOutDefault(number);
//        if (sipCallId < 0 || sipService.getSipCallState(sipCallId) == SipCall.SIPSTATE_DISCONNECTED) setMessage("Call Failed");
//        else {
//            Intent intent = new Intent(getActivity(), InCallActivity.class);
//            intent.putExtra(InCallActivity.EXTRA_BIND_PJSIPCALLID, sipCallId);
//            startActivity(intent);
//        }
    }

    /**
     * @return true if the widget with the phone number digits is empty.
     */
    private boolean isDigitsEmpty() {
        return mDigits.length() == 0;
    }

    private void keyPressed(int keyCode) {
        if (getView() == null || getView().getTranslationY() != 0) {
            return;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_1:
                playTone(ToneGenerator.TONE_DTMF_1, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_2:
                playTone(ToneGenerator.TONE_DTMF_2, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_3:
                playTone(ToneGenerator.TONE_DTMF_3, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_4:
                playTone(ToneGenerator.TONE_DTMF_4, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_5:
                playTone(ToneGenerator.TONE_DTMF_5, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_6:
                playTone(ToneGenerator.TONE_DTMF_6, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_7:
                playTone(ToneGenerator.TONE_DTMF_7, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_8:
                playTone(ToneGenerator.TONE_DTMF_8, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_9:
                playTone(ToneGenerator.TONE_DTMF_9, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_0:
                playTone(ToneGenerator.TONE_DTMF_0, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_POUND:
                playTone(ToneGenerator.TONE_DTMF_P, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_STAR:
                playTone(ToneGenerator.TONE_DTMF_S, TONE_LENGTH_INFINITE);
                break;
            default:
                break;
        }

        getView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
        mDigits.onKeyDown(keyCode, event);

        // If the cursor is at the end of the text we hide it.
        final int length = mDigits.length();
        if (length == mDigits.getSelectionStart() && length == mDigits.getSelectionEnd()) {
            mDigits.setCursorVisible(false);
        }
        if (mKeyListener != null) mKeyListener.onKeyPressed(keyCode);
    }

    /**
     * Plays the specified tone for TONE_LENGTH_MS milliseconds.
     */
    private void playTone(int tone) {
        playTone(tone, TONE_LENGTH_MS);
    }

    /**
     * Play the specified tone for the specified milliseconds
     * <p>
     * The tone is played locally, using the audio stream for phone calls.
     * Tones are played only if the "Audible touch tones" user preference
     * is checked, and are NOT played if the device is in silent mode.
     * <p>
     * The tone length can be -1, meaning "keep playing the tone." If the caller does so, it should
     * call stopTone() afterward.
     *
     * @param tone       a tone code from {@link ToneGenerator}
     * @param durationMs tone length.
     */
    private void playTone(int tone, int durationMs) {
        // if local tone playback is disabled, just return.
        if (!mDTMFToneEnabled) {
            return;
        }

        // Also do nothing if the phone is in silent mode.
        // We need to re-check the ringer mode for *every* playTone()
        // call, rather than keeping a local flag that's updated in
        // onResume(), since it's possible to toggle silent mode without
        // leaving the current activity (via the ENDCALL-longpress menu.)
        AudioManager audioManager = (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);
        int ringerMode = audioManager.getRingerMode();
        if ((ringerMode == AudioManager.RINGER_MODE_SILENT) || (ringerMode == AudioManager.RINGER_MODE_VIBRATE)) {
            return;
        }

        synchronized (mToneGeneratorLock) {
            if (mToneGenerator == null) {
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "playTone: mToneGenerator == null, tone: " + tone);
                return;
            }

            // Start the new tone (will stop any playing tone)
            mToneGenerator.startTone(tone, durationMs);
        }
    }

    private void setMessage(final String message) {
//        getActivity().runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                if (message == null) {
//                    mIldRate.setText(message);
//                    mRateContainer.setVisibility(View.GONE);
//                } else {
//                    mIldRate.setText(message);
//                    mRateContainer.setVisibility(View.VISIBLE);
//                }
//            }
//        });
    }

    /**
     * Stop the tone if it is played.
     */
    private void stopTone() {
        // if local tone playback is disabled, just return.
        if (!mDTMFToneEnabled) {
            return;
        }
        synchronized (mToneGeneratorLock) {
            if (mToneGenerator == null) {
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "stopTone: mToneGenerator == null");
                return;
            }
            mToneGenerator.stopTone();
        }
    }

    /**
     * Update the enabledness of the "Dial" and "Backspace" buttons if applicable.
     */
    private void updateDeleteButtonEnabledState() {
        final boolean digitsNotEmpty = !isDigitsEmpty();
        mDelete.setEnabled(digitsNotEmpty);
    }

    /**
     * Updates the dial string (mDigits) after inserting a Pause character (,)
     * or Wait character (;).
     */
    private void updateDialString(char newDigit) {
        if (newDigit != WAIT && newDigit != PAUSE) {
            throw new IllegalArgumentException(
                    "Not expected for anything other than PAUSE & WAIT");
        }

        int selectionStart;
        int selectionEnd;

        // SpannableStringBuilder editable_text = new SpannableStringBuilder(mDigits.getText());
        int anchor = mDigits.getSelectionStart();
        int point = mDigits.getSelectionEnd();

        selectionStart = Math.min(anchor, point);
        selectionEnd = Math.max(anchor, point);

        if (selectionStart == -1) {
            selectionStart = selectionEnd = mDigits.length();
        }

        Editable digits = mDigits.getText();

        if (canAddDigit(digits, selectionStart, selectionEnd, newDigit)) {
            digits.replace(selectionStart, selectionEnd, Character.toString(newDigit));

            if (selectionStart != selectionEnd) {
                // Unselect: back to a regular cursor, just pass the character inserted.
                mDigits.setSelection(selectionStart + 1);
            }
        }
    }

    /**
     * Handle transitions for the menu button depending on the state of the digits edit text.
     * Transition out when going from digits to no digits and transition in when the first digit
     * is pressed.
     *
     * @param transitionIn True if transitioning in, False if transitioning out
     */
    private void updateMenuOverflowButton(boolean transitionIn) {
        mOverflowMenuButton = mDialpadView.getOverflowMenuButton();
        if (transitionIn) {
            AnimUtils.fadeIn(mOverflowMenuButton, AnimUtils.DEFAULT_DURATION);
        } else {
            AnimUtils.fadeOut(mOverflowMenuButton, AnimUtils.DEFAULT_DURATION);
        }
    }

    public interface OnFragmentInteractionListener {
    }

    interface KeyListener {
        void onKeyPressed(int keyCode);
    }

    class DialpadTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
//            if (DEBUG) gLog.l(Logger.lvVerbose);
            mWasEmptyBeforeTextChange = TextUtils.isEmpty(s);
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
//            if (DEBUG) gLog.l(Logger.lvVerbose);
            if (mWasEmptyBeforeTextChange != TextUtils.isEmpty(s))
                updateMenuOverflowButton(mWasEmptyBeforeTextChange);
        }

        @Override
        public void afterTextChanged(Editable s) {
//            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, mDigits.getText().toString());
            updateDeleteButtonEnabledState();
            setMessage(null);
        }
    }

}
