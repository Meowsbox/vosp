/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.widget;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.meowsbox.vosp.R;

/**
 * TODO: document your custom view class.
 */
public class CompRowColor extends RelativeLayout {
    TextView tvName = null;
    ColorPatch vColor = null;
    ImageView ivPrem = null;
    private int color = Color.DKGRAY;


    public CompRowColor(Context context) {
        super(context);
        init(context, null, 0);
    }

    public CompRowColor(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }


    public CompRowColor(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs, defStyle);
    }

    public int getValue() {
        return color;
    }

    public void setValue(int color) {
        this.color = color;
        vColor.setColor(color);
        invalidate();
    }

    public CompRowColor setRowEnabled(boolean isEnabled) {
        tvName.setEnabled(isEnabled);
        vColor.setEnabled(isEnabled);
        return this;
    }

    public CompRowColor setIsPremium(boolean isPremium) {
        ivPrem.setVisibility(isPremium ? VISIBLE : GONE);
        return this;
    }

    public CompRowColor setName(String title) {
        tvName.setText(title);
        return this;
    }

    private void init(Context context, AttributeSet attrs, int defStyle) {
        inflate(context, R.layout.compf_line_color, this);
        tvName = (TextView) findViewById(R.id.tvName);
        vColor = (ColorPatch) findViewById(R.id.vColor);
        ivPrem = (ImageView) findViewById(R.id.ivPrem);

//        setOnClickListener(new OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                sw.requestFocusFromTouch();
//            }
//        });
    }

}
