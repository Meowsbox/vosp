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
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.meowsbox.vosp.R;

/**
 * TODO: document your custom view class.
 */
public class CompRowEdit extends RelativeLayout {
    EditText etValue = null;
    TextView tvName = null;
    ImageButton ibHelp = null;
    ImageView ivPrem = null;
    IcompRowChangeListener changeListener = null;


    public CompRowEdit(Context context) {
        super(context);
        init(context, null, 0);
    }

    public CompRowEdit(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public CompRowEdit(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs, defStyle);
    }

    public CompRowEdit disableHelp(boolean enabled) {
        ibHelp.setEnabled(enabled);
        return this;
    }

    public EditText getEditText() {
        return etValue;
    }

    public String getValue() {
        return etValue.getText().toString();
    }

    public CompRowEdit setValue(String value) {
        etValue.setText(value);
        return this;
    }

    public CompRowEdit setChangeListener(final IcompRowChangeListener changeListener) {
        this.changeListener = changeListener;
        etValue.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (changeListener != null) changeListener.onChanged();
            }
        });
        return this;
    }

    public CompRowEdit setHelpDrawable(Drawable drawable) {
        ibHelp.setBackground(drawable);
        return this;
    }

    public CompRowEdit setHelpOnClick(OnClickListener helpOnClick) {
        ibHelp.setOnClickListener(helpOnClick);
        return this;
    }

    public CompRowEdit setHelpVisibility(int visibility) {
        ibHelp.setVisibility(VISIBLE);
        return this;
    }

    public void setEtEnabled(boolean isEnabled) {
        etValue.setFocusable(isEnabled);
        etValue.setClickable(isEnabled);
    }

    public void setInputType(int inputType) {
        etValue.setInputType(inputType);
    }

    public CompRowEdit setIsPremium(boolean isPremium) {
        ivPrem.setVisibility(isPremium ? VISIBLE : GONE);
        return this;
    }

    public void setName(String title) {
        tvName.setText(title);
    }

    public CompRowEdit setRowEnabled(boolean enabled) {
        etValue.setEnabled(enabled);
        return this;
    }

    public void setSecret() {
        etValue.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    }

    private void init(Context context, AttributeSet attrs, int defStyle) {
        inflate(context, R.layout.compf_two_line_ed_stack, this);
        etValue = (EditText) findViewById(R.id.etValue);
        tvName = (TextView) findViewById(R.id.tvName);
        ibHelp = (ImageButton) findViewById(R.id.ibHelp);
        ivPrem = (ImageView) findViewById(R.id.ivPrem);

        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                etValue.requestFocusFromTouch();
            }
        });
    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener l) {
        super.setOnClickListener(l);
        etValue.setOnClickListener(l);
        tvName.setOnClickListener(l);
    }
}
