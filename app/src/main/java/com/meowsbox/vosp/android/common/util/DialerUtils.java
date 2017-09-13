/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License.
 */
package com.meowsbox.vosp.android.common.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.meowsbox.vosp.DialerApplication;
import com.meowsbox.vosp.R;
import com.meowsbox.vosp.service.receivers.OutgoingCallReceiver;
import com.meowsbox.vosp.android.common.ContactsUtils;
import com.meowsbox.vosp.android.common.interactions.TouchPointManager;
import com.meowsbox.vosp.common.Logger;

import java.util.List;
import java.util.Locale;

/**
 * General purpose utility methods for the Dialer.
 */
public class DialerUtils {
    public static final String TAG = "DialerUtils";
    private static final boolean DEBUG = DialerApplication.DEBUG;

    /**
     * Closes an {@link AutoCloseable}, silently ignoring any checked exceptions. Does nothing if
     * null.
     *
     * @param closeable to close.
     */
    public static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Sets the image asset and text for an empty list view (see empty_list_view.xml).
     *
     * @param emptyListView The empty list view.
     * @param imageResId    The resource id for the drawable to set as the image.
     * @param strResId      The resource id for the string to set as the message.
     * @param res           The resources to obtain the image and string from.
     */
    public static void configureEmptyListView(
            View emptyListView, int imageResId, int strResId, Resources res) {
        ImageView emptyListViewImage =
                (ImageView) emptyListView.findViewById(R.id.emptyListViewImage);

        emptyListViewImage.setImageDrawable(res.getDrawable(imageResId));
        emptyListViewImage.setContentDescription(res.getString(strResId));

        TextView emptyListViewMessage =
                (TextView) emptyListView.findViewById(R.id.emptyListViewMessage);
        emptyListViewMessage.setText(res.getString(strResId));
    }

    /**
     * Returns the component name to use in order to send an SMS using the default SMS application,
     * or null if none exists.
     */
    public static ComponentName getSmsComponent(Context context) {
        String smsPackage = Telephony.Sms.getDefaultSmsPackage(context);
        if (smsPackage != null) {
            final PackageManager packageManager = context.getPackageManager();
            final Intent intent = new Intent(Intent.ACTION_SENDTO,
                    Uri.fromParts(ContactsUtils.SCHEME_SMSTO, "", null));
            final List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(intent, 0);
            for (ResolveInfo resolveInfo : resolveInfos) {
                if (smsPackage.equals(resolveInfo.activityInfo.packageName)) {
                    return new ComponentName(smsPackage, resolveInfo.activityInfo.name);
                }
            }
        }
        return null;
    }

    public static void hideInputMethod(View view) {
        final InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * @return True if the application is currently in RTL mode.
     */
    public static boolean isRtl() {
        return TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) ==
                View.LAYOUT_DIRECTION_RTL;
    }

    /**
     * Joins a list of {@link CharSequence} into a single {@link CharSequence} seperated by a
     * localized delimiter such as ", ".
     *
     * @param resources Resources used to get list delimiter.
     * @param list      List of char sequences to join.
     * @return Joined char sequences.
     */
    public static CharSequence join(Resources resources, Iterable<CharSequence> list) {
        final CharSequence separator = resources.getString(R.string.list_delimeter);
        return TextUtils.join(separator, list);
    }

    public static void showInputMethod(View view) {
        final InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, 0);
        }
    }

    /**
     * Attempts to start an activity and displays a toast with a provided error message if the
     * activity is not found, instead of throwing an exception.
     *
     * @param context to start the activity with.
     * @param intent  to start the activity with.
     * @param msgId   Resource ID of the string to display in an error message if the activity is
     *                not found.
     */
    private static void startActivityWithErrorToast(Context context, Intent intent, int msgId) {
        try {
            if (Intent.ACTION_CALL.equals(intent.getAction()) || "android.intent.action.CALL_PRIVILEGED".equals(intent.getAction())) {
                // All dialer-initiated calls should pass the touch point to the InCallUI
                Point touchPoint = TouchPointManager.getInstance().getPoint();
                if (touchPoint.x != 0 || touchPoint.y != 0) {
                    Bundle extras = new Bundle();
                    extras.putParcelable(TouchPointManager.TOUCH_POINT, touchPoint);
                    intent.putExtra(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, extras);
                }

                ((Activity) context).startActivityForResult(intent, 0);
            } else {
                context.startActivity(intent);
            }
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, msgId, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     *
     * Attempts to start an activity and displays a toast with the default error message if the
     * activity is not found, instead of throwing an exception.
     *
     * @param context to start the activity with.
     * @param intent  to start the activity with.
     */
    public static void startActivityWithErrorToast(Context context, Intent intent) {
        Logger gLog = new Logger(DialerApplication.LOGGER_VERBOSITY);
        if (DEBUG) gLog.lt(Logger.lvVerbose, TAG, "startActivityWithErrorToast");
        if (DEBUG) gLog.lt(Logger.lvVerbose, TAG, intent.getAction());
        Uri uri = intent.getData();
        if (uri != null && uri.getScheme().equals("tel")) {
            String schemeSpecificPart = uri.getSchemeSpecificPart();
            if (DEBUG) gLog.lt(Logger.lvVerbose, TAG, schemeSpecificPart);
            Intent intent1 = new Intent(context, OutgoingCallReceiver.class);
            intent1.setAction("com.meowsbox.internal.vosp.dialerutil");
            intent1.putExtra(Intent.EXTRA_PHONE_NUMBER, schemeSpecificPart);
            context.sendBroadcast(intent1);
        } else {
            //TODO handle SIP uri
            if (DEBUG) gLog.lt(Logger.lvVerbose, TAG, "not a TEL uri");
            startActivityWithErrorToast(context, intent, R.string.activity_not_available);
        }

    }
}
