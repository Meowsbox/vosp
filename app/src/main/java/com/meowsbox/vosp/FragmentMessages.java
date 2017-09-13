/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp;

import android.app.Fragment;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.meowsbox.vosp.service.Prefs;
import com.meowsbox.vosp.widget.UiMessageController;

/**
 * Created by dhon on 3/31/2017.
 */

public class FragmentMessages extends Fragment implements ServiceBindingController.ServiceConnectionEvents {
    private final String TAG = this.getClass().getSimpleName();
    private int primaryColor = Prefs.DEFAULT_UI_COLOR_PRIMARY;
    private int drawableTint = Color.parseColor("#757575");
    private View parentView;
    private ListView lvMessages;
    private IRemoteSipService sipService = null;
    private ServiceBindingController mServiceController = null;
    private UiMessageController umc = null;

    public void clearMessageSmid(int smid) {
        umc.itemRemoveWithSmId(smid);
    }

    public void messageClearByType(int iAnType) {
        if (umc != null) umc.messageClearByType(iAnType);

    }

    public void messagePost(int iAnType, Bundle bundle) {
        if (umc != null) umc.messagePost(iAnType, bundle);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        parentView = inflater.inflate(R.layout.fragment_messages, container, false);
        lvMessages = (ListView) parentView.findViewById(R.id.lvMessages);
        mServiceController = new ServiceBindingController(null, this, parentView.getContext());
        return parentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        mServiceController.bindToService();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mServiceController != null) mServiceController.unbindToService();

    }

    @Override
    public void onDetach() {
        super.onDetach();
        mServiceController.unbindToService();
    }

    @Override
    public void onServiceConnectTimeout() {

    }

    @Override
    public void onServiceConnected(IRemoteSipService remoteService) {
        if (umc == null) umc = new UiMessageController(remoteService, lvMessages);
        umc.setPrimaryColor(primaryColor);
    }

    @Override
    public void onServiceDisconnected() {

    }

    public void setPrimaryColor(int color) {
        primaryColor = color;
        if (umc != null) umc.setPrimaryColor(color);
    }

}
