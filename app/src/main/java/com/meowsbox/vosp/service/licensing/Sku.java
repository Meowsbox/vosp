/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.service.licensing;

import android.os.Bundle;

/**
 * A billing implementation independent class to hold details for purchasable object data.
 * Created by dhon on 8/2/2017.
 */
public class Sku {
    public String sku;
    public String price; // local
    public String title; // local
    public String desc; // local
    public String type;
    public String purchaseData;
    public String signature;
    public String subscriptionPeriod;

    public static Sku fromBundle(Bundle bundle) {
        final Sku sku = new Sku();
        sku.sku = bundle.getString("sku");
        sku.price = bundle.getString("price");
        sku.title = bundle.getString("title");
        sku.desc = bundle.getString("desc");
        sku.type = bundle.getString("type");
        sku.purchaseData = bundle.getString("purchaseData");
        sku.signature = bundle.getString("signature");
        sku.subscriptionPeriod = bundle.getString("subscriptionPeriod");
        return sku;
    }

    public Bundle toBundle() {
        final Bundle bundle = new Bundle();
        bundle.putString("sku", sku);
        bundle.putString("price", price);
        bundle.putString("title", title);
        bundle.putString("desc", desc);
        bundle.putString("type", type);
        bundle.putString("purchaseData", purchaseData);
        bundle.putString("signature", signature);
        bundle.putString("subscriptionPeriod", subscriptionPeriod);
        return bundle;
    }

}
