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

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.SparseIntArray;
import android.view.ContextThemeWrapper;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnDismissListener;
import android.widget.PopupMenu.OnMenuItemClickListener;

import com.meowsbox.vosp.android.common.util.MaterialColorMapUtils;
import com.meowsbox.vosp.android.common.util.MaterialColorMapUtils.MaterialPalette;
import com.meowsbox.vosp.common.Logger;
import com.meowsbox.vosp.service.SipService;

import static com.meowsbox.vosp.common.Logger.lvVerbose;


/**
 * Fragment for call control buttons
 */
public class CallButtonFragment extends Fragment implements OnMenuItemClickListener, OnDismissListener, View.OnClickListener {
    public final static boolean DEBUG = DialerApplication.DEBUG;
    private static final int INVALID_INDEX = -1;
    // The button is currently visible in the UI
    private static final int BUTTON_VISIBLE = 1;
    // The button is hidden in the UI
    private static final int BUTTON_HIDDEN = 2;
    // The button has been collapsed into the overflow menu
    private static final int BUTTON_MENU = 3;
    // Constants for Drawable.setAlpha()
    private static final int HIDDEN = 0;
    private static final int VISIBLE = 255;
    public final String TAG = this.getClass().getName();
    private Context mContext;
    volatile private InteractionListener mListener;
    private int mButtonMaxVisible;
    private SparseIntArray mButtonVisibilityMap = new SparseIntArray(Buttons.BUTTON_COUNT);
    private CompoundButton mAudioButton;
    private CompoundButton mMuteButton;
    private CompoundButton mRecordButton;
    private CompoundButton mShowDialpadButton;
    private CompoundButton mHoldButton;
    private ImageButton mSwapButton;
    private ImageButton mChangeToVideoButton;
    private CompoundButton mSwitchCameraButton;
    private ImageButton mAddCallButton;
    private ImageButton mMergeButton;
    private CompoundButton mPauseVideoButton;
    private ImageButton mOverflowButton;
    private ImageButton mManageVideoCallConferenceButton;
    private PopupMenu mAudioModePopup;
    private boolean mAudioModePopupVisible;
    private PopupMenu mOverflowPopup;
    private int mPrevAudioMode = 0;
    private boolean mIsEnabled;
    private MaterialPalette mCurrentThemeColors;
    private int primary_color = -1;
    private Logger gLog ;
    private int pjsipCallId = -1;
    //    @Override
//    public CallButtonPresenter createPresenter() {
//        // TODO: find a cleaner way to include audio mode provider than having a singleton instance.
//        return new CallButtonPresenter();
//    }
//    private SipService sipService = null;

    //    @Override
    public void displayDialpad(boolean value, boolean animate) {
        mShowDialpadButton.setSelected(value);
//        if (getActivity() != null && getActivity() instanceof InCallActivity) {
//            ((InCallActivity) getActivity()).showDialpadFragment(value, animate);
//        }
    }

    //    @Override
    public void enableButton(int buttonId, boolean enable) {
        final View button = getButtonById(buttonId);
        if (button != null) {
            button.setEnabled(enable);
        }
    }

    @Override
    public Context getContext() {
        return getActivity();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mContext = context;
        if (context instanceof InteractionListener) {
            mListener = (InteractionListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onAttach(Activity activity) { // <23 API
        super.onAttach(activity);
        mContext = activity;
        if (activity instanceof InteractionListener) {
            mListener = (InteractionListener) activity;
        } else {
            throw new RuntimeException(activity.toString() + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        gLog = ((DialerApplication) getActivity().getApplication()).getLoggerInstanceShared();

        super.onCreate(savedInstanceState);

        mCurrentThemeColors = MaterialColorMapUtils.getDefaultPrimaryAndSecondaryColors(getResources());

        for (int i = 0; i < Buttons.BUTTON_COUNT; i++) {
            mButtonVisibilityMap.put(i, BUTTON_HIDDEN);
        }

        mButtonMaxVisible = 5;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View parent = inflater.inflate(R.layout.call_button_fragment, container, false);

        mAudioButton = (CompoundButton) parent.findViewById(R.id.audioButton);
        mAudioButton.setOnClickListener(this);
        mMuteButton = (CompoundButton) parent.findViewById(R.id.muteButton);
        mMuteButton.setOnClickListener(this);
        mShowDialpadButton = (CompoundButton) parent.findViewById(R.id.dialpadButton);
        mShowDialpadButton.setOnClickListener(this);
        mHoldButton = (CompoundButton) parent.findViewById(R.id.holdButton);
        mHoldButton.setOnClickListener(this);
        mSwapButton = (ImageButton) parent.findViewById(R.id.swapButton);
        mSwapButton.setOnClickListener(this);
        mChangeToVideoButton = (ImageButton) parent.findViewById(R.id.changeToVideoButton);
        mChangeToVideoButton.setOnClickListener(this);
        mSwitchCameraButton = (CompoundButton) parent.findViewById(R.id.switchCameraButton);
        mSwitchCameraButton.setOnClickListener(this);
        mAddCallButton = (ImageButton) parent.findViewById(R.id.addButton);
        mAddCallButton.setOnClickListener(this);
        mMergeButton = (ImageButton) parent.findViewById(R.id.mergeButton);
        mMergeButton.setOnClickListener(this);
        mPauseVideoButton = (CompoundButton) parent.findViewById(R.id.pauseVideoButton);
        mPauseVideoButton.setOnClickListener(this);
        mOverflowButton = (ImageButton) parent.findViewById(R.id.overflowButton);
        mOverflowButton.setOnClickListener(this);
        mManageVideoCallConferenceButton = (ImageButton) parent.findViewById(R.id.manageVideoCallConferenceButton);
        mManageVideoCallConferenceButton.setOnClickListener(this);
        mRecordButton= (CompoundButton) parent.findViewById(R.id.recordButton);
        mRecordButton.setOnClickListener(this);
        setEnabled(false);
        return parent;
    }

    @Override
    public void onDestroy() {
        gLog.flushHint();
        super.onDestroy();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // set the buttons
//        updateAudioButtons(getPresenter().getSupportedAudio());
    }

    @Override
    public void onResume() {
//        if (getPresenter() != null) {
//            getPresenter().refreshMuteState();
//        }
        super.onResume();

        updateColors();
    }

    public int getPjsipCallId() {
        return pjsipCallId;
    }

    public void setPjsipCallId(int pjsipCallId) {
        this.pjsipCallId = pjsipCallId;
    }

//    @Override
//    public CallButtonPresenter.CallButtonUi getUi() {
//        return this;
//    }

    //    @Override
    public boolean isDialpadVisible() {
//        if (getActivity() != null && getActivity() instanceof InCallActivity) {
//            return ((InCallActivity) getActivity()).isDialpadVisible();
//        }
        return false;
    }

    public void setDialpadVisible(boolean value) {
        if (mShowDialpadButton.isSelected() != value) {
            mShowDialpadButton.setSelected(value);
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (DEBUG) gLog.l(TAG, lvVerbose, "onClick(View " + view + ", id " + id + ")...");
        if (pjsipCallId < 0) {
            if (DEBUG) gLog.l(TAG, lvVerbose, "pjsipcallid not set - onClick discarded");
            return;
        }

        switch (id) {
            case R.id.audioButton:
                onAudioButtonClicked();
                break;
            case R.id.addButton:
//                getPresenter().addCallClicked();
                break;
            case R.id.muteButton: {
//                getPresenter().muteClicked(!mMuteButton.isSelected());
//                if (mMuteButton.isSelected()) {
//                    sipService.callMuteResume(pjsipCallId);
//                } else {
//                    sipService.callMute(pjsipCallId);
//                }

                mListener.onBtnMuteClicked();
                break;
            }
            case R.id.recordButton: {
                mListener.onBtnRecordClicked();
                break;
            }
            case R.id.mergeButton:
//                getPresenter().mergeClicked();
//                mMergeButton.setEnabled(false);
                break;
            case R.id.holdButton: {
//                getPresenter().holdClicked(!mHoldButton.isSelected());
//                if (mHoldButton.isSelected()) {
//                    sipService.callHoldResume(pjsipCallId);
//                } else {
//                    sipService.callHold(pjsipCallId);
//                }

                mListener.onBtnHoldClicked();
                break;
            }
            case R.id.swapButton:
//                getPresenter().swapClicked();
                break;
            case R.id.dialpadButton:
//                getPresenter().showDialpadClicked(!mShowDialpadButton.isSelected());
                mListener.onBtnDialpadClicked();
                break;
            case R.id.changeToVideoButton:
//                getPresenter().changeToVideoClicked();
                break;
            case R.id.switchCameraButton:
//                getPresenter().switchCameraClicked(
//                        mSwitchCameraButton.isSelected() /* useFrontFacingCamera */);
                break;
            case R.id.pauseVideoButton:
//                getPresenter().pauseVideoClicked(
//                        !mPauseVideoButton.isSelected() /* pause */);
                break;
            case R.id.overflowButton:
//                if (mOverflowPopup != null) {
//                    mOverflowPopup.show();
//                }
                break;
//            case R.id.manageVideoCallConferenceButton:
//                onManageVideoCallConferenceClicked();
//                break;
            default:
                if (DEBUG) gLog.l(TAG, lvVerbose, "onClick: unexpected");
                return;
        }

        view.performHapticFeedback(
                HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
    }

    // PopupMenu.OnDismissListener implementation; see showAudioModePopup().
    // This gets called when the PopupMenu gets dismissed for *any* reason, like
    // the user tapping outside its bounds, or pressing Back, or selecting one
    // of the menu items.
    @Override
    public void onDismiss(PopupMenu menu) {
        if (DEBUG) gLog.l(TAG, lvVerbose, "- onDismiss: " + menu);
        mAudioModePopupVisible = false;
//        updateAudioButtons(getPresenter().getSupportedAudio());
    }

    /**
     * Refreshes the "Audio mode" popup if it's visible.  This is useful
     * (for example) when a wired headset is plugged or unplugged,
     * since we need to switch back and forth between the "earpiece"
     * and "wired headset" items.
     * <p>
     * This is safe to call even if the popup is already dismissed, or even if
     * you never called showAudioModePopup() in the first place.
     */
    public void refreshAudioModePopup() {
        if (mAudioModePopup != null && mAudioModePopupVisible) {
            // Dismiss the previous one
            mAudioModePopup.dismiss();  // safe even if already dismissed
            // And bring up a fresh PopupMenu
//            showAudioModePopup();
        }
    }

    //    @Override
    public void setAudio(int mode) {
//        updateAudioButtons(getPresenter().getSupportedAudio());
        updateAudioButtons(0);
        refreshAudioModePopup();

//        if (mPrevAudioMode != mode) {
//            updateAudioButtonContentDescription(mode);
//            mPrevAudioMode = mode;
//        }
    }

    //    @Override
    public void setCameraSwitched(boolean isBackFacingCamera) {
        mSwitchCameraButton.setSelected(isBackFacingCamera);
    }

    //    @Override
    public void setEnabled(boolean isEnabled) {
        mIsEnabled = isEnabled;

        mAudioButton.setEnabled(isEnabled);
        mMuteButton.setEnabled(isEnabled);
        mShowDialpadButton.setEnabled(isEnabled);
        mHoldButton.setEnabled(isEnabled);
        mSwapButton.setEnabled(isEnabled);
        mChangeToVideoButton.setEnabled(isEnabled);
        mSwitchCameraButton.setEnabled(isEnabled);
        mAddCallButton.setEnabled(isEnabled);
        mMergeButton.setEnabled(isEnabled);
        mPauseVideoButton.setEnabled(isEnabled);
        mOverflowButton.setEnabled(isEnabled);
        mManageVideoCallConferenceButton.setEnabled(isEnabled);
    }

    //    @Override
    public void setHold(boolean value) {
        if (mHoldButton.isSelected() != value) {
            mHoldButton.setSelected(value);
            mHoldButton.setContentDescription(value ? "Resume Call" : "Hold Call");
        }
    }

    //    @Override
    public void setMute(boolean value) {
        if (mMuteButton.isSelected() != value) {
            mMuteButton.setSelected(value);
        }
    }

    public void setRecord(boolean value) {
        if (mRecordButton.isSelected() != value) {
            mRecordButton.setSelected(value);
        }
    }

    public void setPrimaryColor(int color) {
        MaterialPalette themeColors = mCurrentThemeColors;
        themeColors.mPrimaryColor = color;

        View[] compoundButtons = {
                mAudioButton,
                mMuteButton,
                mRecordButton,
                mShowDialpadButton,
                mHoldButton,
                mSwitchCameraButton,
                mPauseVideoButton
        };

        for (View button : compoundButtons) {
            final LayerDrawable layers = (LayerDrawable) button.getBackground();
            final RippleDrawable btnCompoundDrawable = compoundBackgroundDrawable(themeColors);
            layers.setDrawableByLayerId(R.id.compoundBackgroundItem, btnCompoundDrawable);
        }

        ImageButton[] normalButtons = {
                mSwapButton,
                mChangeToVideoButton,
                mAddCallButton,
                mMergeButton,
                mOverflowButton
        };

        for (ImageButton button : normalButtons) {
            final LayerDrawable layers = (LayerDrawable) button.getBackground();
            final RippleDrawable btnDrawable = backgroundDrawable(themeColors);
//            layers.setDrawableByLayerId(R.id.backgroundItem, btnDrawable);
        }

        mCurrentThemeColors = themeColors;
    }

    //    @Override
    public void setSupportedAudio(int modeMask) {
        updateAudioButtons(modeMask);
        refreshAudioModePopup();
    }

    //    @Override
    public void setVideoPaused(boolean isPaused) {
        mPauseVideoButton.setSelected(isPaused);
    }

    //    @Override
    public void showButton(int buttonId, boolean show) {
        mButtonVisibilityMap.put(buttonId, show ? BUTTON_VISIBLE : BUTTON_HIDDEN);
    }

    /**
     * Iterates through the list of buttons and toggles their visibility depending on the
     * setting configured by the CallButtonPresenter. If there are more visible buttons than
     * the allowed maximum, the excess buttons are collapsed into a single overflow menu.
     */
//    @Override
    public void updateButtonStates() {
        View prevVisibleButton = null;
        int prevVisibleId = -1;
        PopupMenu menu = null;
        int visibleCount = 0;
        for (int i = 0; i < Buttons.BUTTON_COUNT; i++) {
            final int visibility = mButtonVisibilityMap.get(i);
            final View button = getButtonById(i);
            if (visibility == BUTTON_VISIBLE) {
                visibleCount++;
                if (visibleCount <= mButtonMaxVisible) {
                    button.setVisibility(View.VISIBLE);
                    prevVisibleButton = button;
                    prevVisibleId = i;
                } else {
                    if (menu == null) {
                        menu = getPopupMenu();
                    }
                    // Collapse the current button into the overflow menu. If is the first visible
                    // button that exceeds the threshold, also collapse the previous visible button
                    // so that the total number of visible buttons will never exceed the threshold.
                    if (prevVisibleButton != null) {
                        addToOverflowMenu(prevVisibleId, prevVisibleButton, menu);
                        prevVisibleButton = null;
                        prevVisibleId = -1;
                    }
                    addToOverflowMenu(i, button, menu);
                }
            } else if (visibility == BUTTON_HIDDEN) {
                button.setVisibility(View.GONE);
            }
        }

        mOverflowButton.setVisibility(menu != null ? View.VISIBLE : View.GONE);
        if (menu != null) {
            mOverflowPopup = menu;
            mOverflowPopup.setOnMenuItemClickListener(new OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    final int id = item.getItemId();
                    getButtonById(id).performClick();
                    return true;
                }
            });
        }
    }

    public void updateColors() {
//        MaterialPalette themeColors = InCallPresenter.getInstance().getThemeColors();
        MaterialPalette themeColors = mCurrentThemeColors;

        if (mCurrentThemeColors != null && mCurrentThemeColors.equals(themeColors)) {
            return;
        }

        View[] compoundButtons = {
                mAudioButton,
                mMuteButton,
                mRecordButton,
                mShowDialpadButton,
                mHoldButton,
                mSwitchCameraButton,
                mPauseVideoButton
        };

        for (View button : compoundButtons) {
            final LayerDrawable layers = (LayerDrawable) button.getBackground();
            final RippleDrawable btnCompoundDrawable = compoundBackgroundDrawable(themeColors);
            layers.setDrawableByLayerId(R.id.compoundBackgroundItem, btnCompoundDrawable);
        }

        ImageButton[] normalButtons = {
                mSwapButton,
                mChangeToVideoButton,
                mAddCallButton,
                mMergeButton,
                mOverflowButton
        };

        for (ImageButton button : normalButtons) {
            final LayerDrawable layers = (LayerDrawable) button.getBackground();
            final RippleDrawable btnDrawable = backgroundDrawable(themeColors);
//            layers.setDrawableByLayerId(R.id.backgroundItem, btnDrawable);
        }

        mCurrentThemeColors = themeColors;
    }

    // state_focused
    private void addFocused(Resources res, StateListDrawable drawable) {
        int[] focused = {android.R.attr.state_focused};
        Drawable focusedDrawable = res.getDrawable(R.drawable.btn_unselected_focused);
        drawable.addState(focused, focusedDrawable);
    }

    // state_selected
    private void addSelected(Resources res, StateListDrawable drawable, MaterialPalette palette) {
        int[] selected = {android.R.attr.state_selected};
        LayerDrawable selectedDrawable = (LayerDrawable) res.getDrawable(R.drawable.btn_selected);
        ((GradientDrawable) selectedDrawable.getDrawable(0)).setColor(palette.mSecondaryColor);
        drawable.addState(selected, selectedDrawable);
    }

    // state_selected and state_focused
    private void addSelectedAndFocused(Resources res, StateListDrawable drawable) {
        int[] selectedAndFocused = {android.R.attr.state_selected, android.R.attr.state_focused};
        Drawable selectedAndFocusedDrawable = res.getDrawable(R.drawable.btn_selected_focused);
        drawable.addState(selectedAndFocused, selectedAndFocusedDrawable);
    }

    private void addToOverflowMenu(int id, View button, PopupMenu menu) {
        button.setVisibility(View.GONE);
        menu.getMenu().add(Menu.NONE, id, Menu.NONE, button.getContentDescription());
        mButtonVisibilityMap.put(id, BUTTON_MENU);
    }

    // default
    private void addUnselected(Resources res, StateListDrawable drawable, MaterialPalette palette) {
        LayerDrawable unselectedDrawable =
                (LayerDrawable) res.getDrawable(R.drawable.btn_unselected);
        ((GradientDrawable) unselectedDrawable.getDrawable(0)).setColor(palette.mPrimaryColor);
        drawable.addState(new int[0], unselectedDrawable);
    }

    /**
     * Generate a RippleDrawable which will be the background of a button to ensure it
     * is the same color as the rest of the call card.
     */
    private RippleDrawable backgroundDrawable(MaterialPalette palette) {
        Resources res = getResources();
        ColorStateList rippleColor =
                ColorStateList.valueOf(res.getColor(R.color.incall_accent_color));

        StateListDrawable stateListDrawable = new StateListDrawable();
        addFocused(res, stateListDrawable);
        addUnselected(res, stateListDrawable, palette);

        return new RippleDrawable(rippleColor, stateListDrawable, null);
    }

    /**
     * Generate a RippleDrawable which will be the background for a compound button, i.e.
     * a button with pressed and unpressed states. The unpressed state will be the same color
     * as the rest of the call card, the pressed state will be the dark version of that color.
     */
    private RippleDrawable compoundBackgroundDrawable(MaterialPalette palette) {
        Resources res = getResources();
        ColorStateList rippleColor =
                ColorStateList.valueOf(res.getColor(R.color.incall_accent_color));

        StateListDrawable stateListDrawable = new StateListDrawable();
        addSelectedAndFocused(res, stateListDrawable);
        addFocused(res, stateListDrawable);
        addSelected(res, stateListDrawable, palette);
        addUnselected(res, stateListDrawable, palette);

        return new RippleDrawable(rippleColor, stateListDrawable, null);
    }

    private View getButtonById(int id) {
        switch (id) {
            case Buttons.BUTTON_AUDIO:
                return mAudioButton;
            case Buttons.BUTTON_MUTE:
                return mMuteButton;
            case Buttons.BUTTON_RECORD:
                return mRecordButton;
            case Buttons.BUTTON_DIALPAD:
                return mShowDialpadButton;
            case Buttons.BUTTON_HOLD:
                return mHoldButton;
            case Buttons.BUTTON_SWAP:
                return mSwapButton;
            case Buttons.BUTTON_UPGRADE_TO_VIDEO:
                return mChangeToVideoButton;
            case Buttons.BUTTON_SWITCH_CAMERA:
                return mSwitchCameraButton;
            case Buttons.BUTTON_ADD_CALL:
                return mAddCallButton;
            case Buttons.BUTTON_MERGE:
                return mMergeButton;
            case Buttons.BUTTON_PAUSE_VIDEO:
                return mPauseVideoButton;
            case Buttons.BUTTON_MANAGE_VIDEO_CONFERENCE:
                return mManageVideoCallConferenceButton;
            default:
                if (DEBUG) gLog.l(TAG, lvVerbose, "Invalid button id");
                return null;
        }
    }

    private PopupMenu getPopupMenu() {
        return new PopupMenu(new ContextThemeWrapper(getActivity(), R.style.InCallPopupMenuStyle),
                mOverflowButton);
    }

    private boolean isAudio(int mode) {
//        return (mode == getPresenter().getAudioMode());
        return false;
    }

    /**
     * Checks for supporting modes.  If bluetooth is supported, it uses the audio
     * pop up menu.  Otherwise, it toggles the speakerphone.
     */
    private void onAudioButtonClicked() {
//        if (DEBUG) gLog.l(lvVerbose, "onAudioButtonClicked: " + CallAudioState.audioRouteToString(getPresenter().getSupportedAudio()));

        if (mListener.isBlueoothScoAvailable()) {
            showAudioModePopup();
        } else {
//            getPresenter().toggleSpeakerphone();
            mListener.audioRouteSpeakerToggle();
        }
    }

//    private boolean isSupported(int mode) {
////        return (mode == (getPresenter().getSupportedAudio() & mode));
//
//
//        return true;
//    }

    private void onManageVideoCallConferenceClicked() {
        if (DEBUG) gLog.l(TAG, lvVerbose, "onManageVideoCallConferenceClicked");
//        InCallPresenter.getInstance().showConferenceCallManager(true);
    }

    private void showAudioModePopup() {
        if (DEBUG) gLog.l(TAG, lvVerbose, "showAudioPopup()...");

        final ContextThemeWrapper contextWrapper = new ContextThemeWrapper(getActivity(),
                R.style.InCallPopupMenuStyle);
        mAudioModePopup = new PopupMenu(contextWrapper, mAudioButton /* anchorView */);
        mAudioModePopup.getMenuInflater().inflate(R.menu.incall_audio_mode_menu,
                mAudioModePopup.getMenu());
        mAudioModePopup.setOnMenuItemClickListener(this);
        mAudioModePopup.setOnDismissListener(this);

        final Menu menu = mAudioModePopup.getMenu();

        // TODO: Still need to have the "currently active" audio mode come
        // up pre-selected (or focused?) with a blue highlight.  Still
        // need exact visual design, and possibly framework support for this.
        // See comments below for the exact logic.

        final MenuItem speakerItem = menu.findItem(R.id.audio_mode_speaker);
//        speakerItem.setEnabled(isSupported(CallAudioState.ROUTE_SPEAKER));
        // TODO: Show speakerItem as initially "selected" if
        // speaker is on.

        // We display *either* "earpiece" or "wired headset", never both,
        // depending on whether a wired headset is physically plugged in.
        final MenuItem earpieceItem = menu.findItem(R.id.audio_mode_earpiece);
        final MenuItem wiredHeadsetItem = menu.findItem(R.id.audio_mode_wired_headset);

//        final boolean usingHeadset = isSupported(CallAudioState.ROUTE_WIRED_HEADSET);
        final boolean usingHeadset = true;
        earpieceItem.setVisible(!usingHeadset);
        earpieceItem.setEnabled(!usingHeadset);
        wiredHeadsetItem.setVisible(usingHeadset);
        wiredHeadsetItem.setEnabled(usingHeadset);
        // TODO: Show the above item (either earpieceItem or wiredHeadsetItem)
        // as initially "selected" if speakerOn and
        // bluetoothIndicatorOn are both false.

        final MenuItem bluetoothItem = menu.findItem(R.id.audio_mode_bluetooth);
//        bluetoothItem.setEnabled(isSupported(CallAudioState.ROUTE_BLUETOOTH));
        bluetoothItem.setEnabled(mListener.isBlueoothScoAvailable());
        // TODO: Show bluetoothItem as initially "selected" if
        // bluetoothIndicatorOn is true.

        mAudioModePopup.show();

        // Unfortunately we need to manually keep track of the popup menu's
        // visiblity, since PopupMenu doesn't have an isShowing() method like
        // Dialogs do.
        mAudioModePopupVisible = true;
    }

    /**
     * Updates the audio button so that the appriopriate visual layers
     * are visible based on the supported audio formats.
     */
    private void updateAudioButtons(int supportedModes) {
        final boolean bluetoothSupported = mListener.isBlueoothScoAvailable();
        final boolean speakerSupported = true;

        boolean audioButtonEnabled = false;
        boolean audioButtonChecked = false;
        boolean showMoreIndicator = false;

        boolean showBluetoothIcon = false;
        boolean showSpeakerphoneIcon = false;
        boolean showHandsetIcon = false;

        boolean showToggleIndicator = false;

        if (bluetoothSupported) {
            if (DEBUG) gLog.l(TAG, lvVerbose, "updateAudioButtons - popup menu mode");

            audioButtonEnabled = true;
            audioButtonChecked = true;
            showMoreIndicator = true;

            // Update desired layers:
//            if (isAudio(CallAudioState.ROUTE_BLUETOOTH)) {
            if (mListener.getAudioRoute() == SipService.AUDIO_ROUTE_BLUETOOTH) {
                showBluetoothIcon = true;
//            } else if (isAudio(CallAudioState.ROUTE_SPEAKER)) {
            } else if (mListener.getAudioRoute() == SipService.AUDIO_ROUTE_SPEAKER) {
                showSpeakerphoneIcon = true;
            } else {
                showHandsetIcon = true;
                // TODO: if a wired headset is plugged in, that takes precedence
                // over the handset earpiece.  If so, maybe we should show some
                // sort of "wired headset" icon here instead of the "handset
                // earpiece" icon.  (Still need an asset for that, though.)
            }

            // The audio button is NOT a toggle in this state, so set selected to false.
            mAudioButton.setSelected(false);
        } else if (speakerSupported) {
            if (DEBUG) gLog.l(TAG, lvVerbose, "updateAudioButtons - speaker toggle mode");

            audioButtonEnabled = true;

            // The audio button *is* a toggle in this state, and indicated the
            // current state of the speakerphone.
//            audioButtonChecked = isAudio(CallAudioState.ROUTE_SPEAKER);
            audioButtonChecked = mListener.getAudioRoute() == SipService.AUDIO_ROUTE_SPEAKER;
            mAudioButton.setSelected(audioButtonChecked);

            // update desired layers:
            showToggleIndicator = true;
            showSpeakerphoneIcon = true;
        } else {
            if (DEBUG) gLog.l(TAG, lvVerbose, "updateAudioButtons - disabled...");

            // The audio button is a toggle in this state, but that's mostly
            // irrelevant since it's always disabled and unchecked.
            audioButtonEnabled = false;
            audioButtonChecked = false;
            mAudioButton.setSelected(false);

            // update desired layers:
            showToggleIndicator = true;
            showSpeakerphoneIcon = true;
        }

        // Finally, update it all!

        if (DEBUG) gLog.l(TAG, lvVerbose, "audioButtonEnabled: " + audioButtonEnabled);
        if (DEBUG) gLog.l(TAG, lvVerbose, "audioButtonChecked: " + audioButtonChecked);
        if (DEBUG) gLog.l(TAG, lvVerbose, "showMoreIndicator: " + showMoreIndicator);
        if (DEBUG) gLog.l(TAG, lvVerbose, "showBluetoothIcon: " + showBluetoothIcon);
        if (DEBUG) gLog.l(TAG, lvVerbose, "showSpeakerphoneIcon: " + showSpeakerphoneIcon);
        if (DEBUG) gLog.l(TAG, lvVerbose, "showHandsetIcon: " + showHandsetIcon);

        // Only enable the audio button if the fragment is enabled.
        mAudioButton.setEnabled(audioButtonEnabled && mIsEnabled);
        mAudioButton.setChecked(audioButtonChecked);

        final LayerDrawable layers = (LayerDrawable) mAudioButton.getBackground();
        if (DEBUG) gLog.l(TAG, lvVerbose, "'layers' drawable: " + layers);

        layers.findDrawableByLayerId(R.id.compoundBackgroundItem)
                .setAlpha(showToggleIndicator ? VISIBLE : HIDDEN);

        layers.findDrawableByLayerId(R.id.moreIndicatorItem)
                .setAlpha(showMoreIndicator ? VISIBLE : HIDDEN);

        layers.findDrawableByLayerId(R.id.bluetoothItem)
                .setAlpha(showBluetoothIcon ? VISIBLE : HIDDEN);

        layers.findDrawableByLayerId(R.id.handsetItem)
                .setAlpha(showHandsetIcon ? VISIBLE : HIDDEN);

        layers.findDrawableByLayerId(R.id.speakerphoneItem)
                .setAlpha(showSpeakerphoneIcon ? VISIBLE : HIDDEN);

    }

//    /**
//     * Update the content description of the audio button.
//     */
//    private void updateAudioButtonContentDescription(int mode) {
//        int stringId = 0;
//
//        // If bluetooth is not supported, the audio buttion will toggle, so use the label "speaker".
//        // Otherwise, use the label of the currently selected audio mode.
////        if (!isSupported(CallAudioState.ROUTE_BLUETOOTH)) {
//        if (sipService.isBlueoothScoAvailable()) {
//            stringId = R.string.audio_mode_speaker;
//        } else {
//            switch (mode) {
//                case CallAudioState.ROUTE_EARPIECE:
//                    stringId = R.string.audio_mode_earpiece;
//                    break;
//                case CallAudioState.ROUTE_BLUETOOTH:
//                    stringId = R.string.audio_mode_bluetooth;
//                    break;
//                case CallAudioState.ROUTE_WIRED_HEADSET:
//                    stringId = R.string.audio_mode_wired_headset;
//                    break;
//                case CallAudioState.ROUTE_SPEAKER:
//                    stringId = R.string.audio_mode_speaker;
//                    break;
//            }
//        }
//
//        if (stringId != 0) {
//            mAudioButton.setContentDescription(getResources().getString(stringId));
//        }
//    }

    interface InteractionListener {
        void audioRouteSpeakerToggle();

        int getAudioRoute();

        boolean isBlueoothScoAvailable();

        void onBtnAudioBluetoothClicked();

        void onBtnAudioEarClicked();

        void onBtnAudioSpeakerClicked();

        void onBtnDialpadClicked();

        void onBtnHoldClicked();

        void onBtnMuteClicked();

        void onBtnRecordClicked();

    }

    public interface Buttons {
        public static final int BUTTON_AUDIO = 0;
        public static final int BUTTON_MUTE = 1;
        public static final int BUTTON_DIALPAD = 2;
        public static final int BUTTON_HOLD = 3;
        public static final int BUTTON_SWAP = 4;
        public static final int BUTTON_UPGRADE_TO_VIDEO = 5;
        public static final int BUTTON_SWITCH_CAMERA = 6;
        public static final int BUTTON_ADD_CALL = 7;
        public static final int BUTTON_MERGE = 8;
        public static final int BUTTON_PAUSE_VIDEO = 9;
        public static final int BUTTON_MANAGE_VIDEO_CONFERENCE = 10;
        public static final int BUTTON_COUNT = 11;
        public static final int BUTTON_RECORD = 12;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
//        if (DEBUG) gLog.l(lvVerbose, "- onMenuItemClick: " + item);
//        if (DEBUG) gLog.l(lvVerbose, "  id: " + item.getItemId());
//        if (DEBUG) gLog.l(lvVerbose, "  title: '" + item.getTitle() + "'");

//        int mode = CallAudioState.ROUTE_WIRED_OR_EARPIECE;

        switch (item.getItemId()) {
            case R.id.audio_mode_speaker:
//                mode = CallAudioState.ROUTE_SPEAKER;
//                sipService.setAudioRoute(SipService.AUDIO_ROUTE_SPEAKER);
                mListener.onBtnAudioSpeakerClicked();
                break;
            case R.id.audio_mode_earpiece:
            case R.id.audio_mode_wired_headset:
                // InCallCallAudioState.ROUTE_EARPIECE means either the handset earpiece,
                // or the wired headset (if connected.)
//                mode = CallAudioState.ROUTE_WIRED_OR_EARPIECE;
//                sipService.setAudioRoute(SipService.AUDIO_ROUTE_EAR);
                mListener.onBtnAudioEarClicked();
                break;
            case R.id.audio_mode_bluetooth:
//                mode = CallAudioState.ROUTE_BLUETOOTH;
//                sipService.setAudioRoute(SipService.AUDIO_ROUTE_BLUETOOTH);
                mListener.onBtnAudioBluetoothClicked();
                break;
            default:
                if (DEBUG)
                    gLog.l(TAG, lvVerbose, "onMenuItemClick:  unexpected View ID " + item.getItemId() + " (MenuItem = '" + item + "')");
                break;
        }

        setAudio(0);
//        getPresenter().setAudioMode(mode);

        return true;
    }


}
