/*
 * Copyright (C) 2023-2024 The Evolution X Project
 * SPDX-License-Identifier: Apache-2.0
 */

package jp.project2by2.fcntparts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import jp.project2by2.fcntparts.utils.ComponentUtils;
import jp.project2by2.fcntparts.utils.FileUtils;

import jp.project2by2.fcntparts.actionkey.ActionKeyActivity;
import jp.project2by2.fcntparts.actionkey.ActionKeyFragment;
import jp.project2by2.fcntparts.exlider.ExliderActivity;
import jp.project2by2.fcntparts.exlider.ExliderFragment;
import jp.project2by2.fcntparts.holdondisplay.HoldOnDisplayService;

public class Startup extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        // Action Key
        ComponentUtils.toggleComponent(context, ActionKeyActivity.class, ActionKeyFragment.isActionKeySupported(context));

        // Exlider
        ComponentUtils.toggleComponent(context, ExliderActivity.class, ExliderFragment.isExliderSupported(context));

        HoldOnDisplayService.updateState(context);

        final String action = intent.getAction();
    }
}
