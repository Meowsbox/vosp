/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.View;

/**
 * Created by dhon on 3/21/2017.
 */

public class ColorPatch extends View {
    ColorDrawable cdPatch = new ColorDrawable(Color.RED);

    public ColorPatch(Context context) {
        super(context);
    }

    public ColorPatch(Context context, AttributeSet attrs) {
        super(context, attrs);
    }


    public ColorPatch(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Rect c = canvas.getClipBounds();
        cdPatch.setBounds(c.left+1,c.top+1,c.right-1,c.bottom-1);
        cdPatch.draw(canvas);
    }

    void setColor(int color) {
        cdPatch = new ColorDrawable(color);
        invalidate();
    }

}
