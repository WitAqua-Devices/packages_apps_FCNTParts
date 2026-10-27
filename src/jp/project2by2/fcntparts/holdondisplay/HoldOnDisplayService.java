package jp.project2by2.fcntparts.holdondisplay;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.app.KeyguardManager;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import jp.project2by2.fcntparts.Constants;

public class HoldOnDisplayService extends Service implements SensorEventListener {
    private static final String TAG = "FCNTHoldOnDisplay";
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private PowerManager mPowerManager;
    private KeyguardManager mKeyguardManager;
    private PowerManager.WakeLock mWakeLock;
    private Handler mHandler;
    private boolean mSensorRegistered;
    private boolean mSensorHeld;
    private boolean mHeld;

    private final Runnable mSensorRegistrationRetry = this::registerSensor;

    // The hold state only matters while the display is interactive. Listening
    // while the display is off or dozing would wake the AP on every grip change.
    private final BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                registerSensor();
            } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                unregisterSensor();
            } else if (Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
                // The sensor only reports changes, so a hold that started on the
                // keyguard must be re-evaluated once the device is unlocked.
                updateHeldState();
            }
        }
    };

    public static void updateState(Context context) {
        Intent intent = new Intent(context, HoldOnDisplayService.class);
        boolean enabled = Settings.Secure.getInt(context.getContentResolver(),
                Constants.KEY_HOLD_ON_DISPLAY_ENABLED, 0) != 0;
        if (enabled) {
            context.startService(intent);
        } else {
            context.stopService(intent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mSensorManager = getSystemService(SensorManager.class);
        mPowerManager = getSystemService(PowerManager.class);
        mKeyguardManager = getSystemService(KeyguardManager.class);
        mHandler = new Handler(Looper.getMainLooper());
        mWakeLock = mPowerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                TAG);
        mWakeLock.setReferenceCounted(false);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(mScreenStateReceiver, filter);

        if (mPowerManager.isInteractive()) {
            registerSensor();
        }
    }

    private void registerSensor() {
        if (mSensorRegistered || !mPowerManager.isInteractive()) return;

        // The AP is already awake while interactive, so the non-wakeup variant
        // is sufficient.
        mSensor = mSensorManager.getDefaultSensor(
                Constants.FCNT_HOLD_ON_DISPLAY_SENSOR_TYPE, false);
        if (mSensor == null || !mSensorManager.registerListener(
                this, mSensor, SensorManager.SENSOR_DELAY_NORMAL)) {
            Log.w(TAG, "HoldOnDisplay sensor is not ready; retrying");
            mHandler.removeCallbacks(mSensorRegistrationRetry);
            mHandler.postDelayed(mSensorRegistrationRetry, 1000);
            return;
        }
        mSensorRegistered = true;
    }

    private void unregisterSensor() {
        mHandler.removeCallbacks(mSensorRegistrationRetry);
        if (mSensorRegistered) {
            mSensorManager.unregisterListener(this);
            mSensorRegistered = false;
        }
        mSensorHeld = false;
        mHeld = false;
        if (mWakeLock.isHeld()) mWakeLock.release();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // FCNT HAL: 1 = held and 0 = released.
        mSensorHeld = event.values.length > 0 && event.values[0] >= 0.5f;
        updateHeldState();
    }

    private void updateHeldState() {
        // Do not wake or keep the display on from movement while the display is
        // off or the device is still locked.
        final boolean held = mSensorHeld && mPowerManager.isInteractive()
                && !mKeyguardManager.isKeyguardLocked();
        if (held == mHeld) return;
        mHeld = held;

        if (held) {
            // ACQUIRE_CAUSES_WAKEUP is not sufficient while the device is dozing on
            // newer PowerManager implementations. Explicitly leave doze on the
            // transition, then keep the display bright with the wake lock.
            mPowerManager.wakeUp(SystemClock.uptimeMillis(),
                    PowerManager.WAKE_REASON_GESTURE, TAG);
            if (!mWakeLock.isHeld()) mWakeLock.acquire();
        } else if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onDestroy() {
        unregisterReceiver(mScreenStateReceiver);
        unregisterSensor();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
