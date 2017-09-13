/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.meowsbox.vosp.R;

/**
 * TODO: document your custom view class.
 */
public class CompRowHeader extends RelativeLayout {
    TextView tvHeader = null;


    public CompRowHeader(Context context) {
        super(context);
        init(context, null, 0);
    }

    public CompRowHeader(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public CompRowHeader(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs, defStyle);
    }

    public String getValue() {
        return tvHeader.getText().toString();
    }

    public void setValue(String value) {
        tvHeader.setText(value);
    }

    public CompRowHeader setRowEnabled(boolean enabled) {
        tvHeader.setEnabled(enabled);
        return this;
    }

    private void init(Context context, AttributeSet attrs, int defStyle) {
        inflate(context, R.layout.compf_header_text, this);
        tvHeader = (TextView) findViewById(R.id.tvHeader);
    }

}
