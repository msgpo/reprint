package com.github.ajalt.reprint.module_spass;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v4.os.CancellationSignal;
import android.util.Log;

import com.github.ajalt.library.AuthenticationListener;
import com.github.ajalt.library.ReprintModule;
import com.samsung.android.sdk.pass.Spass;
import com.samsung.android.sdk.pass.SpassFingerprint;

public class SpassReprintModule implements ReprintModule {
    public static final int TAG = 2;

    /**
     * The sensor was unable to read the finger.
     */
    public static final int STATUS_SENSOR_FAILED = SpassFingerprint.STATUS_SENSOR_FAILED;

    /**
     * The reader was unable to determine the finger.
     */
    public static final int STATUS_QUALITY_FAILED = SpassFingerprint.STATUS_QUALITY_FAILED;

    /**
     * A fingerprint was read that is not registered.
     */
    public static final int STATUS_AUTHENTICATION_FAILED = SpassFingerprint.STATUS_AUTHENTIFICATION_FAILED;

    /**
     * An authentication attempt was started without any fingerprints being registered.
     */
    public static final int STATUS_NO_REGISTERED_FINGERPRINTS = 1001;

    /**
     * There was an error in the fingerprint reader hardware.
     */
    public static final int STATUS_HW_UNAVAILABLE = 1002;

    private final Context context;

    @Nullable
    private final Spass spass;

    @Nullable
    private SpassFingerprint spassFingerprint;

    public SpassReprintModule(Context context) {
        this.context = context.getApplicationContext();

        Spass s;
        try {
            s = new Spass();
            s.initialize(context);
        } catch (Exception e) {
            s = null;
        }
        spass = s;
    }

    @Override
    public int tag() {
        return TAG;
    }

    @Override
    public boolean isHardwarePresent() {
        try {
            return spass != null && spass.isFeatureEnabled(Spass.DEVICE_FINGERPRINT);
        } catch (Exception ignored) {
            return false;
        }
    }


    @Override
    public boolean hasFingerprintRegistered() {
        try {
            if (isHardwarePresent()) {
                if (spassFingerprint == null) {
                    spassFingerprint = new SpassFingerprint(context);
                }
                return spassFingerprint.hasRegisteredFinger();
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public void authenticate(final AuthenticationListener listener, final CancellationSignal cancellationSignal) {
        if (spassFingerprint == null) {
            spassFingerprint = new SpassFingerprint(context);
        }
        try {
            if (!spassFingerprint.hasRegisteredFinger()) {
                listener.onFailure(TAG, STATUS_NO_REGISTERED_FINGERPRINTS, null);
                return;
            }
        } catch (Throwable ignored) {
            listener.onFailure(TAG, STATUS_HW_UNAVAILABLE, null);
            return;
        }

        cancelFingerprintRequest(spassFingerprint);

        try {
            spassFingerprint.startIdentify(new SpassFingerprint.IdentifyListener() {
                @Override
                public void onFinished(int status) {
                    if (BuildConfig.DEBUG) Log.d("SpassReprintModule",
                            "Fingerprint event status: " + eventStatusName(status));
                    switch (status) {
                        case SpassFingerprint.STATUS_AUTHENTIFICATION_SUCCESS:
                        case SpassFingerprint.STATUS_AUTHENTIFICATION_PASSWORD_SUCCESS:
                            listener.onSuccess();
                            return;
                        case SpassFingerprint.STATUS_QUALITY_FAILED:
                        case SpassFingerprint.STATUS_SENSOR_FAILED:
                        case SpassFingerprint.STATUS_AUTHENTIFICATION_FAILED:
                            listener.onFailure(TAG, status, null);
                            break;
                        case SpassFingerprint.STATUS_TIMEOUT_FAILED:
                            // Spass will time out the fingerprint request after some unspecified amount of
                            // time (a few tens of seconds), so just restart it right away if that happens.
                            authenticate(listener, cancellationSignal);
                            break;
                        default:
                            break;
                    }
                }

                @Override
                public void onReady() {

                }

                @Override
                public void onStarted() {

                }
            });
        } catch (Throwable t) {
            if (BuildConfig.DEBUG) Log.e("SpassReprintModule",
                    "fingerprint identification would not start", t);
            listener.onFailure(TAG, STATUS_HW_UNAVAILABLE, null);
            return;
        }

        cancellationSignal.setOnCancelListener(new CancellationSignal.OnCancelListener() {
            @Override
            public void onCancel() {

            }
        });
    }

    private static String eventStatusName(int status) {
        switch (status) {
            case SpassFingerprint.STATUS_AUTHENTIFICATION_SUCCESS:
                return "STATUS_AUTHENTIFICATION_SUCCESS";
            case SpassFingerprint.STATUS_AUTHENTIFICATION_PASSWORD_SUCCESS:
                return "STATUS_AUTHENTIFICATION_PASSWORD_SUCCESS";
            case SpassFingerprint.STATUS_TIMEOUT_FAILED:
                return "STATUS_TIMEOUT";
            case SpassFingerprint.STATUS_SENSOR_FAILED:
                return "STATUS_SENSOR_ERROR";
            case SpassFingerprint.STATUS_USER_CANCELLED:
                return "STATUS_USER_CANCELLED";
            case SpassFingerprint.STATUS_QUALITY_FAILED:
                return "STATUS_QUALITY_FAILED";
            case SpassFingerprint.STATUS_USER_CANCELLED_BY_TOUCH_OUTSIDE:
                return "STATUS_USER_CANCELLED_BY_TOUCH_OUTSIDE";
            case SpassFingerprint.STATUS_AUTHENTIFICATION_FAILED:
                return "STATUS_AUTHENTIFICATION_FAILED";
            default:
                return "invalid_status_value";
        }
    }


    private static void cancelFingerprintRequest(SpassFingerprint spassFingerprint) {
        try {
            spassFingerprint.cancelIdentify();
        } catch (Throwable t) {
            // There's no way to query if there's an active identify request,
            // so just try to cancel and ignore any exceptions.
        }
    }
}
