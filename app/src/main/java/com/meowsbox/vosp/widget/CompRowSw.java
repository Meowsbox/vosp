/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.meowsbox.vosp.R;

/**
 * TODO: document your custom view class.
 */
public class CompRowSw extends RelativeLayout {
    View parentView = null;
    TextView tvName = null;
    Switch sw = null;
    ImageButton ibHelp = null;
    ImageView ivPrem = null;
    IcompRowChangeListener changeListener = null;

    public CompRowSw(Context context) {
        super(context);
        init(context, null, 0);
    }

    public CompRowSw(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public CompRowSw(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs, defStyle);
    }

    public CompRowSw disableHelp(boolean enabled) {
        ibHelp.setEnabled(enabled);
        return this;
    }

    public boolean getValue() {
        return sw.isChecked();
    }

    public CompRowSw setValue(boolean isChecked) {
        sw.setChecked(isChecked);
        return this;
    }

    public CompRowSw setChangeListener(final IcompRowChangeListener changeListener) {
        this.changeListener = changeListener;
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (changeListener != null) changeListener.onChanged();
            }
        });
        return this;
    }

    public CompRowSw setHelpDrawable(Drawable drawable) {
        ibHelp.setBackground(drawable);
        return this;
    }

    public CompRowSw setHelpOnClick(OnClickListener helpOnClick) {
        ibHelp.setOnClickListener(helpOnClick);
        return this;
    }

    public CompRowSw setHelpVisibility(int visibility) {
        ibHelp.setVisibility(visibility);
        return this;
    }

    public CompRowSw setIsPremium(boolean isPremium) {
        ivPrem.setVisibility(isPremium ? VISIBLE : GONE);
        return this;
    }

    public void setName(String title) {
        tvName.setText(title);
    }

    public void setRowEnabled(boolean isEnabled) {
        tvName.setEnabled(isEnabled);
        sw.setEnabled(isEnabled);
    }

    private void init(Context context, AttributeSet attrs, int defStyle) {
        parentView = inflate(context, R.layout.compf_line_sw, this);
        tvName = (TextView) findViewById(R.id.tvName);
        sw = ((Switch) findViewById(R.id.sw));
        ibHelp = (ImageButton) findViewById(R.id.ibHelp);
        ivPrem = (ImageView) findViewById(R.id.ivPrem);
//        setOnClickListener(new OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                sw.requestFocusFromTouch();
//            }
//        });
    }


}
