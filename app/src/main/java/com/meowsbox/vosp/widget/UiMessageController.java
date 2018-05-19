/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.widget;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.meowsbox.vosp.DialtactsActivity;
import com.meowsbox.vosp.IRemoteSipService;
import com.meowsbox.vosp.R;
import com.meowsbox.vosp.SettingsActivity;
import com.meowsbox.vosp.android.dialer.list.SwipeHelper;
import com.meowsbox.vosp.service.InAppNotifications;
import com.meowsbox.vosp.service.Prefs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

/**
 * Generic handler for UI Messages posted from IRemoteSipServiceEvents.
 * Created by dhon on 5/24/2017.
 */

public class UiMessageController {
    /**
     * Handle messages for specifically for incall activity
     */
    public static final int FLAG_HANDLE_INCALL = 1;
    private static final boolean DEBUG = DialtactsActivity.DEBUG;
    private static final String LOCAL_MESSAGE_ID = "local_message_id";
    private final String TAG = this.getClass().getSimpleName();
    public int MESSAGES_VIEW_MAX = 2;
    public float MESSAGES_VIEW_MAX_VIS_RATIO = 1.5f;
    private ArrayList<Bundle> values = new ArrayList<>();
    private int primaryColor = Prefs.DEFAULT_UI_COLOR_PRIMARY;
    private int drawableTint = Color.parseColor("#757575");
    private HashSet<Integer> flags = new HashSet<>();
    private Context viewContext;
    private ListView lvMessages;
    private NotificationCardAdaptor nca;
    private volatile int localId = 0;
    private IRemoteSipService sipService = null;
    private boolean borderless;
    private final SwipeHelper.OnItemGestureListener mCallLogOnItemSwipeListener =
            new SwipeHelper.OnItemGestureListener() {
                @Override
                public void onSwipe(View view) {
                    Bundle tag = (Bundle) view.getTag();
                    if (DEBUG) Log.v("FragmentMessages", "onSwipe " + tag.getInt(LOCAL_MESSAGE_ID));
                    itemRemoveWithItemId(tag.getInt(LOCAL_MESSAGE_ID));
                    try { // if the message has a service message id, notify service the message has been dismissed by user
                        if (tag.containsKey(InAppNotifications.SMId)) {
                            sipService.messageDismiss(tag.getInt(InAppNotifications.SMId));
                        }
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    updateHeight();
                }

                @Override
                public void onTouch() {
                    if (DEBUG) Log.v("FragmentMessages", "onTouch");

                }

                @Override
                public boolean isSwipeEnabled() {
                    return true;
                }
            };

    public UiMessageController(IRemoteSipService sipService, ListView listView) {
        this.viewContext = listView.getContext();
        this.sipService = sipService;
        lvMessages = listView;
        nca = new NotificationCardAdaptor();
        lvMessages.setAdapter(nca);
        lvMessages.setBackgroundColor(primaryColor);
    }

    public UiMessageController flagAdd(int flag) {
        flags.add(flag);
        return this;
    }

    public boolean flagHas(int flag) {
        return flags.contains(flag);
    }

    public UiMessageController flagRemove(int flag) {
        flags.remove(flag);
        return this;
    }

    public void itemRemoveWithSmId(int smid) {
        synchronized (values) {
            for (int i = 0; i < values.size(); i++) {
                if (values.get(i).getInt(InAppNotifications.SMId) == smid) {
                    values.remove(i);
                    break;
                }
            }
            if (nca != null) nca.notifyDataSetChanged();
            updateHeight();
            return;
        }
    }

    public void messageClearAll() {
        synchronized (values) {
            values.clear();
        }
        nca.notifyDataSetChanged();
    }

    public void messageClearByType(final int iAnType) {
        boolean changed = false;
        synchronized (values) {
            for (Iterator<Bundle> iterator = values.iterator(); iterator.hasNext(); ) {
                Bundle i = iterator.next();
                if (i.getInt(InAppNotifications.IAN_TYPE) == iAnType) {
                    iterator.remove();
                    changed = true;
                }
            }
        }
        if (changed) nca.notifyDataSetChanged();
    }

    public void messagePost(final int iAnType, final Bundle bundle) {
        switch (iAnType) {
            case InAppNotifications.TYPE_TEST:
                bundle.putInt(InAppNotifications.VIEW_TYPE, InAppNotifications.VIEWTYPE_TEST);
                itemAdd(bundle);
                break;
            case InAppNotifications.TYPE_INFO_INCALL_TIP_FIND_CALL:
            case InAppNotifications.TYPE_INFO_INCALL_NETWORK_CHANGE:
                if (!flagHas(FLAG_HANDLE_INCALL)) return;
            case InAppNotifications.TYPE_INFO:
            case InAppNotifications.TYPE_INFO_CONNECT_PROGRESS:
            case InAppNotifications.TYPE_INFO_DOZE_EXEMPT:
            case InAppNotifications.TYPE_INFO_BATTERY_SAVER_ENABLED:
                bundle.putInt(InAppNotifications.VIEW_TYPE, InAppNotifications.VIEWTYPE_INFO);
                itemAdd(bundle);
                break;
            case InAppNotifications.TYPE_WARN:
            case InAppNotifications.TYPE_WARN_LOGIN_FAIL:
            case InAppNotifications.TYPE_WARN_NO_NETWORK:
                bundle.putInt(InAppNotifications.VIEW_TYPE, InAppNotifications.VIEWTYPE_WARN);
                itemAdd(bundle);
                break;
            case InAppNotifications.TYPE_CALL_FAILED:
                bundle.putInt(InAppNotifications.VIEW_TYPE, InAppNotifications.VIEWTYPE_CALL_FAILED);
                itemAdd(bundle);
                break;
            case InAppNotifications.TYPE_SETTINGS:
                bundle.putInt(InAppNotifications.VIEW_TYPE, InAppNotifications.VIEWTYPE_SETTINGS);
                itemAdd(bundle);
                break;
            case InAppNotifications.TYPE_PROMO_1:
                bundle.putInt(InAppNotifications.VIEW_TYPE, InAppNotifications.VIEWTYPE_PATRON_1);
                itemAdd(bundle);
                break;
            case InAppNotifications.TYPE_RATEUS_1:
                bundle.putInt(InAppNotifications.VIEW_TYPE, InAppNotifications.VIEWTYPE_RATEUS_1);
                itemAdd(bundle);
                break;
            case InAppNotifications.TYPE_RATEUS_2:
                bundle.putInt(InAppNotifications.VIEW_TYPE, InAppNotifications.VIEWTYPE_RATEUS_2);
                itemAdd(bundle);
                break;
            default:
                if (DEBUG) Log.d("FragmentMessages", "Unhandled InAppNotifications type, message ignored: " + iAnType);
//                SipService.getInstance().getLoggerInstanceShared().l(Logger.lvDebug, "Unhandled InAppNotifications type, message ignored: " + iAnType);
                break;
        }
    }

    public void setBorderlessViews(boolean borderless) {
        this.borderless = borderless;
    }

    public void setPrimaryColor(int color) {
        primaryColor = color;
        lvMessages.setBackgroundColor(primaryColor);
    }

    private synchronized int getNextMessageId() {
        return localId++;
    }

    private void itemAdd(Bundle bundle) {
        synchronized (values) {
            int mid = getNextMessageId();
            bundle.putInt(LOCAL_MESSAGE_ID, mid); // tag message with fragment local private id
            if (values.size() == 0) values.add(bundle);
            else values.add(0, bundle);
        }
        updateHeight();
        if (nca != null) nca.notifyDataSetChanged();
    }

    private void itemRemove(int position) {
        synchronized (values) {
            if (values.size() == 0) return; // already gone?
            if (position < 0 || position > values.size()) return; // sanity check, or double tap
            values.remove(position);
        }
        updateHeight();
        if (nca != null) nca.notifyDataSetChanged();
    }

    private void itemRemoveWithItemId(int localMessageId) {
        synchronized (values) {
            for (int i = 0; i < values.size(); i++) {
                if (values.get(i).getInt(LOCAL_MESSAGE_ID) == localMessageId) {
                    values.remove(i);
                    break;
                }
            }
            if (nca != null) nca.notifyDataSetChanged();
            updateHeight();
            return;
        }
    }

    /**
     * Resize the ListView to show only specified count of child views, otherwise default to wrap content.
     */
    private void updateHeight() {
        if (nca.getCount() >= MESSAGES_VIEW_MAX) {
            View view = nca.getView(0, null, lvMessages);
            view.measure(0, 0);
            int prefHeight = view.getMeasuredHeight() + lvMessages.getDividerHeight();
            prefHeight *= MESSAGES_VIEW_MAX_VIS_RATIO;
            ViewGroup.LayoutParams layoutParams = lvMessages.getLayoutParams();
            if (layoutParams.height != prefHeight) { // only update layoutparam if different
                layoutParams.height = prefHeight;
                lvMessages.setLayoutParams(layoutParams);
                lvMessages.requestLayout();
            }
        } else {
            ViewGroup.LayoutParams layoutParams = lvMessages.getLayoutParams();
            if (layoutParams.height != ViewGroup.LayoutParams.WRAP_CONTENT) { // only update layoutparam if different
                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                lvMessages.setLayoutParams(layoutParams);
                lvMessages.requestLayout();
            }
        }
    }

    public class NotificationCardAdaptor extends BaseAdapter {

        @Override
        public int getCount() {
            synchronized (values) {
                return values.size();
            }
        }

        @Override
        public Object getItem(int position) {
            synchronized (values) {
                return values.get(position);
            }
        }

        @Override
        public long getItemId(int position) {
            return ((Bundle) getItem(position)).getInt(LOCAL_MESSAGE_ID);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Bundle item = (Bundle) getItem(position);
            final SwipeableNotificationCard wrapper;
            if (convertView == null) {
                wrapper = new SwipeableNotificationCard(parent.getContext());
                wrapper.setOnItemSwipeListener(mCallLogOnItemSwipeListener);
            } else {
                wrapper = (SwipeableNotificationCard) convertView;
            }

            // Special case wrapper view for the most recent call log item. This allows
            // us to create a card-like effect for the more recent call log item in
            // the PhoneFavoriteMergedAdapter, but keep the original look of the item in
            // the CallLogAdapter.
//            final View view = mCallLogAdapter.getView(position, convertView == null ?
//                    null : wrapper.getChildAt(0), parent
//            );
//            final View view = newChildView(parent.getContext(), parent);
            final View view = newItemView(position, convertView, parent);
            wrapper.removeAllViews();
            wrapper.prepareChildView(view);
            wrapper.addView(view);

            view.setTag(item);

            return wrapper;
        }

        public Object getItemById(int id) {
            synchronized (values) {
                for (int i = 0; i < values.size(); i++) {
                    if (values.get(i).getInt(LOCAL_MESSAGE_ID) == id) return values.get(i);
                }
            }
            return null;
        }

        public int getItemPositionById(int id) {
            synchronized (values) {
                for (int i = 0; i < values.size(); i++) {
                    if (values.get(i).getInt(LOCAL_MESSAGE_ID) == id) return i;
                }
            }
            return -1;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public int getItemViewType(int position) {
            Bundle item = (Bundle) getItem(position);
            return item.getInt(InAppNotifications.VIEW_TYPE);
        }

//        private View newChildView(Context context, ViewGroup parent) {
//            LayoutInflater inflater = LayoutInflater.from(context);
//            View view = inflater.inflate(R.layout.compf_img_text, parent, false);
//            View primaryActionView = view.findViewById(R.id.llItemCardView);
//            primaryActionView.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    if (DEBUG) Log.v(TAG, "onClick primary_action_view");
//                }
//            });
//            return view;
//        }

        /**
         * Bind an onClickListener to the child view based on ItemMeta onclick hint
         *
         * @param bundle
         * @param view
         */
        private void attachOnClickListener(final Bundle bundle, View view) {
            if (bundle.containsKey(InAppNotifications.KEY_ONCLICK_HINT)) {
                switch (bundle.getInt(InAppNotifications.KEY_ONCLICK_HINT)) {
                    case InAppNotifications.VALUE_ONCLICK_HINT_TEST:
                        //noop
                        break;
                    case InAppNotifications.VALUE_ONCLICK_HINT_SETTINGS:
                        view.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                itemRemove(getItemPositionById(bundle.getInt(LOCAL_MESSAGE_ID)));
                                if (bundle.containsKey(InAppNotifications.SMId)) {
                                    try {
                                        sipService.messageDismiss(bundle.getInt(InAppNotifications.SMId));
                                    } catch (RemoteException e) {
                                        if (DEBUG) e.printStackTrace();
                                    }
                                }
                                final Intent intent = new Intent(v.getContext(), SettingsActivity.class);
                                viewContext.startActivity(intent);
                            }
                        });
                        break;
                    case InAppNotifications.VALUE_ONCLICK_HINT_CALL_FAILED:
                        view.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                itemRemove(getItemPositionById(bundle.getInt(LOCAL_MESSAGE_ID)));
                                try {
                                    final String url_help_call_failed = sipService.getLocalString("url_help_call_failed", "file:///android_asset/help_call_failed-en/index.html");
                                    AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext(), R.style.Theme_AppCompat_Dialog);
                                    WebView wv = new WebView(v.getContext());
                                    wv.loadUrl(url_help_call_failed);
                                    wv.setWebViewClient(new WebViewClient() {
                                        @Override
                                        public boolean shouldOverrideUrlLoading(WebView view, String url) {
                                            view.loadUrl(url);
                                            return true;
                                        }
                                    });
                                    builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    });
                                    builder.setView(wv);
                                    builder.create().show();
                                } catch (RemoteException e) {
                                    if (DEBUG) e.printStackTrace();
                                }
                            }
                        });
                        break;
                    case InAppNotifications.VALUE_ONCLICK_HINT_DOZE_DISABLE_HELP:
                        try {
                            final String url = sipService.getLocalString("url_help_doze_disable", "file:///android_asset/help_doze_disable-en/index.html");
                            view.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    itemRemove(getItemPositionById(bundle.getInt(LOCAL_MESSAGE_ID)));
                                    AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext(), R.style.Theme_AppCompat_Dialog);
                                    WebView wv = new WebView(v.getContext());
                                    wv.loadUrl(url);
                                    wv.setWebViewClient(new WebViewClient() {
                                        @Override
                                        public boolean shouldOverrideUrlLoading(WebView view, String url) {
                                            view.loadUrl(url);
                                            return true;
                                        }
                                    });
                                    builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    });
                                    builder.setView(wv);
                                    builder.create().show();
                                }
                            });
                        } catch (RemoteException e) {
                            if (DEBUG) e.printStackTrace();
                        }
                        break;
                    case InAppNotifications.VALUE_ONCLICK_HINT_DOZE_CONTROLLER_HELP:
                        try {
                            final String url = sipService.getLocalString("url_help_doze_disable", "file:///android_asset/help_doze_controller-en/index.html");
                            view.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    itemRemove(getItemPositionById(bundle.getInt(LOCAL_MESSAGE_ID)));
                                    AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext(), R.style.Theme_AppCompat_Dialog);
                                    WebView wv = new WebView(v.getContext());
                                    wv.loadUrl(url);
                                    wv.setWebViewClient(new WebViewClient() {
                                        @Override
                                        public boolean shouldOverrideUrlLoading(WebView view, String url) {
                                            view.loadUrl(url);
                                            return true;
                                        }
                                    });
                                    builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    });
                                    builder.setView(wv);
                                    builder.create().show();
                                }
                            });
                        } catch (RemoteException e) {
                            if (DEBUG) e.printStackTrace();
                        }
                        break;
                    case InAppNotifications.VALUE_ONCLICK_HINT_DOZE_AM_RELAX_HELP:
                        try {
                            final String url = sipService.getLocalString("url_help_doze_am_relax", "file:///android_asset/help_doze_am_relax-en/index.html");
                            view.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    itemRemove(getItemPositionById(bundle.getInt(LOCAL_MESSAGE_ID)));
                                    AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext(), R.style.Theme_AppCompat_Dialog);
                                    WebView wv = new WebView(v.getContext());
                                    wv.loadUrl(url);
                                    wv.setWebViewClient(new WebViewClient() {
                                        @Override
                                        public boolean shouldOverrideUrlLoading(WebView view, String url) {
                                            view.loadUrl(url);
                                            return true;
                                        }
                                    });
                                    builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    });
                                    builder.setView(wv);
                                    builder.create().show();
                                }
                            });
                        } catch (RemoteException e) {
                            if (DEBUG) e.printStackTrace();
                        }
                        break;
                    case InAppNotifications.VALUE_ONCLICK_HINT_DOZE_LIGHT_ONLY_HELP:
                        try {
                            final String url = sipService.getLocalString("url_help_doze_light_only", "file:///android_asset/help_doze_light_only-en/index.html");
                            view.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    itemRemove(getItemPositionById(bundle.getInt(LOCAL_MESSAGE_ID)));
                                    AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext(), R.style.Theme_AppCompat_Dialog);
                                    WebView wv = new WebView(v.getContext());
                                    wv.loadUrl(url);
                                    wv.setWebViewClient(new WebViewClient() {
                                        @Override
                                        public boolean shouldOverrideUrlLoading(WebView view, String url) {
                                            view.loadUrl(url);
                                            return true;
                                        }
                                    });
                                    builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    });
                                    builder.setView(wv);
                                    builder.create().show();
                                }
                            });
                        } catch (RemoteException e) {
                            if (DEBUG) e.printStackTrace();
                        }
                        break;
                    case InAppNotifications.VALUE_ONCLICK_HINT_GOPRO:
                        view.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                try {
                                    new DialogPremiumUpgrade().buildAndShow(v.getContext(), sipService);
                                } catch (RemoteException e) {
                                    if (DEBUG) e.printStackTrace();
                                }
                            }
                        });
                        break;
                    case InAppNotifications.VALUE_ONCLICK_HINT_GORATEUS_LOCAL:
                        view.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                try {
                                    sipService.messageDismiss(bundle.getInt(InAppNotifications.SMId));
                                    new DialogRateUsLocal().buildAndShow(v.getContext(), sipService);
                                } catch (RemoteException e) {
                                    if (DEBUG) e.printStackTrace();
                                }
                            }
                        });
                        break;
                    case InAppNotifications.VALUE_ONCLICK_HINT_GOPLAYAPP:
                        view.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                try {
                                    sipService.messageDismiss(bundle.getInt(InAppNotifications.SMId));
                                    sipService.rsSetLong(Prefs.KEY_FLAG_RATE_PLAYAPP_TS, System.currentTimeMillis());
                                    sipService.rsCommit(true);
                                    viewContext.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.meowsbox.vosp")));
                                } catch (RemoteException e) {
                                    if (DEBUG) e.printStackTrace();
                                }
                            }
                        });
                        break;
                    case InAppNotifications.VALUE_ONCLICK_HINT_DOZE_OPTIMIZE_HELP:
                        view.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                try {
                                    sipService.messageDismiss(bundle.getInt(InAppNotifications.SMId));
                                    new DialogHelpDozeExempt().buildAndShow(v.getContext(),sipService);
                                } catch (RemoteException e) {
                                    if (DEBUG) e.printStackTrace();
                                }
                            }
                        });
                        break;
                    case InAppNotifications.VALUE_ONCLICK_HINT_GO_POWER_SAVE_MODE:
                        view.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                try {
                                    sipService.messageDismiss(bundle.getInt(InAppNotifications.SMId));
                                    viewContext.startActivity(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS));
                                } catch (RemoteException e) {
                                    if (DEBUG) e.printStackTrace();
                                }
                            }
                        });
                        break;


                    default:
                        if (DEBUG) Log.d(TAG, "unhandled onclick hint");
                        break;
                }
            } else {
                view.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (DEBUG) Log.d(TAG, "unspecified onClick primary_action_view");
                    }
                });
            }

        }

        /**
         * Returns a populated message specific view.
         *
         * @param position
         * @param convertView
         * @param parent
         * @return
         */
        private View newItemView(int position, View convertView, ViewGroup parent) {
            Bundle item = (Bundle) getItem(position);
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View view = null;
            switch (item.getInt(InAppNotifications.VIEW_TYPE)) {
                case InAppNotifications.VIEWTYPE_TEST: {
                    view = inflater.inflate(R.layout.compf_img_text, parent, false);
                    TextView tvvalue = (TextView) view.findViewById(R.id.tvValue);
                    tvvalue.setText(item.getString("message"));
                    if (item.containsKey("message2")) {
                        TextView tvvalue2 = (TextView) view.findViewById(R.id.tvValue2);
                        tvvalue2.setText(item.getString("message2"));
                        tvvalue2.setVisibility(View.VISIBLE);
                    }
                    View primaryActionView = view.findViewById(R.id.llItemCardView);
                    attachOnClickListener(item, primaryActionView);
                }
                break;
                case InAppNotifications.VIEWTYPE_INFO: {
                    view = inflater.inflate(R.layout.compf_img_text, parent, false);
                    ImageView ivIcon = (ImageView) view.findViewById(R.id.ivIcon);
                    Drawable drawable = viewContext.getResources().getDrawable(R.drawable.ic_error_outline_black_24dp);
                    drawable.setTint(drawableTint);
                    ivIcon.setImageDrawable(drawable);
                    TextView tvvalue = (TextView) view.findViewById(R.id.tvValue);
                    tvvalue.setText(item.getString("message"));
                    if (item.containsKey("message2")) {
                        TextView tvvalue2 = (TextView) view.findViewById(R.id.tvValue2);
                        tvvalue2.setText(item.getString("message2"));
                        tvvalue2.setVisibility(View.VISIBLE);
                    }
                    View primaryActionView = view.findViewById(R.id.llItemCardView);
                    attachOnClickListener(item, primaryActionView);
                }
                break;
                case InAppNotifications.VIEWTYPE_WARN: {
                    view = inflater.inflate(R.layout.compf_img_text, parent, false);
                    ImageView ivIcon = (ImageView) view.findViewById(R.id.ivIcon);
                    Drawable drawable = viewContext.getResources().getDrawable(R.drawable.ic_warning_black_24dp);
                    drawable.setTint(drawableTint);
                    ivIcon.setImageDrawable(drawable);
                    TextView tvvalue = (TextView) view.findViewById(R.id.tvValue);
                    tvvalue.setText(item.getString("message"));
                    if (item.containsKey("message2")) {
                        TextView tvvalue2 = (TextView) view.findViewById(R.id.tvValue2);
                        tvvalue2.setText(item.getString("message2"));
                        tvvalue2.setVisibility(View.VISIBLE);
                    }
                    View primaryActionView = view.findViewById(R.id.llItemCardView);
                    attachOnClickListener(item, primaryActionView);
                }
                break;
                case InAppNotifications.VIEWTYPE_SETTINGS: {
                    view = inflater.inflate(R.layout.compf_img_text, parent, false);
                    ImageView ivIcon = (ImageView) view.findViewById(R.id.ivIcon);
                    Drawable drawable = viewContext.getResources().getDrawable(R.drawable.ic_settings_black_24dp);
                    drawable.setTint(drawableTint);
                    ivIcon.setImageDrawable(drawable);
                    TextView tvvalue = (TextView) view.findViewById(R.id.tvValue);
                    tvvalue.setText(item.getString("message"));
                    if (item.containsKey("message2")) {
                        TextView tvvalue2 = (TextView) view.findViewById(R.id.tvValue2);
                        tvvalue2.setText(item.getString("message2"));
                        tvvalue2.setVisibility(View.VISIBLE);
                    }
                    View primaryActionView = view.findViewById(R.id.llItemCardView);
                    attachOnClickListener(item, primaryActionView);
                }
                break;
                case InAppNotifications.VIEWTYPE_CALL_FAILED: {
                    view = inflater.inflate(R.layout.compf_img_text, parent, false);
                    ImageView ivIcon = (ImageView) view.findViewById(R.id.ivIcon);
                    Drawable drawable = viewContext.getResources().getDrawable(R.drawable.ic_call_failed_48dp);
                    drawable.setTint(drawableTint);
                    ivIcon.setImageDrawable(drawable);
                    TextView tvvalue = (TextView) view.findViewById(R.id.tvValue);
                    tvvalue.setText(item.getString("message"));
                    if (item.containsKey("message2")) {
                        TextView tvvalue2 = (TextView) view.findViewById(R.id.tvValue2);
                        tvvalue2.setText(item.getString("message2"));
                        tvvalue2.setVisibility(View.VISIBLE);
                    }
                    View primaryActionView = view.findViewById(R.id.llItemCardView);
                    attachOnClickListener(item, primaryActionView);
                }
                break;
                case InAppNotifications.VIEWTYPE_PATRON_1: {
                    view = inflater.inflate(R.layout.compf_pro_features, parent, false);

                    ImageView ivIcon = (ImageView) view.findViewById(R.id.ivIcon);
                    Drawable drawable = viewContext.getResources().getDrawable(R.drawable.ic_sentiment_very_satisfied_black_24dp);
                    drawable.setTint(drawableTint);
                    ivIcon.setImageDrawable(drawable);
//                    ivIcon.setImageResource(R.drawable.ic_sentiment_very_satisfied_black_24dp);

                    TextView tvvalue = (TextView) view.findViewById(R.id.tvValue);
                    tvvalue.setText(item.getString("message"));
//                    tvvalue.setTextColor(Color.WHITE);
                    if (item.containsKey("message2")) {
                        TextView tvvalue2 = (TextView) view.findViewById(R.id.tvValue2);
                        tvvalue2.setText(item.getString("message2"));
                        tvvalue2.setVisibility(View.VISIBLE);
//                        tvvalue2.setTextColor(Color.WHITE);
                    }
                    View primaryActionView = view.findViewById(R.id.llItemCardView);
                    attachOnClickListener(item, primaryActionView);
                }
                break;
                case InAppNotifications.VIEWTYPE_RATEUS_1: {
                    view = inflater.inflate(R.layout.compf_pro_features, parent, false);

                    ImageView ivIcon = (ImageView) view.findViewById(R.id.ivIcon);
                    Drawable drawable = viewContext.getResources().getDrawable(R.drawable.ic_sentiment_very_satisfied_black_24dp);
                    drawable.setTint(drawableTint);
                    ivIcon.setImageDrawable(drawable);
//                    ivIcon.setImageResource(R.drawable.ic_sentiment_very_satisfied_black_24dp);

                    TextView tvvalue = (TextView) view.findViewById(R.id.tvValue);
                    tvvalue.setText(item.getString("message"));
//                    tvvalue.setTextColor(Color.WHITE);
                    if (item.containsKey("message2")) {
                        TextView tvvalue2 = (TextView) view.findViewById(R.id.tvValue2);
                        tvvalue2.setText(item.getString("message2"));
                        tvvalue2.setVisibility(View.VISIBLE);
//                        tvvalue2.setTextColor(Color.WHITE);
                    }
                    View primaryActionView = view.findViewById(R.id.llItemCardView);
                    attachOnClickListener(item, primaryActionView);
                }
                break;
                case InAppNotifications.VIEWTYPE_RATEUS_2: {
                    view = inflater.inflate(R.layout.compf_pro_features, parent, false);

                    ImageView ivIcon = (ImageView) view.findViewById(R.id.ivIcon);
                    ivIcon.setImageResource(R.mipmap.ic_round_launcher_play_store);

                    TextView tvvalue = (TextView) view.findViewById(R.id.tvValue);
                    tvvalue.setText(item.getString("message"));
//                    tvvalue.setTextColor(Color.WHITE);
                    if (item.containsKey("message2")) {
                        TextView tvvalue2 = (TextView) view.findViewById(R.id.tvValue2);
                        tvvalue2.setText(item.getString("message2"));
                        tvvalue2.setVisibility(View.VISIBLE);
//                        tvvalue2.setTextColor(Color.WHITE);
                    }
                    View primaryActionView = view.findViewById(R.id.llItemCardView);
                    attachOnClickListener(item, primaryActionView);
                }
                break;
                default:
                    new RuntimeException("Unhandled ItemMeta ViewType");
            }
            return view;
        }
    }

    /**
     * Fork of Google's ShortcutCardsAdapter
     */
    class SwipeableNotificationCard extends FrameLayout implements SwipeHelper.SwipeHelperCallback {

        private static final float CLIP_CARD_BARELY_HIDDEN_RATIO = 0.001f;
        private static final float CLIP_CARD_MOSTLY_HIDDEN_RATIO = 0.9f;
        // Fade out 5x faster than the hidden ratio.
        private static final float CLIP_CARD_OPACITY_RATIO = 5f;
        private final int mCallLogMarginHorizontal;
        private final int mCallLogMarginTop;
        private final int mCallLogMarginBottom;
        private final int mCallLogPaddingStart;
        private final int mCallLogPaddingTop;
        private final int mCallLogPaddingBottom;
        private final int mCardMaxHorizontalClip;
        private int mShortCardBackgroundColor;
        private SwipeHelper mSwipeHelper;
        private SwipeHelper.OnItemGestureListener mOnItemSwipeListener;
        private float mPreviousTranslationZ = 0;
        private Rect mClipRect = new Rect();

        public SwipeableNotificationCard(Context context) {
            super(context);

            final Resources resources = context.getResources();
            mCardMaxHorizontalClip = resources.getDimensionPixelSize(R.dimen.recent_call_log_item_horizontal_clip_limit);
            mCallLogMarginHorizontal = resources.getDimensionPixelSize(R.dimen.recent_call_log_item_margin_horizontal);
            mCallLogMarginTop = resources.getDimensionPixelSize(R.dimen.recent_call_log_item_margin_top);
//            mCallLogMarginBottom = resources.getDimensionPixelSize(R.dimen.recent_call_log_item_margin_bottom);
            mCallLogMarginBottom = 0;
            mCallLogPaddingStart = resources.getDimensionPixelSize(R.dimen.recent_call_log_item_padding_start);
            mCallLogPaddingTop = resources.getDimensionPixelSize(R.dimen.recent_call_log_item_padding_top);
            mCallLogPaddingBottom = resources.getDimensionPixelSize(R.dimen.recent_call_log_item_padding_bottom);
            mShortCardBackgroundColor = resources.getColor(R.color.call_log_expanded_background_color);

            final float densityScale = viewContext.getResources().getDisplayMetrics().density;
            final float pagingTouchSlop = ViewConfiguration.get(context).getScaledPagingTouchSlop();
            mSwipeHelper = new SwipeHelper(context, SwipeHelper.X, this, densityScale, pagingTouchSlop);
        }

        /**
         * Clips the card by a specified amount.
         *
         * @param ratioHidden A float indicating how much of each edge of the card should be
         *                    clipped. If 0, the entire card is displayed. If 0.5f, each edge is hidden
         *                    entirely, thus obscuring the entire card.
         */
        public void clipCard(float ratioHidden) {
            final View viewToClip = getChildAt(0);
            if (viewToClip == null) {
                return;
            }
            int width = viewToClip.getWidth();
            int height = viewToClip.getHeight();

            if (ratioHidden <= CLIP_CARD_BARELY_HIDDEN_RATIO) {
                viewToClip.setTranslationZ(mPreviousTranslationZ);
            } else if (viewToClip.getTranslationZ() != 0) {
                mPreviousTranslationZ = viewToClip.getTranslationZ();
                viewToClip.setTranslationZ(0);
            }

            if (ratioHidden > CLIP_CARD_MOSTLY_HIDDEN_RATIO) {
                mClipRect.set(0, 0, 0, 0);
                setVisibility(View.INVISIBLE);
            } else {
                setVisibility(View.VISIBLE);
                int newTop = (int) (ratioHidden * height);
                mClipRect.set(0, newTop, width, height);

                // Since the pane will be overlapping with the action bar, apply a vertical offset
                // to top align the clipped card in the viewable area;
                viewToClip.setTranslationY(-newTop);
            }
            viewToClip.setClipBounds(mClipRect);

            // If the view has any children, fade them out of view.
            final ViewGroup viewGroup = (ViewGroup) viewToClip;
            setChildrenOpacity(
                    viewGroup, Math.max(0, 1 - (CLIP_CARD_OPACITY_RATIO * ratioHidden)));
        }

        @Override
        public View getChildAtPosition(MotionEvent ev) {
            return getChildCount() > 0 ? getChildAt(0) : null;
        }

        @Override
        public View getChildContentView(View v) {
//            return v.findViewById(R.id.call_log_list_item);
            return v;
        }

        @Override
        public void onScroll() {
        }

        @Override
        public boolean canChildBeDismissed(View v) {
            Bundle tag = (Bundle) v.getTag();
            return !tag.getBoolean(InAppNotifications.FLAG_NO_DISMISS, false);
        }

        @Override
        public void onBeginDrag(View v) {
            // We do this so the underlying ScrollView knows that it won't get
            // the chance to intercept events anymore
            requestDisallowInterceptTouchEvent(true);
        }

        @Override
        public void onChildDismissed(View v) {
            if (v != null && mOnItemSwipeListener != null) {
                mOnItemSwipeListener.onSwipe(v);
            }
        }

        @Override
        public void onDragCancelled(View v) {
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (mSwipeHelper != null) {
                return mSwipeHelper.onInterceptTouchEvent(ev) || super.onInterceptTouchEvent(ev);
            } else {
                return super.onInterceptTouchEvent(ev);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (mSwipeHelper != null) {
                return mSwipeHelper.onTouchEvent(ev) || super.onTouchEvent(ev);
            } else {
                return super.onTouchEvent(ev);
            }
        }

        public void setOnItemSwipeListener(SwipeHelper.OnItemGestureListener listener) {
            mOnItemSwipeListener = listener;
        }

        private void prepareChildView(View view) {
            // Override CallLogAdapter's accessibility behavior; don't expand the shortcut card.
            view.setAccessibilityDelegate(null);
            view.setBackgroundResource(R.drawable.rounded_corner_bg);

            if (!borderless) {
                final LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                params.setMargins(mCallLogMarginHorizontal, mCallLogMarginTop, mCallLogMarginHorizontal, mCallLogMarginBottom);
                view.setLayoutParams(params);

                LinearLayout cardView = (LinearLayout) view.findViewById(R.id.llItemCardView);
                cardView.setPaddingRelative(mCallLogPaddingStart, mCallLogPaddingTop, cardView.getPaddingEnd(), mCallLogPaddingBottom);
            }

            // TODO: Set content description including type/location and time information.
//            TextView nameView = (TextView) cardView.findViewById(R.id.tvValue);
//            cardView.setContentDescription(getResources().getString(
//                    R.string.description_call_back_action, nameView.getText()));

            mPreviousTranslationZ = viewContext.getResources().getDimensionPixelSize(
                    R.dimen.recent_call_log_item_translation_z);
            view.setTranslationZ(mPreviousTranslationZ);

            final LinearLayout callLogItem = (LinearLayout) view.findViewById(R.id.llListItem);
            // Reset the internal call log item view if it is being recycled
            callLogItem.setTranslationX(0);
            callLogItem.setTranslationY(0);
            callLogItem.setAlpha(1);
            callLogItem.setClipBounds(null);
            setChildrenOpacity(callLogItem, 1.0f);
//            callLogItem.findViewById(R.id.llListItem)
//                    .setBackgroundColor(mShortCardBackgroundColor);
//            callLogItem.findViewById(R.id.call_indicator_icon).setVisibility(View.VISIBLE);
        }

        private void setChildrenOpacity(ViewGroup viewGroup, float alpha) {
            final int count = viewGroup.getChildCount();
            for (int i = 0; i < count; i++) {
                viewGroup.getChildAt(i).setAlpha(alpha);
            }
        }
    }

}
