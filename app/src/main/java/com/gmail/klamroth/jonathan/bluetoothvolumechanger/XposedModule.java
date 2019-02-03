package com.gmail.klamroth.jonathan.bluetoothvolumechanger;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import java.lang.ref.WeakReference;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookConstructor;
import static de.robv.android.xposed.XposedHelpers.getBooleanField;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.getStaticIntField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;


public class XposedModule implements IXposedHookLoadPackage {

    private static final String ACTION_CHANGE_BT_VOLUME = "com.gmail.klamroth.jonathan.bluetoothvolumechanger.ACTION_CHANGE_BT_VOLUME";
    private static final String EXTRA_VOLUME = "com.gmail.klamroth.jonathan.bluetoothvolumechanger.EXTRA_VOLUME";


    @Override
    public void handleLoadPackage (XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"android".equals(lpparam.packageName))
            return;


        final ClassLoader cl = lpparam.classLoader;
        findAndHookConstructor("com.android.server.audio.AudioService", cl, Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod (MethodHookParam param) {
                Context context = (Context) param.args[0];
                context.registerReceiver(new CommandReceiver(param.thisObject), new IntentFilter(ACTION_CHANGE_BT_VOLUME));
            }
        });
    }


    private static int[] getIntArrayField (Object obj, String fieldName) {
        return (int[]) getObjectField(obj, fieldName);
    }

    private static int getIntArrayFieldElement (Object obj, String fieldName, int arrayIndex) {
        return getIntArrayField(obj, fieldName)[arrayIndex];
    }

    private static Object[] getObjectArrayField (Object obj, String fieldName) {
        return (Object[]) getObjectField(obj, fieldName);
    }

    private static Object getObjectArrayFieldElement (Object obj, String fieldName, int arrayIndex) {
        return getObjectArrayField(obj, fieldName)[arrayIndex];
    }

    private void setBluetoothVolume (Object thiz, int index, int flags) {
        setStreamVolume(thiz, AudioSystem.STREAM_MUSIC, AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP, index, flags, "BtVolChanger");
    }

    private void setStreamVolume (Object thiz, int streamType, int device, int index, int flags, String caller) {
        if (getBooleanField(thiz, "mUseFixedVolume")) {
            return;
        }

        callMethod(thiz, "ensureValidStreamType", streamType);
        int streamTypeAlias = getIntArrayFieldElement(thiz, "mStreamVolumeAlias", streamType);
        Object /* VolumeStreamState */ streamState = getObjectArrayFieldElement(thiz, "mStreamStates", streamTypeAlias);

        //final int device = getDeviceForStream(streamType);
        int oldIndex;

        // skip a2dp absolute volume control request when the device
        // is not an a2dp device
        if ((device & AudioSystem.DEVICE_OUT_ALL_A2DP) == 0 &&
                (flags & AudioManager.FLAG_BLUETOOTH_ABS_VOLUME) != 0) {
            return;
        }
        /*
        // If we are being called by the system (e.g. hardware keys) check for current user
        // so we handle user restrictions correctly.
        if (uid == android.os.Process.SYSTEM_UID) {
            uid = UserHandle.getUid(getCurrentUserId(), UserHandle.getAppId(uid));
        }
        if (mAppOps.noteOp(STREAM_VOLUME_OPS[streamTypeAlias], uid, callingPackage)
                != AppOpsManager.MODE_ALLOWED) {
            return;
        }

        if (isAndroidNPlus(callingPackage)
                && wouldToggleZenMode(getNewRingerMode(streamTypeAlias, index, flags))
                && !mNm.isNotificationPolicyAccessGrantedForPackage(callingPackage)) {
            throw new SecurityException("Not allowed to change Do Not Disturb state");
        }

        if (!volumeAdjustmentAllowedByDnd(streamTypeAlias, flags)) {
            return;
        }
        */

        synchronized (getObjectField(thiz, "mSafeMediaVolumeState")) {
            // reset any pending volume command
            setObjectField(thiz, "mPendingVolumeCommand", null);

            oldIndex = (int) callMethod(streamState, "getIndex", device);

            index = (int) callMethod(thiz, "rescaleIndex", index * 10, streamType, streamTypeAlias);

            /*
            if (streamTypeAlias == AudioSystem.STREAM_MUSIC &&
                    (device & AudioSystem.DEVICE_OUT_ALL_A2DP) != 0 &&
                    (flags & AudioManager.FLAG_BLUETOOTH_ABS_VOLUME) == 0) {
                synchronized (mA2dpAvrcpLock) {
                    if (mA2dp != null && mAvrcpAbsVolSupported) {
                        mA2dp.setAvrcpAbsoluteVolume(index / 10);
                    }
                }
            }
            */

            if ((device & AudioSystem.DEVICE_OUT_HEARING_AID) != 0) {
                callMethod(thiz, "setHearingAidVolume", index, streamState);
            }

            if (streamTypeAlias == AudioSystem.STREAM_MUSIC) {
                int streamMaxVolume = (int) callMethod(thiz, "getStreamMaxVolume", streamType);
                callMethod(thiz, "setSystemAudioVolume", oldIndex, index, streamMaxVolume, flags);
            }

            flags &= ~AudioManager.FLAG_FIXED_VOLUME;
            if ((streamTypeAlias == AudioSystem.STREAM_MUSIC) &&
                    ((device & getIntField(thiz, "mFixedVolumeDevices")) != 0)) {
                flags |= AudioManager.FLAG_FIXED_VOLUME;

                // volume is either 0 or max allowed for fixed volume devices
                if (index != 0) {
                    if ((int) getObjectField(thiz, "mSafeMediaVolumeState") == getStaticIntField(thiz.getClass(), "SAFE_MEDIA_VOLUME_ACTIVE") &&
                            (device & getIntField(thiz, "mSafeMediaVolumeDevices")) != 0) {
                        index = (int) callMethod(thiz, "safeMediaVolumeIndex", device);
                    } else {
                        index = (int) callMethod(streamState, "getMaxIndex");
                    }
                }
            }

            /*
            if (!checkSafeMediaVolume(streamTypeAlias, index, device)) {
                mVolumeController.postDisplaySafeVolumeWarning(flags);
                mPendingVolumeCommand = new StreamVolumeCommand(
                        streamType, index, flags, device);
            } else {
                onSetStreamVolume(streamType, index, flags, device, caller);
                index = mStreamStates[streamType].getIndex(device);
            }
            */

            callMethod(thiz, "onSetStreamVolume", streamType, index, flags, device, caller);
            Object /* VolumeStreamState */ tmp = getObjectArrayFieldElement(thiz, "mStreamStates", streamType);
            index = (int) callMethod(tmp, "getIndex", device);
        }
        callMethod(thiz, "sendVolumeUpdate", streamType, oldIndex, index, flags);
    }


    private static class AudioSystem {

        public static final int STREAM_MUSIC = 3;

        public static final int DEVICE_OUT_BLUETOOTH_A2DP = 0x80;
        public static final int DEVICE_OUT_BLUETOOTH_A2DP_HEADPHONES = 0x100;
        public static final int DEVICE_OUT_BLUETOOTH_A2DP_SPEAKER = 0x200;
        public static final int DEVICE_OUT_HEARING_AID = 0x8000000;

        public static final int DEVICE_OUT_ALL_A2DP = (DEVICE_OUT_BLUETOOTH_A2DP |
                DEVICE_OUT_BLUETOOTH_A2DP_HEADPHONES |
                DEVICE_OUT_BLUETOOTH_A2DP_SPEAKER);

    }

    private static class AudioManager {

        public static final int FLAG_FIXED_VOLUME = 1<<5;
        public static final int FLAG_BLUETOOTH_ABS_VOLUME = 1<<6;

    }


    private class CommandReceiver extends BroadcastReceiver {

        private final WeakReference<Object> audioSerivceRef;


        public CommandReceiver (Object audioService) {
            audioSerivceRef = new WeakReference<>(audioService);
        }


        @Override
        public void onReceive (Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_CHANGE_BT_VOLUME.equals(action)) {
                int volume = intent.getIntExtra(EXTRA_VOLUME, -1);
                if (volume < 0 || volume > 15)
                    return;

                Object audioService = audioSerivceRef.get();
                if (audioService == null) {
                    XposedBridge.log("BluetoothVolumeChanger: audio service object is null");
                    return;
                }

                try {
                    // catch everything -> prevents crash of zygote if this method call would throw an unhandled exception
                    setBluetoothVolume(audioService, volume, 0);
                } catch (Exception e) {
                    XposedBridge.log(e);
                }
            }
        }

    }

}
