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
public class CompRowEditIbIntRange extends RelativeLayout {
    EditText etValue;
    TextView tvName;
    ImageButton ibHelp, ibDec, ibInc;
    ImageView ivPrem;
    int boundLower, boundUpper;
    int snapValue;
    boolean snapToRange = false;
    boolean enabled = true;
    IcompRowChangeListener changeListener = null;

    public CompRowEditIbIntRange(Context context) {
        super(context);
        init(context, null, 0);
    }

    public CompRowEditIbIntRange(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public CompRowEditIbIntRange(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs, defStyle);
    }

    public CompRowEditIbIntRange disableHelp(boolean enabled) {
        ibHelp.setEnabled(enabled);
        return this;
    }

    public EditText getEditText() {
        return etValue;
    }

    public String getValue() {
        return etValue.getText().toString();
    }

    public CompRowEditIbIntRange setValue(String value) {
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
    public CompRowEditIbIntRange setBoundRange(int lower, int upper) {
        boundLower = lower;
        boundUpper = upper;
        return this;
    }

    public CompRowEditIbIntRange setChangeListener(final IcompRowChangeListener changeListener) {
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

    public CompRowEditIbIntRange setHelpDrawable(Drawable drawable) {
        ibHelp.setBackground(drawable);
        return this;
    }

    public CompRowEditIbIntRange setHelpOnClick(OnClickListener helpOnClick) {
        ibHelp.setOnClickListener(helpOnClick);
        return this;
    }

    public CompRowEditIbIntRange setHelpVisibility(int visibility) {
        ibHelp.setVisibility(VISIBLE);
        return this;
    }

    public CompRowEditIbIntRange setInputType(int inputType) {
        etValue.setInputType(inputType);
        return this;
    }

    public CompRowEditIbIntRange setIsPremium(boolean isPremium) {
        ivPrem.setVisibility(isPremium ? VISIBLE : GONE);
        return this;
    }

    public CompRowEditIbIntRange setName(String title) {
        tvName.setText(title);
        return this;
    }

    public CompRowEditIbIntRange setRowEnabled(boolean enabled) {
        this.enabled = enabled;
        etValue.setEnabled(enabled);
        ibDec.setEnabled(enabled);
        ibInc.setEnabled(enabled);
        return this;
    }

    public CompRowEditIbIntRange setSecret() {
        etValue.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return this;
    }

    /**
     * Set the default values when user input contains values outside the range bounds.
     *
     * @param snapValue
     * @return
     */
    public CompRowEditIbIntRange setSnapDefaults(int snapValue) {
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
    public CompRowEditIbIntRange setSnapToRange(boolean snapToRange) {
        this.snapToRange = snapToRange;
        return this;
    }


    private void init(Context context, AttributeSet attrs, int defStyle) {
        inflate(context, R.layout.compf_two_line_ed_ib_stack, this);
        etValue = (EditText) findViewById(R.id.etValue);
        tvName = (TextView) findViewById(R.id.tvName);
        ibHelp = (ImageButton) findViewById(R.id.ibHelp);
        ibDec = (ImageButton) findViewById(R.id.ibDec);
        ibInc = (ImageButton) findViewById(R.id.ibInc);
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

        ibDec.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String value = getValue();
                if (value.isEmpty()) {
                    setValue(String.valueOf(boundLower));
                    return;
                }
                try {
                    int i = Integer.valueOf(value);
                    if (i-- < boundLower) return;
                    setValue(String.valueOf(i--));
                } catch (NumberFormatException e) {
                    setValue(String.valueOf(boundLower));
                    return;
                }
            }
        });
        ibInc.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String value = getValue();
                if (value.isEmpty()) {
                    setValue(String.valueOf(boundUpper));
                    return;
                }
                try {
                    int i = Integer.valueOf(value);
                    if (i++ > boundUpper) return;
                    setValue(String.valueOf(i++));
                } catch (NumberFormatException e) {
                    setValue(String.valueOf(boundUpper));
                    return;
                }
            }
        });


    }

}
