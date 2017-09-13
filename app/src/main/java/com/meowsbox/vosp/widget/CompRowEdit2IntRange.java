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
public class CompRowEdit2IntRange extends RelativeLayout {
    EditText etValue = null;
    EditText etValue2 = null;
    TextView tvName = null;
    TextView tvRangeDelim = null;
    ImageButton ibHelp = null;
    ImageView ivPrem = null;
    int boundLower, boundUpper;
    int snapLower, snapUpper;
    boolean snapToRange = false;
    IcompRowChangeListener changeListener = null;

    public CompRowEdit2IntRange(Context context) {
        super(context);
        init(context, null, 0);
    }

    public CompRowEdit2IntRange(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public CompRowEdit2IntRange(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs, defStyle);
    }

    public CompRowEdit2IntRange disableHelp(boolean enabled) {
        ibHelp.setEnabled(enabled);
        return this;
    }

    public EditText getEditText() {
        return etValue;
    }

    public String getValue() {
        return etValue.getText().toString();
    }

    public CompRowEdit2IntRange setValue(String value) {
        if (snapToRange) {
            if (value == null || value.isEmpty()) {
                etValue.setText(String.valueOf(snapLower));
                return this;
            }

            try {
                int i = Integer.parseInt(value);
                if (i < boundLower || i > boundUpper) {
                    etValue.setText(String.valueOf(snapLower));
                    return this;
                }
            } catch (NumberFormatException e) {
                etValue.setText(String.valueOf(snapLower));
                return this;
            }
            etValue.setText(value);
        } else
            etValue.setText(value);
        return this;
    }

    public String getValue2() {
        return etValue2.getText().toString();
    }

    public CompRowEdit2IntRange setValue2(String value) {
        etValue2.setText(value);
        if (snapToRange) {
            if (value == null || value.isEmpty()) {
                etValue2.setText(String.valueOf(snapUpper));
                return this;
            }

            try {
                int i = Integer.parseInt(value);
                if (i < boundLower || i > boundUpper) {
                    etValue2.setText(String.valueOf(snapUpper));
                    return this;
                }
            } catch (NumberFormatException e) {
                etValue2.setText(String.valueOf(snapUpper));
                return this;
            }
            etValue2.setText(value);
        } else
            etValue2.setText(value);
        return this;
    }

    /**
     * Set the lower and upper include range for validation. Behavior is undefined when bounds are equal or inverted.
     *
     * @param lower
     * @param upper
     * @return
     */
    public CompRowEdit2IntRange setBoundRange(int lower, int upper) {
        boundLower = lower;
        boundUpper = upper;
        return this;
    }

    public CompRowEdit2IntRange setChangeListener(final IcompRowChangeListener changeListener) {
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
        etValue2.addTextChangedListener(textWatcher);
        return this;
    }

    public CompRowEdit2IntRange setHelpDrawable(Drawable drawable) {
        ibHelp.setBackground(drawable);
        return this;
    }

    public CompRowEdit2IntRange setHelpOnClick(OnClickListener helpOnClick) {
        ibHelp.setOnClickListener(helpOnClick);
        return this;
    }

    public CompRowEdit2IntRange setHelpVisibility(int visibility) {
        ibHelp.setVisibility(visibility);
        return this;
    }

    public void setInputType(int inputType) {
        etValue.setInputType(inputType);
        etValue2.setInputType(inputType);
    }

    public CompRowEdit2IntRange setIsPremium(boolean isPremium) {
        ivPrem.setVisibility(isPremium ? VISIBLE : GONE);
        return this;
    }

    public void setName(String title) {
        tvName.setText(title);
    }

    public void setRangeDelim(String string) {
        tvRangeDelim.setText(string);
    }

    public CompRowEdit2IntRange setRowEnabled(boolean enabled) {
        etValue.setEnabled(enabled);
        return this;
    }

    public CompRowEdit2IntRange setSecret() {
        etValue.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return this;
    }

    /**
     * Set the default values when user input contains values outside the range bounds.
     *
     * @param lower
     * @param upper
     * @return
     */
    public CompRowEdit2IntRange setSnapDefaults(int lower, int upper) {
        snapLower = lower;
        snapUpper = upper;
        return this;
    }

    /**
     * Enable or disable value range check and snap-to-values on loss of focus or when setValue is called.
     * Behavior is undefined if bounds and snap values have not yet been set or ranges are invalid.
     *
     * @param snapToRange
     * @return
     */
    public CompRowEdit2IntRange setSnapToRange(boolean snapToRange) {
        this.snapToRange = snapToRange;
        return this;
    }

    private void init(Context context, AttributeSet attrs, int defStyle) {
        inflate(context, R.layout.compf_two_line_ed_range_stack, this);
        etValue = (EditText) findViewById(R.id.etValue);
        etValue2 = (EditText) findViewById(R.id.etValue2);
        tvName = (TextView) findViewById(R.id.tvName);
        tvRangeDelim = (TextView) findViewById(R.id.tvRangeDelim);
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
                        setValue(String.valueOf(snapLower));
                        return;
                    }
                    int val;
                    try {
                        val = Integer.valueOf(sLower);
                        if (val < boundLower || val > boundUpper) { // out of range
                            setValue(String.valueOf(snapLower));
                        }
                    } catch (Exception e) { // not a valid number
                        setValue(String.valueOf(snapLower));
                        return;
                    }
                    // compare with upeer to confirm valid range
                    String sUpper = getValue2();
                    if (TextUtils.isEmpty(sUpper)) return;
                    try {
                        int val2 = Integer.valueOf(sUpper);
                        if (val > val2) { // inverted range
                            setValue(sUpper);
                            setValue2(sLower);
                        } else if (val == val2) { // not a range
                            setValue(String.valueOf(snapLower));
                            setValue2(String.valueOf(snapUpper));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        etValue2.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (snapToRange && !hasFocus) {
                    String sUpper = getValue2();
                    if (TextUtils.isEmpty(sUpper)) { // is empty or null
                        setValue2(String.valueOf(snapUpper));
                        return;
                    }
                    int val;
                    try {
                        val = Integer.valueOf(sUpper);
                        if (val < boundLower || val > boundUpper) { // out of range
                            setValue2(String.valueOf(snapUpper));
                        }
                    } catch (Exception e) { // not a valid number
                        setValue2(String.valueOf(snapUpper));
                        return;
                    }
//                     compare with lower to confirm valid range
                    String sLower = getValue();
                    if (TextUtils.isEmpty(sLower)) return;
                    try {
                        int val2 = Integer.valueOf(sLower);
                        if (val < val2) { // inverted range
                            setValue(sUpper);
                            setValue2(sLower);
                        } else if (val == val2) { // not a range
                            setValue(String.valueOf(snapLower));
                            setValue2(String.valueOf(snapUpper));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });

    }

}
