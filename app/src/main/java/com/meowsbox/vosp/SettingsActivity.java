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
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.annotation.ColorInt;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.view.ContextThemeWrapper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.meowsbox.vosp.common.Logger;
import com.meowsbox.vosp.service.Prefs;
import com.meowsbox.vosp.service.SipEndpoint;
import com.meowsbox.vosp.service.licensing.LicensingManager;
import com.meowsbox.vosp.widget.CompRowColor;
import com.meowsbox.vosp.widget.CompRowEdit;
import com.meowsbox.vosp.widget.CompRowEdit2IntRange;
import com.meowsbox.vosp.widget.CompRowEditIbIntRange;
import com.meowsbox.vosp.widget.CompRowEditIntRange;
import com.meowsbox.vosp.widget.CompRowHeader;
import com.meowsbox.vosp.widget.CompRowSw;
import com.meowsbox.vosp.widget.DialogHelpAltIcons;
import com.meowsbox.vosp.widget.DialogPremiumRequiredAltIcons;
import com.meowsbox.vosp.widget.DialogPremiumRequiredColorPicker;
import com.meowsbox.vosp.widget.DialogPremiumUpgrade;
import com.meowsbox.vosp.widget.IcompRowChangeListener;
import com.thebluealliance.spectrum.SpectrumDialog;

public class SettingsActivity extends Activity implements ServiceBindingController.ServiceConnectionEvents, IcompRowChangeListener {
    public final static boolean DEBUG = DialerApplication.DEBUG;
    public final static boolean DEV = DialerApplication.DEV;
    static Logger gLog;
    public final String TAG = this.getClass().getName();
    SpectrumDialog spectrumDialog;
    DialogPremiumRequiredColorPicker dialogPremiumRequiredColorPicker;
    DialogPremiumRequiredAltIcons dialogPremiumRequiredAltIcons;
    private CompRowSw vOutgoingAll;
    private CompRowEdit vUser;
    private CompRowEdit vFriendly;
    private CompRowEdit vSecret;
    private CompRowEdit vServer, vServerOutProxy;
    //    private CompRowEdit vPort, vRtpBegin, vRtpEnd;
    private CompRowEditIntRange vPort, vPortOutProxy;
    private CompRowEdit2IntRange vRtpRange;
    private CompRowSw vStun, vIce;
    private CompRowEdit vStunServer;
    private CompRowSw vTcp;
    private CompRowSw vOnBoot;
    private CompRowSw vDozeWorkAround, vDozeDisableRoot;
    private CompRowSw vWifiLock;
    private CompRowSw vVibe;
    private CompRowSw vAltLaunchers;
    private CompRowSw vUiColorNotifDark;
    private CompRowEdit vRegExpire;
    private CompRowEdit vKaTcpMobile;
    private CompRowEdit vKaTcpWifi;
    private CompRowEditIbIntRange vMediaAudioPreAmpRx, vMediaAudioPreAmpTx, vMediaQuality;
    private CompRowColor vUiColorPrimary;
    private IRemoteSipService sipService = null;
    private ServiceBindingController mServiceController = null;
    private boolean isPrem = false;
    private AlertDialog dialogPremiumRequired;
    private View.OnClickListener onClickListenerPremium;
    private boolean hasMadeChanges = false;
    private EventHandler sipServiceEventHandler = new EventHandler();

    @Override
    public void onChanged() {
        hasMadeChanges = true;
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
                    gLog.l(TAG, Logger.lvDebug, e);
                    if (DEBUG) e.printStackTrace();
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
        gLog = ((DialerApplication) getApplication()).getLoggerInstanceShared();
        super.onCreate(savedInstanceState);
        final ActionBar actionBar = getActionBar();
        if (actionBar != null) actionBar.hide();
        mServiceController = new ServiceBindingController(gLog, this, this);
//        if (DEBUG) gLog.lEnd();
    }

    @Override
    protected void onResume() {
        if (mServiceController != null) mServiceController.bindToService();
        super.onResume();
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
        if (hasMadeChanges) {
            final ContextThemeWrapper contextWrapper = new ContextThemeWrapper(this, R.style.Theme_AppCompat_Dialog_Alert);
            AlertDialog.Builder builder = new AlertDialog.Builder(contextWrapper);
            builder.setTitle(getString("changes_made", "Changes Made"))
                    .setMessage(getString("save_changes_q", "Save Changes ?"))
                    .setCancelable(true)
                    .setPositiveButton(getString("yes", "Yes"), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                save();
                            } catch (RemoteException e) {
                                if (DEBUG) e.printStackTrace();
                            }
                            dialog.dismiss();
                            finish();
                        }
                    })
                    .setNegativeButton(getString("no", "No"), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            finish();
                        }
                    });
            builder.create().show();
        } else finish();
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (item.getItemId()) {
            case 22:
                try {
                    item.setEnabled(false);
                    save();
                } catch (RemoteException e) {
                    gLog.l(TAG, Logger.lvDebug, e);
                    if (DEBUG) e.printStackTrace();
                }
                return true;
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        menu.add(0, 22, 0, "Save");
        MenuItem item = menu.getItem(0);
        item.setIcon(R.drawable.ic_save_white_24dp);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;

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

    private void populateUi() throws RemoteException {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "populateUi");
        setContentView(R.layout.activity_settings);

        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE);
            actionBar.setTitle(getString("settings", "Settings"));
            actionBar.show();

            // move layout below actionbar
            View llSettings = findViewById(R.id.llSettings);
            llSettings.setTop(actionBar.getHeight());
        }

        Drawable dHelp = getResources().getDrawable(R.drawable.ic_help_black_24dp);
        final int helpTint = getResources().getColor(R.color.md_light_blue_500);
        dHelp.setTint(helpTint);
        final ContextThemeWrapper contextWrapper = new ContextThemeWrapper(this, R.style.Theme_AppCompat_Light_Dialog_Alert);

        if (!isPrem) {
            // create premium dialog
            AlertDialog.Builder builder = new AlertDialog.Builder(contextWrapper);
            builder.setTitle(getString("premium_feature", "Premium Feature"))
                    .setMessage(getString("help_premium_feature", "Upgrade to unlock this and other features."))
                    .setCancelable(true)
                    .setPositiveButton(getString("upgrade", "Upgrade"), new DialogInterface.OnClickListener() {
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
                    .setNegativeButton(getString("close", "Close"), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            dialogPremiumRequired = builder.create();
            // create premium clickListener
            onClickListenerPremium = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialogPremiumRequired.show();
                }
            };

            dialogPremiumRequiredColorPicker = new DialogPremiumRequiredColorPicker();
            dialogPremiumRequiredColorPicker.build(contextWrapper, sipService);

            dialogPremiumRequiredAltIcons = new DialogPremiumRequiredAltIcons();
            dialogPremiumRequiredAltIcons.build(contextWrapper, sipService);

        }


        CompRowHeader hGeneral = (CompRowHeader) findViewById(R.id.ihGeneralOptions);
        hGeneral.setValue(getString("header_general_options", "General Options"));

        vOutgoingAll = (CompRowSw) findViewById(R.id.ilSwOutgoingAll);
        vOutgoingAll.setName(getString("handle_all_outgoing_calls", "Handle all outgoing calls"));
        vOutgoingAll.setValue(sipService.rsGetBoolean(Prefs.KEY_HANDLE_OUTGOING_ALL, true));
        vOutgoingAll.setHelpDrawable(dHelp)
                .setChangeListener(this)
                .setHelpVisibility(View.VISIBLE)
                .setHelpOnClick(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(contextWrapper);
                        builder.setTitle(getString("handle_all_outgoing_calls", "Handle all outgoing calls"))
                                .setMessage(getString("help_handle_all_outgoing_calls", "Automatically route all normal outgoing calls from anywhere through this app."))
                                .setCancelable(true)
                                .setPositiveButton(getString("close", "Close"), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                });

        CompRowHeader hServerConfig = (CompRowHeader) findViewById(R.id.ihServerConfig);
        hServerConfig.setValue(getString("header_server_config", "Server Configuration"));

        vFriendly = (CompRowEdit) findViewById(R.id.ilFriendlyName);
        vFriendly.setName(getString("account_name", "Account Name"));
        vFriendly.setValue(sipService.rsGetString(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_NAME), "default"))
                .setChangeListener(this);

        vUser = (CompRowEdit) findViewById(R.id.ilUsername);
        vUser.setName(getString("user_name", "User Name"));
        vUser.setValue(sipService.rsGetString(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_USER), ""))
                .setChangeListener(this);

        vSecret = (CompRowEdit) findViewById(R.id.ilSecret);
        vSecret.setSecret();
        vSecret.setName(getString("secret", "Secret"));
        vSecret.setValue(sipService.rsGetString(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_SECRET), ""))
                .setChangeListener(this);


        vServer = (CompRowEdit) findViewById(R.id.ilServer);
        vServer.setName(getString("server", "Server"));
        vServer.setValue(sipService.rsGetString(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_SERVER), ""))
                .setChangeListener(this);


        vPort = (CompRowEditIntRange) findViewById(R.id.IlServerPort);
        vPort.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_NORMAL);
        vPort.setName(getString("port", "Port"));
        vPort.setValue(String.valueOf(sipService.rsGetInt(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_PORT), 5060)));
        vPort.setBoundRange(1, 65535).setSnapDefaults(5060).setSnapToRange(true);
        vPort.setHelpDrawable(dHelp)
                .setChangeListener(this)
                .setHelpVisibility(View.VISIBLE)
                .setHelpOnClick(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(contextWrapper);
                        builder.setTitle(getString("port", "Port"))
                                .setMessage(getString("help_port", "The port the SIP server is listening for client connections. Check with your service provider for the correct value. The valid range is 1-65535. The default is 5060."))
                                .setCancelable(true)
                                .setPositiveButton(getString("close", "Close"), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                });

        vRtpRange = (CompRowEdit2IntRange) findViewById(R.id.IlRtpPortRange);
        vRtpRange.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_NORMAL);
        vRtpRange.setName(getString("rtp_port_range", "RTP Port Range"));
        int rtpLower = sipService.rsGetInt(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_MEDIA_PORT_BEGIN), 5000);
        int rtpUpper = sipService.rsGetInt(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_MEDIA_PORT_END), 31000);
        vRtpRange.setValue(String.valueOf(rtpLower)).setValue2(String.valueOf(rtpUpper));
        vRtpRange.setBoundRange(1, 65534).setSnapDefaults(5000, 31000).setSnapToRange(true);
        vRtpRange.setHelpDrawable(dHelp)
                .setChangeListener(this)
                .setHelpVisibility(View.VISIBLE)
                .setHelpOnClick(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(contextWrapper);
                        builder.setTitle(getString("rtp_port_range", "RTP Port Range"))
                                .setMessage(getString("help_rtp_port_begin", "This is the inclusive bound of the RTP port range. The maximum valid range is 1-65534. Ranges that do not match your service provider and excessively narrow ranges may cause call and audio problems. Typical ranges include 1024-65534, 5000-31000, or 10000-20000"))
                                .setCancelable(true)
                                .setPositiveButton(getString("close", "Close"), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                });

        CompRowHeader hAppOption = (CompRowHeader) findViewById(R.id.ihAdvancedOpt);
        hAppOption.setValue(getString("header_advanced_opt", "Advanced Options"));

        vStun = (CompRowSw) findViewById(R.id.ilSwStun);
        vStun.setName(getString("use_stun", "Use STUN"));
        vStun.setValue(sipService.rsGetBoolean(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_USE_STUN), true));
        vStun.setHelpDrawable(dHelp)
                .setChangeListener(this)
                .setHelpVisibility(View.VISIBLE)
                .setHelpOnClick(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(contextWrapper);
                        builder.setTitle(getString("use_stun", "Use STUN"))
                                .setMessage(getString("help_use_stun", "Use Session Traversal Utilities for NAT (STUN) server to traverse through most network firewalls or NAT. The default is enabled."))
                                .setCancelable(true)
                                .setPositiveButton(getString("close", "Close"), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                });

        vServerOutProxy = (CompRowEdit) findViewById(R.id.ilServerOutProxy);
        vServerOutProxy.setName(getString("server_out_proxy", "Outbound Proxy"));
        vServerOutProxy.setValue(sipService.rsGetString(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_SERVER_OUT_PROXY), ""))
                .setChangeListener(this);
        vServerOutProxy.setHelpDrawable(dHelp)
                .setChangeListener(this)
                .setHelpVisibility(View.VISIBLE)
                .setHelpOnClick(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(contextWrapper);
                        builder.setTitle(getString("use_out_proxy", "Use Outbound Proxy"))
                                .setMessage(getString("help_use_out_proxy", "Specify an outbound sip proxy instead of the registration server for outbound call routing. The default is blank."))
                                .setCancelable(true)
                                .setPositiveButton(getString("close", "Close"), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                });

        vPortOutProxy = (CompRowEditIntRange) findViewById(R.id.ilServerOutProxyPort);
        vPortOutProxy.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_NORMAL);
        vPortOutProxy.setName(getString("port_out_proxy", "Outbound Proxy Port"));
        vPortOutProxy.setValue(String.valueOf(sipService.rsGetInt(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_PORT_OUT_PROXY), 5060)));
        vPortOutProxy.setBoundRange(1, 65535).setSnapDefaults(5060).setSnapToRange(true);
        vPortOutProxy.setHelpDrawable(dHelp)
                .setChangeListener(this)
                .setHelpVisibility(View.VISIBLE)
                .setHelpOnClick(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(contextWrapper);
                        builder.setTitle(getString("port_out_proxy", "Outbound Proxy Port"))
                                .setMessage(getString("help_port_out_proxy", "The port the outbound proxy server is listening for client connections. Check with your service provider for the correct value. The valid range is 1-65535. The default is 5060."))
                                .setCancelable(true)
                                .setPositiveButton(getString("close", "Close"), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                });

        vIce = (CompRowSw) findViewById(R.id.ilSwIce);
        vIce.setName(getString("use_ice", "Use ICE"));
        vIce.setValue(sipService.rsGetBoolean(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_USE_ICE), false));
        vIce.setIsPremium(true).setRowEnabled(isPrem);
        vIce.setHelpDrawable(dHelp)
                .setChangeListener(this)
                .setHelpVisibility(View.VISIBLE)
                .setHelpOnClick(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(contextWrapper);
                        builder.setTitle(getString("use_ice", "Use ICE"))
                                .setMessage(getString("help_use_ice", "Use Interactive Connectivity Establishment (ICE) to traverse through difficult network firewalls or NAT. ICE may increase connection set up time. The default is disabled."))
                                .setCancelable(true)
                                .setPositiveButton(getString("close", "Close"), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                });
        if (!isPrem) vIce.setOnClickListener(onClickListenerPremium);


        vTcp = (CompRowSw) findViewById(R.id.ilSwTcp);
        vTcp.setName(getString("use_tcp", "Use TCP"));
        vTcp.setValue(sipService.rsGetBoolean(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_TCP), false));
        vTcp.setIsPremium(true).setRowEnabled(isPrem);
        if (!isPrem) vTcp.setOnClickListener(onClickListenerPremium);
        vTcp.setHelpDrawable(dHelp)
                .setChangeListener(this)
                .setHelpVisibility(View.VISIBLE)
                .setHelpOnClick(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(contextWrapper);
                        builder.setTitle(getString("use_tcp", "Use TCP"))
                                .setMessage(getString("help_use_tcp", "Use TCP instead of UDP to connect to SIP server. May significantly reduce battery usage, however not all SIP server support TCP. The default is disabled."))
                                .setCancelable(true)
                                .setPositiveButton(getString("close", "Close"), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                });

        vOnBoot = (CompRowSw) findViewById(R.id.ilOnBoot);
        vOnBoot.setName(getString("start_on_boot", "Start On Boot"));
        vOnBoot.setValue(sipService.rsGetBoolean(Prefs.KEY_ONBOOT_ENABLE, true));
        vOnBoot.setHelpDrawable(dHelp)
                .setChangeListener(this)
                .setHelpVisibility(View.VISIBLE)
                .setHelpOnClick(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(contextWrapper);
                        builder.setTitle(getString("start_on_boot", "Start On Boot"))
                                .setMessage(getString("help_start_on_boot", "Automatically start the call handling background service when the device is turned on. The default is enabled."))
                                .setCancelable(true)
                                .setPositiveButton(getString("close", "Close"), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                });

        vDozeWorkAround = (CompRowSw) findViewById(R.id.ilDozeWorkaround);
        vDozeWorkAround.setName(getString("doze_workaround", "Doze Workaround"));
        vDozeWorkAround.setValue(sipService.rsGetBoolean(Prefs.KEY_DOZE_WORKAROUND_ENABLE, false))
                .setChangeListener(this);
        vDozeWorkAround.setVisibility(Build.VERSION.SDK_INT >= 23 ? View.VISIBLE : View.GONE);
        vDozeWorkAround.setIsPremium(true).setRowEnabled(isPrem);
        if (!isPrem) vDozeWorkAround.setOnClickListener(onClickListenerPremium);

        vDozeWorkAround.setHelpDrawable(dHelp)
                .setHelpVisibility(View.VISIBLE)
                .setHelpOnClick(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(contextWrapper);
                        builder.setTitle(getString("doze_workaround", "Doze Workaround"))
                                .setMessage(getString("help_doze_workaround", "The system Doze feature on Android 6 (Marshmallow) and newer may block the app from receiving calls when your device is left undisturbed for a period of time. Enable this setting to prevent missing calls while the phone is dozing. The default is enabled."))
                                .setCancelable(true)
                                .setPositiveButton(getString("close", "Close"), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                });

        vDozeDisableRoot = (CompRowSw) findViewById(R.id.ilDozeDisableRoot);
        vDozeDisableRoot.setName(getString("doze_disable_with_root", "Doze Disable (Root)"));
        vDozeDisableRoot.setValue(sipService.rsGetBoolean(Prefs.KEY_DOZE_DISABLE_SU, false))
                .setChangeListener(this);
        vDozeDisableRoot.setIsPremium(true).setRowEnabled(isPrem);
        if (!isPrem) vDozeDisableRoot.setOnClickListener(onClickListenerPremium);
        if (Build.VERSION.SDK_INT < 23) vDozeDisableRoot.setVisibility(View.GONE);
        vDozeDisableRoot.setHelpDrawable(dHelp)
                .setHelpVisibility(View.VISIBLE)
                .setHelpOnClick(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            final String url_help_doze_disable = sipService.getLocalString("url_help_doze_disable", "file:///android_asset/help_doze_disable-en/index.html");
                            AlertDialog.Builder builder = new AlertDialog.Builder(contextWrapper);
                            WebView wv = new WebView(v.getContext());
                            wv.loadUrl(url_help_doze_disable);
                            wv.setWebViewClient(new WebViewClient() {
                                @Override
                                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                                    view.loadUrl(url);
                                    return true;
                                }
                            });
                            builder.setPositiveButton(getString("close", "Close"), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                            builder.setNegativeButton(getString("view_in_browser", "View In Browser"), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    String url = "http://apps.meowsbox.com/vosp/help_doze_disable-en/";
                                    Intent i = new Intent(Intent.ACTION_VIEW);
                                    i.setData(Uri.parse(url));
                                    startActivity(i);
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

        vWifiLock = (CompRowSw) findViewById(R.id.ilWifiLock);
        vWifiLock.setName(getString("wifi_lock", "Wifi Lock"));
        vWifiLock.setValue(sipService.rsGetBoolean(Prefs.KEY_WIFI_LOCK_ENABLE, false))
                .setChangeListener(this);
        vWifiLock.setIsPremium(true).setRowEnabled(isPrem);
        if (!isPrem) vWifiLock.setOnClickListener(onClickListenerPremium);
        vWifiLock.setHelpDrawable(dHelp)
                .setHelpVisibility(View.VISIBLE)
                .setHelpOnClick(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(contextWrapper);
                        builder.setTitle(getString("wifi_lock", "Wifi Lock"))
                                .setMessage(getString("help_wifi_lock", "Force the WiFi radio to never go to sleep and to remain in high-performance mode. May prevent audio jitter caused by excessive radio power saving. The default is disabled."))
                                .setCancelable(true)
                                .setPositiveButton(getString("close", "Close"), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                });

        vStunServer = (CompRowEdit) findViewById(R.id.ilStunServer);
        vStunServer.setName(getString("stun_server", "STUN Server"));
        vStunServer.setValue(sipService.rsGetString(Prefs.getAccountKey(0, Prefs.KEY_STUN_SERVER), "stun.meowsbox.com"))
                .setChangeListener(this);
        vStunServer.setIsPremium(true).setRowEnabled(isPrem);
        if (!isPrem) vStunServer.setOnClickListener(onClickListenerPremium);

        vStunServer.setHelpDrawable(dHelp)
                .setHelpVisibility(View.VISIBLE)
                .setHelpOnClick(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(contextWrapper);
                        builder.setTitle(getString("stun_server", "STUN Server"))
                                .setMessage(getString("help_stun_server", "The STUN server to use when the setting to use a STUN server is enabled."))
                                .setCancelable(true)
                                .setPositiveButton(getString("close", "Close"), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                });

        vRegExpire = (CompRowEdit) findViewById(R.id.ilRegExpire);
        vRegExpire.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_NORMAL);
        vRegExpire.setName(getString("reg_expire", "Registration Expiry"));
        vRegExpire.setValue(String.valueOf(sipService.rsGetInt(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_REG_EXPIRE), 300)))
                .setChangeListener(this);
        vRegExpire.setHelpDrawable(dHelp)
                .setHelpVisibility(View.VISIBLE)
                .setHelpOnClick(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(contextWrapper);
                        builder.setTitle(getString("reg_expire", "Registration Expiry"))
                                .setMessage(getString("help_reg_expire", "The number of seconds between renewal of your SIP registration. Higher values reduce power consumption, lower numbers may improve reachability on poor networks. Refer to your SIP service provider for the correct value. The default is 300."))
                                .setCancelable(true)
                                .setPositiveButton(getString("close", "Close"), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                });

        vKaTcpMobile = (CompRowEdit) findViewById(R.id.ilKaTcpMobile);
        vKaTcpMobile.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_NORMAL);
        vKaTcpMobile.setName(getString("keepalive_tcp_mobile", "Keep Alive TCP Mobile"));
        vKaTcpMobile.setValue(String.valueOf(sipService.rsGetInt(Prefs.KEY_KA_TCP_MOBILE, SipEndpoint.KEEP_ALIVE_TCP_MOBILE)))
                .setChangeListener(this);
        vKaTcpMobile.setIsPremium(true).setRowEnabled(isPrem);
        if (!isPrem) vKaTcpMobile.setOnClickListener(onClickListenerPremium);
        vKaTcpMobile.setHelpDrawable(dHelp)
                .setHelpVisibility(View.VISIBLE)
                .setHelpOnClick(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(contextWrapper);
                        builder.setTitle(getString("keepalive_tcp_mobile", "Keep Alive TCP Mobile"))
                                .setMessage(getString("help_keepalive_tcp_mobile", "The number of seconds between messages sent to the SIP server when using TCP over cellular data to maintain network connections alive and improve reachability. Higher values reduce power consumption, lower numbers may improve reachability on poor networks."))
                                .setCancelable(true)
                                .setPositiveButton(getString("close", "Close"), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                });

        vKaTcpWifi = (CompRowEdit) findViewById(R.id.ilKaTcpWifi);
        vKaTcpWifi.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_NORMAL);
        vKaTcpWifi.setName(getString("keepalive_tcp_wifi", "Keep Alive TCP Wifi"));
        vKaTcpWifi.setValue(String.valueOf(sipService.rsGetInt(Prefs.KEY_KA_TCP_WIFI, SipEndpoint.KEEP_ALIVE_TCP_WIFI)))
                .setChangeListener(this);
        vKaTcpWifi.setIsPremium(true).setRowEnabled(isPrem);
        if (!isPrem) vKaTcpWifi.setOnClickListener(onClickListenerPremium);
        vKaTcpWifi.setHelpDrawable(dHelp)
                .setHelpVisibility(View.VISIBLE)
                .setHelpOnClick(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(contextWrapper);
                        builder.setTitle(getString("keepalive_tcp_wifi", "Keep Alive TCP Wifi"))
                                .setMessage(getString("help_keepalive_tcp", "The number of seconds between messages sent to the SIP server when using TCP over WiFi data to maintain network connections alive and improve reachability. Higher values reduce power consumption, lower numbers may improve reachability on poor networks."))
                                .setCancelable(true)
                                .setPositiveButton(getString("close", "Close"), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                });

        vUiColorPrimary = (CompRowColor) findViewById(R.id.ilUiColorPrimary);
        vUiColorPrimary.setName(getString("primary_color", "Primary Color"));
        vUiColorPrimary.setValue(sipService.rsGetInt(Prefs.KEY_UI_COLOR_PRIMARY, Prefs.DEFAULT_UI_COLOR_PRIMARY));
        vUiColorPrimary.setIsPremium(true).setRowEnabled(isPrem);
        spectrumDialog = new SpectrumDialog.Builder(this, R.style.Theme_AppCompat_Dialog)
                .setColors(R.array.primary_color_palette)
                .setSelectedColor(vUiColorPrimary.getValue())
                .setDismissOnColorSelected(true)
                .setOutlineWidth(2)
                .setOnColorSelectedListener(new SpectrumDialog.OnColorSelectedListener() {
                    @Override
                    public void onColorSelected(boolean positiveResult, @ColorInt final int color) {
                        if (DEBUG) {
                            gLog.l(TAG, Logger.lvVerbose, "spectrumDialog " + positiveResult);
                            gLog.l(TAG, Logger.lvVerbose, "spectrumDialog " + color);
                        }
                        if (positiveResult) {
                            vUiColorPrimary.setValue(color);
                        }
                    }
                }).build();
        vUiColorPrimary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isPrem) {
                    spectrumDialog.updateSelectedColor(vUiColorPrimary.getValue());
                    spectrumDialog.show(getFragmentManager(), "spectrum__color_picker");
                } else dialogPremiumRequiredColorPicker.show();
            }
        });

        vUiColorNotifDark = (CompRowSw) findViewById(R.id.ilSwUiColorNotifDark);
        vUiColorNotifDark.setName(getString("dark_notifications", "Dark Notifications"));
        vUiColorNotifDark.setValue(sipService.rsGetBoolean(Prefs.KEY_UI_COLOR_NOTIF_DARK, false))
                .setChangeListener(this);
        if (!isPrem) vUiColorNotifDark.setOnClickListener(onClickListenerPremium);
        vUiColorNotifDark.setIsPremium(true).setRowEnabled(isPrem);
        vUiColorNotifDark.setHelpDrawable(dHelp)
                .setHelpVisibility(View.VISIBLE)
                .setHelpOnClick(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(contextWrapper);
                        builder.setTitle(getString("dark_notifications", "Dark Notifications"))
                                .setMessage(getString("help_dark_notifications", "Use dark themed notifications. The default is disabled."))
                                .setCancelable(true)
                                .setPositiveButton(getString("close", "Close"), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                });

        vVibe = (CompRowSw) findViewById(R.id.ilSwVibe);
        vVibe.setName(getString("vibrate_on_ring", "Vibrate on Ring"));
        vVibe.setValue(sipService.rsGetBoolean(Prefs.KEY_VIBRATE_ON_RING, true))
                .setChangeListener(this);
        vVibe.setHelpDrawable(dHelp)
                .setHelpVisibility(View.VISIBLE)
                .setHelpOnClick(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(contextWrapper);
                        builder.setTitle(getString("vibrate_on_ring", "Vibrate on Ring"))
                                .setMessage(getString("help_vibrate_on_ring", "Always vibrate device on incoming calls. The default is enabled."))
                                .setCancelable(true)
                                .setPositiveButton(getString("close", "Close"), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                });

        vAltLaunchers = (CompRowSw) findViewById(R.id.ilSwAltLaunchers);
        vAltLaunchers.setName(getString("show_alternate_launcher_icons", "Show Alternate Launcher Icons"));
        vAltLaunchers.setValue(sipService.rsGetBoolean(Prefs.KEY_SHOW_ALTERNATE_LAUNCHERS, false))
                .setChangeListener(this);
        vAltLaunchers.setIsPremium(true).setRowEnabled(isPrem);
        if (!isPrem) vAltLaunchers.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialogPremiumRequiredAltIcons.show();
            }
        });
        vAltLaunchers.setHelpDrawable(dHelp)
                .setHelpVisibility(View.VISIBLE)
                .setHelpOnClick(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            new DialogHelpAltIcons().buildAndShow(contextWrapper, sipService);
                        } catch (RemoteException e) {
                            gLog.l(TAG, Logger.lvDebug, e);
                            if (DEBUG) e.printStackTrace();
                        }
                    }
                });

        vMediaAudioPreAmpRx = (CompRowEditIbIntRange) findViewById(R.id.ilMediaAudioPreAmpRx);
        vMediaAudioPreAmpRx.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        vMediaAudioPreAmpRx.setName(getString("audio_preamp_rx", "Audio PreAmp RX"));
        vMediaAudioPreAmpRx.setValue(String.valueOf(sipService.rsGetFloat(Prefs.KEY_MEDIA_PREAMP_RX, Prefs.KEY_MEDIA_PREAMP_RX_DEFAULT)))
                .setChangeListener(this);
        vMediaAudioPreAmpRx.setIsPremium(true).setRowEnabled(isPrem);
        vMediaAudioPreAmpRx.setBoundRange(1, 3).setSnapDefaults(1).setSnapToRange(true);
        if (!isPrem) vMediaAudioPreAmpRx.setOnClickListener(onClickListenerPremium);
        vMediaAudioPreAmpRx.setHelpDrawable(dHelp)
                .setHelpVisibility(View.VISIBLE)
                .setHelpOnClick(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(contextWrapper);
                        builder.setTitle(getString("audio_preamp_rx", "Audio PreAmp RX"))
                                .setMessage(getString("help_media_audio_preamp_rx", "Speaker audio preamplifier multiplier factor. Higher values may boost increase speaker volume but may also cause audio distortion. The range is 1 to 3, where 1 is neutral. The default is 2."))
                                .setCancelable(true)
                                .setPositiveButton(getString("close", "Close"), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                });

        vMediaAudioPreAmpTx = (CompRowEditIbIntRange) findViewById(R.id.ilMediaAudioPreAmpTx);
        vMediaAudioPreAmpTx.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        vMediaAudioPreAmpTx.setName(getString("audio_preamp_tx", "Audio PreAmp TX"));
        vMediaAudioPreAmpTx.setValue(String.valueOf(sipService.rsGetFloat(Prefs.KEY_MEDIA_PREAMP_TX, Prefs.KEY_MEDIA_PREAMP_TX_DEFAULT)))
                .setChangeListener(this);
        vMediaAudioPreAmpTx.setIsPremium(true).setRowEnabled(isPrem);
        vMediaAudioPreAmpTx.setBoundRange(1, 10).setSnapDefaults(1).setSnapToRange(true);
        if (!isPrem) vMediaAudioPreAmpTx.setOnClickListener(onClickListenerPremium);
        vMediaAudioPreAmpTx.setHelpDrawable(dHelp)
                .setHelpVisibility(View.VISIBLE)
                .setHelpOnClick(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(contextWrapper);
                        builder.setTitle(getString("audio_preamp_tx", "Audio PreAmp TX"))
                                .setMessage(getString("help_media_audio_preamp_tx", "Microphone audio preamplifier multiplier factor. Higher values may boost increase microphone volume but may also cause audio distortion. The range is 1 to 10, where 1 is neutral. The default is 1.5."))
                                .setCancelable(true)
                                .setPositiveButton(getString("close", "Close"), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                });

        vMediaQuality = (CompRowEditIbIntRange) findViewById(R.id.ilMediaQuality);
        vMediaQuality.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_NORMAL);
        vMediaQuality.setName(getString("media_quality", "Media Quality"));
        vMediaQuality.setValue(String.valueOf(sipService.rsGetInt(Prefs.KEY_MEDIA_QUALITY, SipEndpoint.MEDIA_QUALITY_DEFAULT)))
                .setChangeListener(this);
        vMediaQuality.setIsPremium(true).setRowEnabled(isPrem);
        vMediaQuality.setBoundRange(1, 10).setSnapDefaults(2).setSnapToRange(true);
        if (!isPrem) vMediaQuality.setOnClickListener(onClickListenerPremium);
        vMediaQuality.setHelpDrawable(dHelp)
                .setHelpVisibility(View.VISIBLE)
                .setHelpOnClick(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(contextWrapper);
                        builder.setTitle(getString("media_quality", "Media Quality"))
                                .setMessage(getString("help_media_quality", "Internal audio codec and filtering quality. Higher values significantly increase CPU use and battery consumption. High values beyond the device capability may cause audio artifacts or gaps. The valid range is 1 to 10. The default is 2."))
                                .setCancelable(true)
                                .setPositiveButton(getString("close", "Close"), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                });

        setColors();
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

        sipService.rsSetBoolean(Prefs.KEY_HANDLE_OUTGOING_ALL, vOutgoingAll.getValue());
        sipService.rsSetBoolean(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_ENABLED), true);
        sipService.rsSetString(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_NAME), vFriendly.getValue());
        sipService.rsSetString(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_USER), vUser.getValue());
        sipService.rsSetString(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_SECRET), vSecret.getValue());
        sipService.rsSetString(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_SERVER), vServer.getValue());
        sipService.rsSetString(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_SERVER_OUT_PROXY), vServerOutProxy.getValue());
        try {
            final int i = Integer.parseInt(vPort.getValue());
            sipService.rsSetInt(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_PORT), i);
        } catch (NumberFormatException e) {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, e);
        }
        try {
            final int i = Integer.parseInt(vPortOutProxy.getValue());
            sipService.rsSetInt(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_PORT_OUT_PROXY), i);
        } catch (NumberFormatException e) {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, e);
        }
        try {
            final int i = Integer.parseInt(vRtpRange.getValue());
            sipService.rsSetInt(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_MEDIA_PORT_BEGIN), i);
        } catch (NumberFormatException e) {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, e);
        }
        try {
            final int i = Integer.parseInt(vRtpRange.getValue2());
            sipService.rsSetInt(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_MEDIA_PORT_END), i);
        } catch (NumberFormatException e) {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, e);
        }
        try {
            final int i = Integer.parseInt(vRegExpire.getValue());
            sipService.rsSetInt(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_REG_EXPIRE), i);
        } catch (NumberFormatException e) {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, e);
        }
        sipService.rsSetBoolean(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_USE_STUN), vStun.getValue());
        sipService.rsSetBoolean(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_USE_ICE), vIce.getValue());
        sipService.rsSetBoolean(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_TCP), vTcp.getValue());
        sipService.rsSetBoolean(Prefs.KEY_ONBOOT_ENABLE, vOnBoot.getValue());
        sipService.rsSetBoolean(Prefs.KEY_DOZE_WORKAROUND_ENABLE, vDozeWorkAround.getValue());
        sipService.rsSetBoolean(Prefs.KEY_DOZE_DISABLE_SU, vDozeDisableRoot.getValue());
        sipService.rsSetBoolean(Prefs.KEY_WIFI_LOCK_ENABLE, vWifiLock.getValue());
        sipService.rsSetBoolean(Prefs.KEY_VIBRATE_ON_RING, vVibe.getValue());
        sipService.rsSetBoolean(Prefs.KEY_UI_COLOR_NOTIF_DARK, vUiColorNotifDark.getValue());
        sipService.rsSetBoolean(Prefs.KEY_SHOW_ALTERNATE_LAUNCHERS, vAltLaunchers.getValue());
        sipService.rsSetString(Prefs.KEY_STUN_SERVER, vStunServer.getValue());
        try {
            final int i = Integer.parseInt(vKaTcpMobile.getValue());
            sipService.rsSetInt(Prefs.KEY_KA_TCP_MOBILE, i);
        } catch (NumberFormatException e) {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, e);
        }
        try {
            final int i = Integer.parseInt(vKaTcpWifi.getValue());
            sipService.rsSetInt(Prefs.KEY_KA_TCP_WIFI, i);
        } catch (NumberFormatException e) {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, e);
        }
        sipService.rsSetInt(Prefs.KEY_UI_COLOR_PRIMARY, vUiColorPrimary.getValue());
        try {
            final float v = Float.parseFloat(vMediaAudioPreAmpRx.getValue());
            sipService.rsSetFloat(Prefs.KEY_MEDIA_PREAMP_RX, v);
        } catch (NumberFormatException e) {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, e);
        }
        try {
            final float v = Float.parseFloat(vMediaAudioPreAmpTx.getValue());
            sipService.rsSetFloat(Prefs.KEY_MEDIA_PREAMP_TX, v);
        } catch (NumberFormatException e) {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, e);
        }
        try {
            final int i = Integer.parseInt(vMediaQuality.getValue());
            sipService.rsSetInt(Prefs.KEY_MEDIA_QUALITY, i);
        } catch (NumberFormatException e) {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, e);
        }

        sipService.rsCommit(true);
        sipService.accountChangesComitted();
        finish();
    }

    private void setColors() {
        getActionBar().setBackgroundDrawable(new ColorDrawable(vUiColorPrimary.getValue()));
    }

    class EventHandler extends IRemoteSipServiceEvents.Stub {

        @Override
        public void onCallMuteStateChanged(int pjsipCallId) throws RemoteException {

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
