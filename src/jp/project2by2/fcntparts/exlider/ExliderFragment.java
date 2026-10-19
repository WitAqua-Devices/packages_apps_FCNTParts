/*
 * Copyright (C) 2025 The 2by2 Project
 * SPDX-License-Identifier: Apache-2.0
 */

package jp.project2by2.fcntparts.exlider;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.android.settingslib.widget.MainSwitchPreference;

import java.util.LinkedHashSet;
import java.util.Set;

import jp.project2by2.fcntparts.Constants;
import jp.project2by2.fcntparts.R;

public class ExliderFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "Exlider";
    private static final ComponentName EXLIDER_SERVICE = new ComponentName(
            "jp.project2by2.fcntparts",
            "jp.project2by2.fcntparts.exlider.ExliderService");

    private MainSwitchPreference mExliderEnableSwitch;
    private ExliderSpeedPreference mScrollSpeedPreference;
    private ContentResolver mContentResolver;
    private boolean mObserverRegistered;

    private final ContentObserver mAccessibilityObserver = new ContentObserver(
            new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            syncAccessibilitySwitch();
        }
    };

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        mContentResolver = requireContext().getContentResolver();
        setPreferencesFromResource(R.xml.exlider, rootKey);

        mExliderEnableSwitch = (MainSwitchPreference) findPreference("exlider");
        // The system accessibility setting is the single source of truth.
        mExliderEnableSwitch.setPersistent(false);
        mExliderEnableSwitch.setOnPreferenceChangeListener(this);

        mScrollSpeedPreference = (ExliderSpeedPreference)
                findPreference(Constants.KEY_EXLIDER_SCROLL_SPEED);
        mScrollSpeedPreference.setOnPreferenceChangeListener(this);
        float speed = Settings.Secure.getFloat(
                mContentResolver,
                Constants.KEY_EXLIDER_SCROLL_SPEED, 10.0f);
        mScrollSpeedPreference.refresh(Math.round(Math.max(1.0f, Math.min(10.0f, speed)) * 10f));
        syncAccessibilitySwitch();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!mObserverRegistered) {
            mContentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                    false, mAccessibilityObserver);
            mContentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED),
                    false, mAccessibilityObserver);
            mObserverRegistered = true;
        }
        syncAccessibilitySwitch();
    }

    @Override
    public void onResume() {
        super.onResume();
        syncAccessibilitySwitch();
    }

    @Override
    public void onStop() {
        if (mObserverRegistered) {
            mContentResolver.unregisterContentObserver(mAccessibilityObserver);
            mObserverRegistered = false;
        }
        super.onStop();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mExliderEnableSwitch) {
            boolean enabled = (Boolean) newValue;
            boolean changed = setExliderAccessibilityEnabled(enabled);
            if (!changed) syncAccessibilitySwitch();
            return changed;
        }
        if (preference == mScrollSpeedPreference) {
            float speed = ((Integer) newValue) / 10.0f;
            return Settings.Secure.putFloat(mContentResolver, preference.getKey(), speed);
        }
        return false;
    }

    private void syncAccessibilitySwitch() {
        if (mExliderEnableSwitch != null) {
            mExliderEnableSwitch.setChecked(isExliderAccessibilityEnabled());
        }
    }

    private boolean isExliderAccessibilityEnabled() {
        if (Settings.Secure.getInt(
                mContentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) {
            return false;
        }
        for (String service : getEnabledAccessibilityServices()) {
            ComponentName component = ComponentName.unflattenFromString(service);
            if (EXLIDER_SERVICE.equals(component)) return true;
        }
        return false;
    }

    private boolean setExliderAccessibilityEnabled(boolean enabled) {
        Set<String> services = getEnabledAccessibilityServices();
        services.removeIf(service -> EXLIDER_SERVICE.equals(
                ComponentName.unflattenFromString(service)));
        if (enabled) services.add(EXLIDER_SERVICE.flattenToString());

        boolean stored = Settings.Secure.putString(
                mContentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                TextUtils.join(":", services));
        if (!stored) {
            Log.e(TAG, "Failed to update enabled accessibility services");
            return false;
        }

        if (enabled) {
            return Settings.Secure.putInt(
                    mContentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        }
        if (services.isEmpty()) {
            return Settings.Secure.putInt(
                    mContentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0);
        }
        return true;
    }

    private Set<String> getEnabledAccessibilityServices() {
        Set<String> services = new LinkedHashSet<>();
        String setting = Settings.Secure.getString(
                mContentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(setting)) return services;

        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(setting);
        for (String service : splitter) {
            if (!TextUtils.isEmpty(service)) services.add(service);
        }
        return services;
    }

    public static boolean isExliderSupported(Context context) {
        return context.getResources().getBoolean(R.bool.config_exliderSupported);
    }
}
