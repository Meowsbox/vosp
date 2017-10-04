/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.widget;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.meowsbox.vosp.BuildConfig;
import com.meowsbox.vosp.DialerApplication;
import com.meowsbox.vosp.IRemoteSipService;
import com.meowsbox.vosp.LicensesActivity;
import com.meowsbox.vosp.R;
import com.meowsbox.vosp.common.Logger;
import com.meowsbox.vosp.service.licensing.LicensingManager;
import com.meowsbox.vosp.service.providers.SupportAttachmentProvider;

import java.util.ArrayList;
import java.util.Locale;


/**
 * Created by dhon on 6/22/2017.
 */

public class DialogAbout {
    public static final boolean DEBUG = DialerApplication.DEBUG;
    static Logger gLog = new Logger(DialerApplication.LOGGER_VERBOSITY);
    public final String TAG = this.getClass().getName();

    private Dialog dialog;

    public DialogAbout build(final Context context, final IRemoteSipService sipService) throws RemoteException {
        boolean isPrem = false;
        boolean isPatron = false;
        try {
            final Bundle licensingStateBundle = sipService.getLicensingStateBundle();
            if (licensingStateBundle != null)
                isPrem = licensingStateBundle.getBoolean(LicensingManager.KEY_PREM, false);
            isPatron = licensingStateBundle.getBoolean(LicensingManager.KEY_PATRON, false);
        } catch (RemoteException e) {
            if (DEBUG) e.printStackTrace();
        }

        LayoutInflater li = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View view = li.inflate(R.layout.dialog_fragment_about, null);

        final Button bService, bContact;

        ApplicationInfo applicationInfo = context.getApplicationInfo();
        final TextView tvAppName = (TextView) view.findViewById(R.id.tvAppName);
        if (applicationInfo.labelRes == 0) {
            tvAppName.setText(applicationInfo.nonLocalizedLabel.toString());
        } else
            tvAppName.setText(applicationInfo.labelRes);

        final TextView tvVersion = (TextView) view.findViewById(R.id.tvVersion);
        StringBuilder sb = new StringBuilder();
        sb.append(BuildConfig.VERSION_NAME);
        sb.append('.');
        sb.append(BuildConfig.VERSION_CODE);
        if (isPatron) {
            sb.append(" ");
            sb.append(sipService.getLocalString("patron", "PATRON").toUpperCase());
        }
        if (isPrem) {
            sb.append(" ");
            sb.append(sipService.getLocalString("premium", "PREMIUM").toUpperCase());
        }
        if (BuildConfig.DEBUG) {
            sb.append(" ");
            sb.append(sipService.getLocalString("debug", "DEBUG").toUpperCase());
        }
        if (DialerApplication.DEV) {
            sb.append(" ");
            sb.append(sipService.getLocalString("dev", "DEV").toUpperCase());
        }
        if (BuildConfig.DEBUG || DialerApplication.DEV) {
            sb.append("\r\n");
            sb.append(sipService.getLocalString("not_for_redistribution", "NOT FOR REDISTRIBUTION").toUpperCase());
        }
        if (Locale.getDefault().getLanguage().contains("ru")) { // See com.meowsbox.vosp.service.licensing.RuFederationLicensingProvider
            sb.append("\r\n");
            sb.append("Версия для бывшего СССР");
            sb.append("\r\n");
            sb.append("Купите если понравилось!");
        }
        sb.append("\r\n");
        sb.append(sipService.getDeviceId());
        tvVersion.setText(sb.toString());

        final TextView tvNotes = (TextView) view.findViewById(R.id.tvNotes);
        tvNotes.setText(sipService.getLocalString("release_notes", "Release Notes"));
        tvNotes.setPaintFlags(tvNotes.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        tvNotes.setVisibility(View.VISIBLE);
        tvNotes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = "http://apps.meowsbox.com/vosp/release_notes-en";
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                context.startActivity(i);
            }
        });
        tvNotes.setClickable(true);

        final TextView tvTransHelp = (TextView) view.findViewById(R.id.tvHelpTrans);
        tvTransHelp.setText(sipService.getLocalString("help_translate", "Help Translate"));
        tvTransHelp.setPaintFlags(tvTransHelp.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        tvTransHelp.setVisibility(View.VISIBLE);
        tvTransHelp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = "http://apps.meowsbox.com/vosp/help_trans-en";
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                context.startActivity(i);
            }
        });
        tvTransHelp.setClickable(true);


        final TextView tvTerms = (TextView) view.findViewById(R.id.tvTerms);
        tvTerms.setText(sipService.getLocalString("view_terms_license_policy", "View Privacy Policy and Licenses"));
        tvTerms.setPaintFlags(tvTerms.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        tvTerms.setVisibility(View.VISIBLE);
        tvTerms.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Intent intent = new Intent(context, LicensesActivity.class);
                context.startActivity(intent);
            }
        });
        tvTerms.setClickable(true);

        bService = (Button) view.findViewById(R.id.bServiceCode);
        bService.setText(sipService.rsGetString("service_code", "Service Code"));
        bService.setEnabled(false);

        bContact = (Button) view.findViewById(R.id.bContactSupport);
        bContact.setText(sipService.rsGetString("contact_support", "Contact Support"));
        bContact.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_SEND_MULTIPLE);
                i.setType("message/rfc822");
                i.putExtra(Intent.EXTRA_EMAIL, new String[]{"support@meowsbox.com"});
                String deviceId = null;
                try {
                    deviceId = sipService.getDeviceId();
                } catch (RemoteException e) {
                    if (DEBUG) e.printStackTrace();
                }
                i.putExtra(Intent.EXTRA_SUBJECT, "VOSP " + deviceId);
                try {
                    i.putExtra(Intent.EXTRA_TEXT, sipService.getLocalString("support_email_body", ""));
                } catch (RemoteException e) {
                    if (DEBUG) e.printStackTrace();
                }

                ArrayList<Uri> attachmentUriList = new ArrayList<Uri>();
                attachmentUriList.add(Uri.parse("content://" + SupportAttachmentProvider.AUTHORITY + "/" + "data.db.zip"));
                attachmentUriList.add(Uri.parse("content://" + SupportAttachmentProvider.AUTHORITY + "/" + "logs.db.zip"));

                i.putExtra(Intent.EXTRA_STREAM, attachmentUriList);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // grant the external mail app permission to read attachments
                dismiss();
                context.startActivity(Intent.createChooser(i, "Send mail..."));
            }
        });

        dialog = new Dialog(context, R.style.Theme_AppCompat_Light_Dialog_Alert);
        dialog.setContentView(view);
////        dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_inset);
//        dialog.getWindow().setBackgroundDrawable(null);
        return this;
    }

    public DialogAbout buildAndShow(final Context context, final IRemoteSipService sipService) throws RemoteException {
        build(context, sipService);
        dialog.show();
        return this;
    }

    public void dismiss() {
        if (dialog != null) dialog.dismiss();
    }

    public void show() {
        if (dialog != null) dialog.show();
    }

}
