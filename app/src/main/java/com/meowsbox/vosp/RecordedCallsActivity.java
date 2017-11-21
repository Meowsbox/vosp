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
import android.content.ContentUris;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.support.design.widget.BaseTransientBottomBar;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import com.meowsbox.vosp.android.common.ContactPhotoManager;
import com.meowsbox.vosp.android.common.util.BitmapUtil;
import com.meowsbox.vosp.android.dialer.callog.CallTypeIconsView;
import com.meowsbox.vosp.common.Logger;
import com.meowsbox.vosp.service.Prefs;
import com.meowsbox.vosp.service.licensing.LicensingManager;
import com.meowsbox.vosp.widget.DialogWavPlayer;

import java.io.File;
import java.text.Collator;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;

public class RecordedCallsActivity extends Activity implements ServiceBindingController.ServiceConnectionEvents, Toolbar.OnMenuItemClickListener {
    public final static boolean DEBUG = DialerApplication.DEBUG;
    public final static boolean DEV = DialerApplication.DEV;
    public static final int SORT_DATE_ASC = 1;
    public static final int SORT_DATE_DSC = 2;
    public static final int SORT_DIR_ASC = 3;
    public static final int SORT_DIR_DSC = 4;
    public static final int SORT_CONTACT_ASC = 5;
    public static final int SORT_CONTACT_DSC = 6;
    final static int MENU_ID_REFRESH = 11;
    final static int MENU_ID_DELETE_ALL = 12;
    static Logger gLog;
    public final String TAG = this.getClass().getName();
    private DialogWavPlayer dialogWavPlayer;
    private int iconWidth, iconHeight;
    private IRemoteSipService sipService = null;
    private ServiceBindingController mServiceController = null;
    private boolean isPrem = false;
    private EventHandler sipServiceEventHandler = new EventHandler();
    private LinkedList<CallRecMeta> llRecCallFiles = new LinkedList<>();
    private LinkedList<Snackbar> llSnackBars = new LinkedList<>();
    private ContactPhotoManager mContactPhotoManager;
    private Toolbar mtoolBar;
    private RecyclerView rvList;

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, item.getTitle());
        switch (item.getItemId()) {
            case MENU_ID_REFRESH:
                snackbarFlush();
                loadFileList();
                rvList.getAdapter().notifyDataSetChanged();
                return true;
            case MENU_ID_DELETE_ALL:
                return true;
        }
        return false;
    }

    @Override
    public void onServiceConnectTimeout() {
        finish();
    }

    @Override
    public void onServiceConnected(IRemoteSipService remoteService) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "onServiceConnected");
        sipService = remoteService;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    //TODO retrv set isPrem
                    refreshLicenseState(sipService);
                    populateUi();
                    sipService.eventSubscribe(sipServiceEventHandler, -1);
                } catch (RemoteException e) {
                    if (DEBUG) {
                        gLog.l(TAG, Logger.lvDebug, e);
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    @Override
    public void onServiceDisconnected() {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "onServiceDisconnected");
        sipService = null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
//        if (DEBUG) gLog.lBegin();
        setTheme(R.style.DialtactsThemeAppCompat);
        gLog = ((DialerApplication) getApplication()).getLoggerInstanceShared();
        super.onCreate(savedInstanceState);
        final ActionBar actionBar = getActionBar();
        if (actionBar != null) actionBar.hide();
        mServiceController = new ServiceBindingController(gLog, this, this);
//        if (DEBUG) gLog.lEnd();
        iconHeight = (int) getResources().getDimension(android.R.dimen.notification_large_icon_height);
        iconWidth = (int) getResources().getDimension(android.R.dimen.notification_large_icon_width);
        mContactPhotoManager = ContactPhotoManager.getInstance(this);
    }

    @Override
    protected void onResume() {
        if (mServiceController != null) mServiceController.bindToService();
        super.onResume();
    }

    @Override
    protected void onPause() {
        snackbarFlush();
        if (dialogWavPlayer != null) dialogWavPlayer.dismiss();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        try {
            if (sipService != null)
                sipService.eventUnSubscribe(sipServiceEventHandler, -1);
        } catch (Exception e) {
            if (DEBUG) e.printStackTrace();
        }
        mServiceController.unbindToService();
        gLog.flushHint();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    /**
     * Get the call date/time of the call, relative to the current time.
     * e.g. 3 minutes ago
     * <p>
     * FROM com.meowsbox.vosp.android.dialer.PhoneCallDetailsHelper#getCallDate(com.meowsbox.vosp.android.dialer.PhoneCallDetails
     *
     * @return String representing when the call occurred.
     */
    private CharSequence getCallDate(String date, String time) {
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        try {
            final Date datep = sdf.parse(date + "_" + time);
//            return DateUtils.getRelativeTimeSpanString(datep.getTime(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE);
            final CharSequence relativeTimeSpanString = DateUtils.getRelativeTimeSpanString(datep.getTime(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE);
            final StringBuilder sb = new StringBuilder();
            sb.append(relativeTimeSpanString);
            sb.append(' ');
            sb.append("at");
            sb.append(' ');
            sb.append(time.substring(0, 2));
            sb.append(':');
            sb.append(time.substring(2, 4));
            sb.append(':');
            sb.append(time.substring(4, 6));
//            sb.append("");
            return sb.toString();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String getString(String key, String defaultValue) {
        if (sipService == null) return defaultValue;
        try {
            return sipService.getLocalString(key, defaultValue);
        } catch (RemoteException e) {
            e.printStackTrace();
            return defaultValue;
        }
    }

    private void loadFileList() {
        synchronized (llRecCallFiles) {
            llRecCallFiles.clear();
            try {
                File dir = new File(sipService.getAppExtStoragePath());
                final File[] files = dir.listFiles();
                for (File file : files) {
                    final CallRecMeta callRecMeta = new CallRecMeta(file.getName());
                    if (callRecMeta.isValid) llRecCallFiles.add(callRecMeta); // add if pass lazy sanity hint
                }
            } catch (RemoteException e) {
                if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
                e.printStackTrace();
            }
        }
        sortFileList(SORT_DATE_DSC);
    }

    private void populateUi() throws RemoteException {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "populateUi");

        setContentView(R.layout.activity_recorded_calls);

        // setup toolbar/actionbar
        mtoolBar = findViewById(R.id.my_toolbar);
        if (mtoolBar != null) {
            mtoolBar.setBackground(new ColorDrawable(sipService.rsGetInt(Prefs.KEY_UI_COLOR_PRIMARY, Prefs.DEFAULT_UI_COLOR_PRIMARY)));
            mtoolBar.setTitleTextColor(Color.WHITE);
            mtoolBar.setTitle(getString("recorded_calls", "Recorded Calls"));
            mtoolBar.setOnMenuItemClickListener(this);
            final Menu menu = mtoolBar.getMenu();
            menu.add(0, MENU_ID_REFRESH, 0, "Refresh");
            MenuItem item = menu.findItem(MENU_ID_REFRESH);
            item.setIcon(R.drawable.ic_refresh_white_24dp);
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
//            menu.add(0, MENU_ID_DELETE_ALL, 0, "Delete All");
//            item = menu.findItem(MENU_ID_DELETE_ALL);
//            item.setIcon(R.drawable.ic_delete_wht_24dp);
//            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }

        loadFileList();

        rvList = findViewById(R.id.rvFileList);
        rvList.setLayoutManager(new LinearLayoutManager(this));
        rvList.setAdapter(new CallRecordAdaptor());

        final ItemTouchHelper.SimpleCallback simpleCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                synchronized (llRecCallFiles) {
                    final int adapterPosition = viewHolder.getAdapterPosition();
                    final CallRecMeta callRecMeta = llRecCallFiles.get(adapterPosition);
                    llRecCallFiles.remove(adapterPosition);
                    if (DEBUG) gLog.l(Logger.TAG, Logger.lvVerbose, "onSwiped " + callRecMeta.fName);
                    String snackBarMessage = "Recording Deleted";
                    String snackBarMessageUndo = "UNDO";
                    try {
                        snackBarMessage = sipService.getLocalString("recording_deleted", "Recording Deleted");
                        snackBarMessageUndo = sipService.getLocalString("_snackbar_action_undo", "UNDO");
                    } catch (RemoteException e) {
                        if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
                    }
                    final Snackbar snackbar = Snackbar.make(rvList, snackBarMessage, Snackbar.LENGTH_INDEFINITE)
                            .setAction(snackBarMessageUndo, new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "Reinserting " + callRecMeta.fName);
                                    synchronized (llRecCallFiles) {
                                        llRecCallFiles.add(adapterPosition, callRecMeta);
                                    }
                                    rvList.getAdapter().notifyItemInserted(adapterPosition);
                                    rvList.scrollToPosition(adapterPosition); // show user entry has been restored
                                }
                            })
                            .addCallback(new BaseTransientBottomBar.BaseCallback<Snackbar>() {
                                @Override
                                public void onDismissed(Snackbar transientBottomBar, int event) {
                                    super.onDismissed(transientBottomBar, event);
                                    if (event != BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_ACTION) {
                                        // snackbar was not cancelled, continue with delete
                                        if (DEBUG) gLog.l(TAG, Logger.lvDebug, "Delete " + callRecMeta.fName);
                                        final Thread thread = new Thread(new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    String fPath = sipService.getAppExtStoragePath() + '/' + callRecMeta.fName;
                                                    File del = new File(fPath);
                                                    if (!del.exists()) {
                                                        if (DEBUG)
                                                            gLog.l(TAG, Logger.lvDebug, "Delete not found " + fPath);
                                                        return;
                                                    }
                                                    if (!del.isFile()) {
                                                        if (DEBUG)
                                                            gLog.l(TAG, Logger.lvDebug, "Delete not file " + fPath);
                                                        return;
                                                    }
                                                    if (!del.delete()) {
                                                        if (DEBUG) gLog.l(TAG, Logger.lvDebug, "Deleted " + fPath);
                                                        return;
                                                    }
                                                } catch (RemoteException e) {
                                                    if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
                                                }
                                            }
                                        });
                                        thread.setPriority(Thread.MIN_PRIORITY);
                                        thread.start();
                                    }
                                }
                            });
                    synchronized (llSnackBars) {
                        llSnackBars.add(snackbar);
                    }
                    snackbar.show();
                }
                rvList.getAdapter().notifyItemRemoved(viewHolder.getAdapterPosition());
            }
        };

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(simpleCallback);
        itemTouchHelper.attachToRecyclerView(rvList);


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

    /**
     * Commit user preferences. Invalid parameters are silently ignored.
     *
     * @throws RemoteException
     */
    private void save() throws RemoteException {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "save");

        sipService.rsCommit(true);
        sipService.accountChangesComitted();
        finish();
    }

    private void snackbarFlush() {
        for (Snackbar snackbar : llSnackBars) {
            if (snackbar.isShownOrQueued()) snackbar.dismiss();
        }
        llSnackBars.clear();
    }

    private void sortFileList(int sortBy) {
        synchronized (llRecCallFiles) {
            switch (sortBy) {
                case SORT_CONTACT_ASC:
                    sortFileList(SORT_DATE_DSC); // presort most recent
                    Collections.sort(llRecCallFiles, new Comparator<CallRecMeta>() {
                        @Override
                        public int compare(CallRecMeta o1, CallRecMeta o2) {
                            return Collator.getInstance(Locale.ENGLISH).compare(o1.getContact(), o2.getContact());
                        }
                    });
                    break;
                case SORT_CONTACT_DSC:
                    sortFileList(SORT_DATE_DSC); // presort most recent
                    Collections.sort(llRecCallFiles, new Comparator<CallRecMeta>() {
                        @Override
                        public int compare(CallRecMeta o1, CallRecMeta o2) {
                            return Collator.getInstance(Locale.ENGLISH).compare(o2.getContact(), o1.getContact());
                        }
                    });
                    break;
                case SORT_DIR_ASC:
                    sortFileList(SORT_DATE_DSC); // presort most recent
                    Collections.sort(llRecCallFiles, new Comparator<CallRecMeta>() {
                        @Override
                        public int compare(CallRecMeta o1, CallRecMeta o2) {
                            return Collator.getInstance(Locale.ENGLISH).compare(o1.getDir(), o2.getDir());
                        }
                    });
                    break;
                case SORT_DIR_DSC:
                    sortFileList(SORT_DATE_DSC); // presort most recent
                    Collections.sort(llRecCallFiles, new Comparator<CallRecMeta>() {
                        @Override
                        public int compare(CallRecMeta o1, CallRecMeta o2) {
                            return Collator.getInstance(Locale.ENGLISH).compare(o2.getDir(), o1.getDir());
                        }
                    });
                    break;
                case SORT_DATE_ASC:
                    Collections.sort(llRecCallFiles, new Comparator<CallRecMeta>() {
                        @Override
                        public int compare(CallRecMeta o1, CallRecMeta o2) {
                            return Collator.getInstance(Locale.ENGLISH).compare(o1.fName, o2.fName);
                        }
                    });
                    break;
                case SORT_DATE_DSC:
                default:
                    Collections.sort(llRecCallFiles, new Comparator<CallRecMeta>() {
                        @Override
                        public int compare(CallRecMeta o1, CallRecMeta o2) {
                            return Collator.getInstance(Locale.ENGLISH).compare(o2.fName, o1.fName);
                        }
                    });
            }
        }
    }

    class CallRecordAdaptor extends RecyclerView.Adapter {

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

            final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.call_rec_list_item, parent, false);
            view.findViewById(R.id.call_log_day_group_label).setVisibility(View.GONE);
            view.setClickable(true);
            return new CallRecordViewHolder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            CallRecordViewHolder callRecordViewHolder = (CallRecordViewHolder) holder;
            final CallRecMeta callRecMeta;
            synchronized (llRecCallFiles) {
                callRecMeta = llRecCallFiles.get(position);
            }

            final String contact = callRecMeta.getContact();
            if (contact != null && !contact.isEmpty()) {
                final Bundle bundle = ContactProvider.getContactBundleByNumber(getApplicationContext(), callRecMeta.getContact());// lookup extension in user contacts

                if (bundle != null) {
                    final int contactId = bundle.getInt(ContactsContract.PhoneLookup._ID);
                    final String contactName = ContactProvider.getContactName(getApplicationContext(), contactId);
                    if (contactName != null || !contactName.isEmpty()) callRecordViewHolder.name.setText(contactName);
                    else callRecordViewHolder.name.setText(llRecCallFiles.get(position).getContact());
                } else callRecordViewHolder.name.setText(llRecCallFiles.get(position).getContact());

                callRecordViewHolder.callTypeIconsView.clear();
                switch (callRecMeta.getDir()) {
                    case "TO":
                        callRecordViewHolder.callTypeIconsView.add(CallLog.Calls.OUTGOING_TYPE);
                        break;
                    case "FROM":
                        callRecordViewHolder.callTypeIconsView.add(CallLog.Calls.INCOMING_TYPE);
                        break;
                }

                callRecordViewHolder.call_location_and_date.setText(getCallDate(callRecMeta.getDate(), callRecMeta.getTime()));

                callRecordViewHolder.quickContactBadge.setOverlay(null);
                callRecordViewHolder.quickContactBadge.setImageToDefault();
                if (bundle != null) {
                    final int contactId = bundle.getInt(ContactsContract.PhoneLookup._ID);
                    final String lookupKey = bundle.getString(ContactsContract.PhoneLookup.LOOKUP_KEY);
                    Uri contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId);

                    Bitmap contactPhotoThumb = ContactProvider.getContactPhotoThumb(getApplicationContext(), contactId);
                    if (contactPhotoThumb != null) {
                        Bitmap roundedBitmap = BitmapUtil.getRoundedBitmap(contactPhotoThumb, iconWidth, iconHeight);
                        callRecordViewHolder.quickContactBadge.setImageBitmap(roundedBitmap);
                    } else {
                        final String contactName = ContactProvider.getContactName(getApplicationContext(), contactId);
                        ContactPhotoManager.DefaultImageRequest request = new ContactPhotoManager.DefaultImageRequest(contactName, lookupKey, true /* isCircular */);
                        mContactPhotoManager.loadThumbnail(callRecordViewHolder.quickContactBadge, 0, false /* darkTheme */, true /* isCircular */, request);
                    }
                    callRecordViewHolder.quickContactBadge.assignContactUri(contactUri);
                }

                callRecordViewHolder.primary_action_view.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (DEBUG) gLog.l(TAG, Logger.lvDebug, callRecMeta.fName);

//                        try {
//                            Intent intent = new Intent();
//                            intent.setAction(android.content.Intent.ACTION_VIEW);
//                            File file = new File(sipService.getAppExtStoragePath() + '/' + callRecMeta.fName);
//                            intent.setDataAndType(Uri.fromFile(file), "audio/wav");
//                            startActivity(intent);
//                        } catch (RemoteException e) {
//                            if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
//                        }

                        dialogWavPlayer = new DialogWavPlayer(callRecMeta.fName);
                        try {
                            dialogWavPlayer.buildAndShow(getWindow().getContext(), sipService);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }


                    }
                });
            } else { // invalid file
                String errorString = "error";
                try {
                    errorString = sipService.getLocalString("error", "error");
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                callRecordViewHolder.name.setText(errorString);
                callRecordViewHolder.callTypeIconsView.clear();
                callRecordViewHolder.call_location_and_date.setText(null);
                callRecordViewHolder.quickContactBadge.setOverlay(null);
                callRecordViewHolder.quickContactBadge.setImageToDefault();
                callRecordViewHolder.primary_action_view.setOnClickListener(null);
            }


        }

        @Override
        public int getItemCount() {
            synchronized (llRecCallFiles) {
                return llRecCallFiles.size();
            }
        }
    }

    class CallRecordViewHolder extends RecyclerView.ViewHolder {
        View primary_action_view;
        QuickContactBadge quickContactBadge;
        TextView name;
        CallTypeIconsView callTypeIconsView;
        TextView call_location_and_date;


        public CallRecordViewHolder(View itemView) {
            super(itemView);
            primary_action_view = itemView.findViewById(R.id.primary_action_view);
            quickContactBadge = (QuickContactBadge) itemView.findViewById(R.id.quick_contact_photo);
            name = (TextView) itemView.findViewById(R.id.name);
            callTypeIconsView = (CallTypeIconsView) itemView.findViewById(R.id.call_type_icons);
            call_location_and_date = (TextView) itemView.findViewById(R.id.call_location_and_date);
        }
    }

    class CallRecMeta {
        String fName;
        String[] split;
        boolean isValid = true; // sanity hint

        CallRecMeta(String fileName) {
            this.fName = fileName;
            split = fileName.split("_");
            sanityCheckWeak();
        }

        String getContact() {
            if (!isValid) return null;
            try {
                return split[3].substring(0, split[3].lastIndexOf('.'));
            } catch (IndexOutOfBoundsException e) {
                if (DEBUG) e.printStackTrace();
                return null;
            }
        }

        String getDate() {
            if (!isValid) return null;
            return split[0];
        }

        String getDir() {
            if (!isValid) return null;

            return split[2];
        }

        String getTime() {
            if (!isValid) return null;
            return split[1];
        }

        private void sanityCheckWeak() {
            if (split.length <= 1 || split.length > 4) {
                isValid = false;
                if (DEBUG) gLog.l(TAG, Logger.lvDebug, "Invalid FileName " + fName);
                return;
            }
            if (getContact() == null || getContact().isEmpty()) {
                isValid = false;
                return;
            }
            if (getDate() == null || getDate().isEmpty()) {
                isValid = false;
                return;
            }
            if (getDir() == null || getDir().isEmpty()) {
                isValid = false;
                return;
            }
            if (getTime() == null || getTime().isEmpty()) {
                isValid = false;
                return;
            }
        }

    }

    class EventHandler extends IRemoteSipServiceEvents.Stub {

        @Override
        public void onCallMuteStateChanged(int pjsipCallId) throws RemoteException {

        }

        @Override
        public void onCallRecordStateChanged(int pjsipCallId) throws RemoteException {

        }

        @Override
        public void onCallStateChanged(int pjsipCallId) throws RemoteException {

        }

        @Override
        public void onStackStateChanged(int stackState) throws RemoteException {

        }

        @Override
        public void onExit() throws RemoteException {

        }

        @Override
        public void onSettingsChanged() throws RemoteException {

        }

        @Override
        public void showMessage(int iAnType, Bundle bundle) throws RemoteException {

        }

        @Override
        public void clearMessageByType(int iAnType) throws RemoteException {

        }

        @Override
        public void clearMessageBySmid(int smid) throws RemoteException {

        }

        @Override
        public void onLicenseStateUpdated(Bundle licenseData) throws RemoteException {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "onLicenseStateUpdated");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        refreshLicenseState(sipService);
                        populateUi();
                    } catch (RemoteException e) {
                        if (DEBUG) e.printStackTrace();
                    }
                }
            });
        }
    }

}
