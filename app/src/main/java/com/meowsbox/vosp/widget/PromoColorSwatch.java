/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.graphics.drawable.shapes.Shape;
import android.support.annotation.AttrRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StyleRes;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.meowsbox.vosp.R;

/**
 * Created by dhon on 7/31/2017.
 */

public class PromoColorSwatch extends FrameLayout {
    public final String TAG = this.getClass().getName();
    AttributeSet attrs;
    int defStyleAttr;

    public PromoColorSwatch(@NonNull Context context) {
        super(context);
        init(context, null, 0);

    }

    public PromoColorSwatch(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);


    }

    public PromoColorSwatch(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    public PromoColorSwatch(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr);
    }

    private void init(final Context context, final AttributeSet attrs, int defStyle) {
        this.attrs = attrs;
        this.defStyleAttr = defStyle;

        final int[] pc = context.getResources().getIntArray(R.array.primary_color_palette);

        final TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.PromoColorSwatch);
        final int rs = ta.getInt(R.styleable.PromoColorSwatch_row_size, pc.length);
        ta.recycle();


        addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                                final int sw = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 50, getResources().getDisplayMetrics());
                final int vw = getWidth() - getPaddingEnd() - getPaddingStart();
                final int vh = getHeight() - getPaddingBottom() - getPaddingTop();
                final int iw = vw / (pc.length > rs ? rs : pc.length) * 2;
                final int ih = iw;

                int vp = 0;
                if (vh > iw) {
                    vp = (vh - ih) / 2;
                }
                final int spc = (vw - (iw / 2)) / (pc.length > rs ? rs : pc.length);

                for (int i = 0; i < pc.length; i++) {
                    ImageView iv = new ImageView(context);
                    final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(iw, ih);
                    lp.setMargins(i % rs * spc, i / rs * spc, 0, 0);
                    iv.setLayoutParams(lp);
                    iv.setImageResource(R.drawable.round_50dp_trans);
                    final ShapeDrawable s = new ShapeDrawable(new OvalShape());
                    s.setIntrinsicWidth(sw);
                    s.setIntrinsicHeight(sw);
                    s.setTint(pc[i]);
                    iv.setBackground(s);

                    addView(iv);
                }
            }
        });
    }
}
