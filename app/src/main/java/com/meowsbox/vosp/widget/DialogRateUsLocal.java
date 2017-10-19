/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.widget;

import android.animation.Animator;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.RatingBar;
import android.widget.TextView;

import com.meowsbox.vosp.DialerApplication;
import com.meowsbox.vosp.IRemoteSipService;
import com.meowsbox.vosp.R;
import com.meowsbox.vosp.common.Logger;
import com.meowsbox.vosp.service.Prefs;
import com.meowsbox.vosp.service.providers.SupportAttachmentProvider;

import java.util.ArrayList;


/**
 * Simple rating dialog to determine user satisfaction and prompt for outreach below specified threshold
 * Created by dhon on 6/22/2017.
 */

public class DialogRateUsLocal {
    public static final boolean DEBUG = DialerApplication.DEBUG;
    static Logger gLog = DEBUG ? new Logger(DialerApplication.LOGGER_VERBOSITY) : null;
    public final String TAG = this.getClass().getName();

    private Dialog dialog;

    public DialogRateUsLocal build(final Context context, final IRemoteSipService sipService) throws RemoteException {
        if (context == null | sipService == null) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, "context or sipService NULL");
            return null;
        }
        LayoutInflater li = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View view = li.inflate(R.layout.dialog_fragment_rateus_local, null);

        final TextView tvTitle = (TextView) view.findViewById(R.id.tvTitle);
        tvTitle.setText(sipService.rsGetString("rate_title", "Let us know how you like this app."));

        final RatingBar rbStars = (RatingBar) view.findViewById(R.id.rbStars);

        rbStars.setOnRatingBarChangeListener(new RatingBar.OnRatingBarChangeListener() {
            @Override
            public void onRatingChanged(RatingBar ratingBar, float rating, boolean fromUser) {
                if (rating < 1) ratingBar.setRating(1);
            }
        });

        final Button bSubmit = (Button) view.findViewById(R.id.bRateUs);
        bSubmit.setText(sipService.rsGetString("submit", "Submit"));
        bSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sipService == null) {
                    if (DEBUG) gLog.l(TAG, Logger.lvDebug, "sipService NULL");
                    dismiss();
                    return;
                }
                try {
                    sipService.rsSetLong(Prefs.KEY_FLAG_RATE_LOCAL_TS, System.currentTimeMillis());
                } catch (RemoteException e) {
                    if (DEBUG) e.printStackTrace();
                }
                final RatingBar rbStars = (RatingBar) view.findViewById(R.id.rbStars);
                if (rbStars != null) {
                    try {
                        sipService.rsSetInt(Prefs.KEY_FLAG_RATE_LOCAL, (int) rbStars.getRating());
                        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "Rating: " + rbStars.getRating());
                    } catch (RemoteException e) {
                        if (DEBUG) e.printStackTrace();
                    }
                    rbStars.setEnabled(false);
                } else if (DEBUG) gLog.l(TAG, Logger.lvDebug, "RatingsBar NULL");
                try {
                    sipService.rsCommit(true);
                } catch (RemoteException e) {
                    if (DEBUG) e.printStackTrace();
                }

                final Button bContact = (Button) view.findViewById(R.id.bContactUs);
                try {
                    bContact.setText(sipService.rsGetString("send_feedback_q", "Send Feedback?"));
                } catch (RemoteException e) {
                    if (DEBUG) e.printStackTrace();
                }
                bContact.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (sipService == null) {
                            if (DEBUG) gLog.l(TAG, Logger.lvDebug, "sipService NULL");
                            dismiss();
                            return;
                        }
                        Intent i = new Intent(Intent.ACTION_SEND_MULTIPLE);
                        i.setType("message/rfc822");
                        i.putExtra(Intent.EXTRA_EMAIL, new String[]{"support@meowsbox.com"});
                        String deviceId = null;
                        try {
                            deviceId = sipService.getDeviceId();
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                        i.putExtra(Intent.EXTRA_SUBJECT, "VOSP Ratings Feedback " + deviceId);
                        try {
                            i.putExtra(Intent.EXTRA_TEXT, sipService.getLocalString("ratings_email_body", ""));
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


                bSubmit.setEnabled(false);
                try {
                    bSubmit.setText(sipService.rsGetString("thank_you", "Thank You"));
                } catch (RemoteException e) {
                    if (DEBUG) e.printStackTrace();
                }
                bSubmit.animate()
                        .setStartDelay(1000)
                        .setListener(new Animator.AnimatorListener() {
                            @Override
                            public void onAnimationStart(Animator animation) {
                            }

                            @Override
                            public void onAnimationEnd(Animator animation) {
                                bSubmit.setVisibility(View.GONE);
                                bContact.setVisibility(View.VISIBLE);
                            }

                            @Override
                            public void onAnimationCancel(Animator animation) {

                            }

                            @Override
                            public void onAnimationRepeat(Animator animation) {

                            }
                        })
                        .start();


            }
        });

        dialog = new Dialog(context, R.style.Theme_AppCompat_Light_Dialog_Alert);
        dialog.setContentView(view);
        return this;
    }

    public DialogRateUsLocal buildAndShow(final Context context, final IRemoteSipService sipService) throws RemoteException {
        build(context, sipService);
        if (dialog != null) dialog.show();
        return this;
    }

    public void dismiss() {
        if (dialog != null) dialog.dismiss();
    }

    public void show() {
        if (dialog != null) dialog.show();
    }

}
