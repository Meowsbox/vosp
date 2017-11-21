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
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.meowsbox.vosp.DialerApplication;
import com.meowsbox.vosp.IRemoteSipService;
import com.meowsbox.vosp.R;
import com.meowsbox.vosp.common.Logger;
import com.meowsbox.vosp.service.licensing.LicensingManager;
import com.meowsbox.vosp.service.licensing.Sku;


/**
 * Created by dhon on 6/22/2017.
 */

public class DialogPremiumUpgrade {
    public static final boolean DEBUG = DialerApplication.DEBUG;
    static Logger gLog = new Logger(DialerApplication.LOGGER_VERBOSITY);
    public final String TAG = this.getClass().getName();

    private Dialog dialog;

    public DialogPremiumUpgrade build(final Context context, final IRemoteSipService sipService) throws RemoteException {
        if (context == null | sipService == null) {
            if (DEBUG) gLog.l(TAG,Logger.lvDebug,"context or sipService NULL");
            return null;
        }
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

        final Bundle skuInfoBundle = sipService.getSkuInfoBundle();
        Sku skuDataPrem = null, skuDataPatron = null;
        if (skuInfoBundle == null) gLog.l(TAG, Logger.lvDebug, "skuInfoBundle NULL!");
        else if (skuInfoBundle.getInt(LicensingManager.KEY_STATE) != LicensingManager.STATE_OK) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, "LicensingManager Not Ready");
        } else {
            Bundle bundle = skuInfoBundle.getBundle(LicensingManager.KEY_PREM);
            if (bundle != null) skuDataPrem = Sku.fromBundle(bundle);
            else if (DEBUG) gLog.l(TAG, Logger.lvDebug, "skuDataPrem NULL");

            bundle = skuInfoBundle.getParcelable(LicensingManager.KEY_PATRON);
            if (bundle != null) skuDataPatron = Sku.fromBundle(bundle);
            else if (DEBUG) gLog.l(TAG, Logger.lvDebug, "skuDataPatron NULL");

        }

        LayoutInflater li = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View view = li.inflate(R.layout.dialog_fragment_gopro, null);
        ((TextView) view.findViewById(R.id.pmaintitle)).setText(sipService.getLocalString("premium_features", "Premium Features"));
        ((TextView) view.findViewById(R.id.pmainsubtitle)).setText(sipService.getLocalString("support_your_developers", "Support your developers"));
        ((TextView) view.findViewById(R.id.pcapmain)).setText(sipService.getLocalString("premium_upgrade_caption", "Your contributions help make this app and its continued development possible. Two ways to show your support and enjoy these additional benefits."));

        View pcard1 = view.findViewById(R.id.pcard1);
        ((ImageView) pcard1.findViewById(R.id.icon)).setImageResource(R.drawable.gem_icon);
        ((TextView) pcard1.findViewById(R.id.title)).setText(sipService.getLocalString("premium_upgrade", "Premium Upgrade"));
//        ((TextView) pcard1.findViewById(R.id.subtitle)).setText(sipService.getLocalString("$4.99", "$4.99")); // place holder
        if (skuDataPrem != null) ((TextView) pcard1.findViewById(R.id.subtitle)).setText(skuDataPrem.price);
        ((TextView) pcard1.findViewById(R.id.cap1)).setText(sipService.getLocalString("premup_p1", "16 Material theme colors"));
        ((TextView) pcard1.findViewById(R.id.cap2)).setText(sipService.getLocalString("premup_p2", "4 Bonus launcher icons"));
        ((TextView) pcard1.findViewById(R.id.cap3)).setText(sipService.getLocalString("premup_p3", "Call recording + more settings"));
        pcard1.setClickable(true);
        if (!isPrem) pcard1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sipService == null){
                    if (DEBUG) gLog.l(TAG,Logger.lvDebug,"sipService NULL");
                    dismiss();
                    return;
                }
                try {
                    final int r = sipService.licensingBuyPrem();
                    handlePreBuyIntentResult(context, sipService, r);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
        else {
            ((TextView) pcard1.findViewById(R.id.tvOwnedTitle)).setText(sipService.getLocalString("thankyou_exp", "Thank You!"));
            ((FrameLayout) pcard1.findViewById(R.id.flOwnedOverlay)).setVisibility(View.VISIBLE);
        }

        View pcard2 = view.findViewById(R.id.pcard2);
        ((ImageView) pcard2.findViewById(R.id.icon)).setImageResource(R.drawable.coffee_icon);
        ((TextView) pcard2.findViewById(R.id.title)).setText(sipService.getLocalString("become_a_patron", "Become a Patron"));
//        ((TextView) pcard2.findViewById(R.id.subtitle)).setText(sipService.getLocalString("$0.99/month", "$0.99/month")); // place holder
        if (skuDataPatron != null) {
//            ((TextView) pcard2.findViewById(R.id.subtitle)).setText(skuDataPatron.price + "/" + skuDataPatron.subscriptionPeriod);

            StringBuilder sb = new StringBuilder();
            sb.append(skuDataPatron.price);
            sb.append(" / ");

            switch (skuDataPatron.subscriptionPeriod) { // TODO: perform actual ISO8601 parsing
                case "P1W":
                    sb.append(sipService.getLocalString("week", "week"));
                    break;
                case "P1M":
                    sb.append(sipService.getLocalString("month", "month"));
                    break;
                case "P3M":
                    sb.append("3 ");
                    sb.append(sipService.getLocalString("months", "months"));
                    break;
                case "P6M":
                    sb.append("6 ");
                    sb.append(sipService.getLocalString("months", "months"));
                    break;
                case "P1Y":
                    sb.append(sipService.getLocalString("year", "year"));
                    break;
            }
            ((TextView) pcard2.findViewById(R.id.subtitle)).setText(sb.toString());
        }
        ((TextView) pcard2.findViewById(R.id.cap1)).setText(sipService.getLocalString("patup_p1", "All the Premium Upgrade benefits"));
        ((TextView) pcard2.findViewById(R.id.cap2)).setText(sipService.getLocalString("patup_p2", "Less than the cost of a coffee"));
        ((TextView) pcard2.findViewById(R.id.cap3)).setVisibility(View.GONE);

        pcard2.setClickable(true);
        if (!isPatron) pcard2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sipService == null){
                    if (DEBUG) gLog.l(TAG,Logger.lvDebug,"sipService NULL");
                    dismiss();
                    return;
                }
                try {
                    final int r = sipService.licensingBuyPatron();
                    handlePreBuyIntentResult(context, sipService, r);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
        else {
            ((TextView) pcard2.findViewById(R.id.tvOwnedTitle)).setText(sipService.getLocalString("thankyou_exp", "Thank You!"));
            ((FrameLayout) pcard2.findViewById(R.id.flOwnedOverlay)).setVisibility(View.VISIBLE);
        }

        dialog = new Dialog(context, R.style.Theme_AppCompat_Light_Dialog_Alert);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(view);
//        dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_inset);
        dialog.getWindow().setBackgroundDrawable(null);
        return this;
    }

    public DialogPremiumUpgrade buildAndShow(final Context context, final IRemoteSipService sipService) throws RemoteException {
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

    public void handlePreBuyIntentResult(Context context, IRemoteSipService sipService, int result) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, result);
        if (context == null || sipService == null) {
            if (dialog != null) dialog.dismiss();
            return;
        }
        switch (result) {
            case LicensingManager.RESULT_ERROR_ALREADY_OWNED: {
                AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.Theme_AppCompat_Light_Dialog_Alert);
                try {
                    builder.setTitle(sipService.getLocalString("already_owned", "Already Owned"));
                    builder.setMessage(sipService.getLocalString("you_have_already_purchased_this_option", "You have already purchased this option."));
                    builder.setPositiveButton(context.getResources().getText(R.string.ok), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                    builder.create().show();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            break;
            case LicensingManager.RESULT_ERROR: {
                AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.Theme_AppCompat_Light_Dialog_Alert);
                try {
                    builder.setTitle(sipService.getLocalString("licensing_problem", "Licensing Problem"));
                    builder.setMessage(sipService.getLocalString("please_try_again_later", "Please try again later."));
                    builder.setPositiveButton(context.getResources().getText(R.string.ok), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                    builder.create().show();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            break;
            case LicensingManager.RESULT_OK:
                if (dialog != null) dialog.dismiss();
            default:
                break;
        }
    }
}
