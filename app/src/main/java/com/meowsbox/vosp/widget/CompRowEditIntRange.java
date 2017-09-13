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
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
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
public class CompRowEditIntRange extends RelativeLayout {
    EditText etValue = null;
    TextView tvName = null;
    ImageButton ibHelp = null;
    ImageView ivPrem = null;
    int boundLower, boundUpper;
    int snapValue;
    boolean snapToRange = false;
    IcompRowChangeListener changeListener = null;

    public CompRowEditIntRange(Context context) {
        super(context);
        init(context, null, 0);
    }

    public CompRowEditIntRange(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public CompRowEditIntRange(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs, defStyle);
    }

    public CompRowEditIntRange disableHelp(boolean enabled) {
        ibHelp.setEnabled(enabled);
        return this;
    }

    public EditText getEditText() {
        return etValue;
    }

    public String getValue() {
        return etValue.getText().toString();
    }

    public CompRowEditIntRange setValue(String value) {
        if (snapToRange) {
            if (value == null || value.isEmpty()) {
                etValue.setText(String.valueOf(snapValue));
                return this;
            }
            try {
                int i = Integer.parseInt(value);
                if (i < boundLower || i > boundUpper) {
                    etValue.setText(String.valueOf(snapValue));
                    return this;
                }
            } catch (NumberFormatException e) {
                etValue.setText(String.valueOf(snapValue));
                return this;
            }
            etValue.setText(value);
        } else
            etValue.setText(value);
        return this;
    }

    /**
     * Set the lower and upper include range for validation. Behavior is undefined when bounds are equal or inverted.
     *
     * @param lower
     * @param upper
     * @return
     */
    public CompRowEditIntRange setBoundRange(int lower, int upper) {
        boundLower = lower;
        boundUpper = upper;
        return this;
    }

    public CompRowEditIntRange setChangeListener(final IcompRowChangeListener changeListener) {
        this.changeListener = changeListener;
        TextWatcher textWatcher = new TextWatcher() {
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
        };
        etValue.addTextChangedListener(textWatcher);
        return this;
    }

    public CompRowEditIntRange setHelpDrawable(Drawable drawable) {
        ibHelp.setBackground(drawable);
        return this;
    }

    public CompRowEditIntRange setHelpOnClick(OnClickListener helpOnClick) {
        ibHelp.setOnClickListener(helpOnClick);
        return this;
    }

    public CompRowEditIntRange setHelpVisibility(int visibility) {
        ibHelp.setVisibility(VISIBLE);
        return this;
    }

    public CompRowEditIntRange setInputType(int inputType) {
        etValue.setInputType(inputType);
        return this;
    }

    public CompRowEditIntRange setIsPremium(boolean isPremium) {
        ivPrem.setVisibility(isPremium ? VISIBLE : GONE);
        return this;
    }

    public CompRowEditIntRange setName(String title) {
        tvName.setText(title);
        return this;
    }

    public CompRowEditIntRange setRowEnabled(boolean enabled) {
        etValue.setEnabled(enabled);
        return this;
    }

    public CompRowEditIntRange setSecret() {
        etValue.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return this;
    }

    /**
     * Set the default values when user input contains values outside the range bounds.
     *
     * @param snapValue
     * @return
     */
    public CompRowEditIntRange setSnapDefaults(int snapValue) {
        this.snapValue = snapValue;
        return this;
    }

    /**
     * Enable or disable value range check and snap-to-values on loss of focus or when setValue is called.
     * Behavior is undefined if bounds and snap values have not yet been set or ranges are invalid.
     *
     * @param snapToRange
     * @return
     */
    public CompRowEditIntRange setSnapToRange(boolean snapToRange) {
        this.snapToRange = snapToRange;
        return this;
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

        etValue.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (snapToRange && !hasFocus) {
                    String sLower = getValue();
                    if (TextUtils.isEmpty(sLower)) { // is empty or null
                        setValue(String.valueOf(snapValue));
                        return;
                    }
                    int val;
                    try {
                        val = Integer.valueOf(sLower);
                        if (val < boundLower || val > boundUpper) { // out of range
                            setValue(String.valueOf(snapValue));
                        }
                    } catch (Exception e) { // not a valid number
                        setValue(String.valueOf(snapValue));
                        return;
                    }
                }
            }
        });


    }

}
