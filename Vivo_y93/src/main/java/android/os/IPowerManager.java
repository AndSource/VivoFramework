package android.os;

public interface IPowerManager extends IInterface {

    public static abstract class Stub extends Binder implements IPowerManager {
        private static final String DESCRIPTOR = "android.os.IPowerManager";
        static final int TRANSACTION_acquireWakeLock = 1;
        static final int TRANSACTION_acquireWakeLockWithUid = 2;
        static final int TRANSACTION_blockReallyGoToSleepFromDozing = 45;
        static final int TRANSACTION_blockSleepFromDozingByFingerprint = 47;
        static final int TRANSACTION_boostScreenBrightness = 24;
        static final int TRANSACTION_crash = 21;
        static final int TRANSACTION_getLastShutdownReason = 22;
        static final int TRANSACTION_getPlugType = 30;
        static final int TRANSACTION_getPowerSaveState = 14;
        static final int TRANSACTION_goToSleep = 10;
        static final int TRANSACTION_isDeviceIdleMode = 16;
        static final int TRANSACTION_isInteractive = 12;
        static final int TRANSACTION_isLightDeviceIdleMode = 17;
        static final int TRANSACTION_isPowerSaveMode = 13;
        static final int TRANSACTION_isScreenBrightnessBoosted = 25;
        static final int TRANSACTION_isScreenOn = 43;
        static final int TRANSACTION_isUseProximitySensorLocked = 44;
        static final int TRANSACTION_isWakeLockLevelSupported = 7;
        static final int TRANSACTION_lightupNow = 36;
        static final int TRANSACTION_lockNow = 35;
        static final int TRANSACTION_nap = 11;
        static final int TRANSACTION_notifyCameraParamLuma = 37;
        static final int TRANSACTION_notifyKeyguardActive = 32;
        static final int TRANSACTION_notifyPhoneState = 34;
        static final int TRANSACTION_onFronzenPackage = 50;
        static final int TRANSACTION_powerHint = 5;
        static final int TRANSACTION_reboot = 18;
        static final int TRANSACTION_rebootSafeMode = 19;
        static final int TRANSACTION_releaseWakeLock = 3;
        static final int TRANSACTION_sendFingerprintResult = 40;
        static final int TRANSACTION_setAttentionLight = 28;
        static final int TRANSACTION_setButtonLightMode = 31;
        static final int TRANSACTION_setColorFadeOffAnimationDurationMillis = 38;
        static final int TRANSACTION_setDozeOverrideFromNightPearl = 42;
        static final int TRANSACTION_setFingerprintOverlayEnable = 48;
        static final int TRANSACTION_setFingerprintShooterVisible = 49;
        static final int TRANSACTION_setHasUnansweredCall = 33;
        static final int TRANSACTION_setIsNightPearlShowing = 46;
        static final int TRANSACTION_setPowerSaveMode = 15;
        static final int TRANSACTION_setStayOnSetting = 23;
        static final int TRANSACTION_setTemporaryScreenAutoBrightnessAdjustmentSettingOverride = 27;
        static final int TRANSACTION_setTemporaryScreenBrightnessSettingOverride = 26;
        static final int TRANSACTION_setWakefullnessOverride = 41;
        static final int TRANSACTION_shutdown = 20;
        static final int TRANSACTION_updateBlockedUids = 29;
        static final int TRANSACTION_updateWakeLockUids = 4;
        static final int TRANSACTION_updateWakeLockWorkSource = 6;
        static final int TRANSACTION_userActivity = 8;
        static final int TRANSACTION_wakeUp = 9;
        static final int TRANSACTION_wakeUpByWho = 39;

        private static class Proxy implements IPowerManager {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            public IBinder asBinder() {
                return this.mRemote;
            }

            public String getInterfaceDescriptor() {
                return Stub.DESCRIPTOR;
            }

            public void acquireWakeLock(IBinder lock, int flags, String tag, String packageName, WorkSource ws, String historyTag, IRemoteCallback callback) throws RemoteException {
                IBinder iBinder = null;
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeStrongBinder(lock);
                    _data.writeInt(flags);
                    _data.writeString(tag);
                    _data.writeString(packageName);
                    if (ws != null) {
                        _data.writeInt(1);
                        ws.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    _data.writeString(historyTag);
                    if (callback != null) {
                        iBinder = callback.asBinder();
                    }
                    _data.writeStrongBinder(iBinder);
                    this.mRemote.transact(1, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void acquireWakeLockWithUid(IBinder lock, int flags, String tag, String packageName, int uidtoblame) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeStrongBinder(lock);
                    _data.writeInt(flags);
                    _data.writeString(tag);
                    _data.writeString(packageName);
                    _data.writeInt(uidtoblame);
                    this.mRemote.transact(2, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void releaseWakeLock(IBinder lock, int flags) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeStrongBinder(lock);
                    _data.writeInt(flags);
                    this.mRemote.transact(3, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void updateWakeLockUids(IBinder lock, int[] uids) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeStrongBinder(lock);
                    _data.writeIntArray(uids);
                    this.mRemote.transact(4, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void powerHint(int hintId, int data) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(hintId);
                    _data.writeInt(data);
                    this.mRemote.transact(5, _data, null, 1);
                } finally {
                    _data.recycle();
                }
            }

            public void updateWakeLockWorkSource(IBinder lock, WorkSource ws, String historyTag) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeStrongBinder(lock);
                    if (ws != null) {
                        _data.writeInt(1);
                        ws.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    _data.writeString(historyTag);
                    this.mRemote.transact(6, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public boolean isWakeLockLevelSupported(int level) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(level);
                    this.mRemote.transact(7, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    _reply.recycle();
                    _data.recycle();
                    return _result;
                } catch (Throwable th) {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void userActivity(long time, int event, int flags) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeLong(time);
                    _data.writeInt(event);
                    _data.writeInt(flags);
                    this.mRemote.transact(8, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void wakeUp(long time, String reason, String opPackageName) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeLong(time);
                    _data.writeString(reason);
                    _data.writeString(opPackageName);
                    this.mRemote.transact(9, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void goToSleep(long time, int reason, int flags) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeLong(time);
                    _data.writeInt(reason);
                    _data.writeInt(flags);
                    this.mRemote.transact(10, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void nap(long time) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeLong(time);
                    this.mRemote.transact(11, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public boolean isInteractive() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(12, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    _reply.recycle();
                    _data.recycle();
                    return _result;
                } catch (Throwable th) {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public boolean isPowerSaveMode() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(13, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    _reply.recycle();
                    _data.recycle();
                    return _result;
                } catch (Throwable th) {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public PowerSaveState getPowerSaveState(int serviceType) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    PowerSaveState _result;
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(serviceType);
                    this.mRemote.transact(14, _data, _reply, 0);
                    _reply.readException();
                    if (_reply.readInt() != 0) {
                        _result = (PowerSaveState) PowerSaveState.CREATOR.createFromParcel(_reply);
                    } else {
                        _result = null;
                    }
                    _reply.recycle();
                    _data.recycle();
                    return _result;
                } catch (Throwable th) {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public boolean setPowerSaveMode(boolean mode) throws RemoteException {
                int i = 0;
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (mode) {
                        i = 1;
                    }
                    _data.writeInt(i);
                    this.mRemote.transact(15, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    _reply.recycle();
                    _data.recycle();
                    return _result;
                } catch (Throwable th) {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public boolean isDeviceIdleMode() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(16, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    _reply.recycle();
                    _data.recycle();
                    return _result;
                } catch (Throwable th) {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public boolean isLightDeviceIdleMode() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(17, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    _reply.recycle();
                    _data.recycle();
                    return _result;
                } catch (Throwable th) {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void reboot(boolean confirm, String reason, boolean wait) throws RemoteException {
                int i = 1;
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(confirm ? 1 : 0);
                    _data.writeString(reason);
                    if (!wait) {
                        i = 0;
                    }
                    _data.writeInt(i);
                    this.mRemote.transact(18, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void rebootSafeMode(boolean confirm, boolean wait) throws RemoteException {
                int i = 1;
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(confirm ? 1 : 0);
                    if (!wait) {
                        i = 0;
                    }
                    _data.writeInt(i);
                    this.mRemote.transact(19, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void shutdown(boolean confirm, String reason, boolean wait) throws RemoteException {
                int i = 1;
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(confirm ? 1 : 0);
                    _data.writeString(reason);
                    if (!wait) {
                        i = 0;
                    }
                    _data.writeInt(i);
                    this.mRemote.transact(20, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void crash(String message) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(message);
                    this.mRemote.transact(21, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public int getLastShutdownReason() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(22, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void setStayOnSetting(int val) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(val);
                    this.mRemote.transact(23, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void boostScreenBrightness(long time) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeLong(time);
                    this.mRemote.transact(24, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public boolean isScreenBrightnessBoosted() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(25, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    _reply.recycle();
                    _data.recycle();
                    return _result;
                } catch (Throwable th) {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void setTemporaryScreenBrightnessSettingOverride(int brightness) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(brightness);
                    this.mRemote.transact(26, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void setTemporaryScreenAutoBrightnessAdjustmentSettingOverride(float adj) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeFloat(adj);
                    this.mRemote.transact(27, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void setAttentionLight(boolean on, int color) throws RemoteException {
                int i = 0;
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (on) {
                        i = 1;
                    }
                    _data.writeInt(i);
                    _data.writeInt(color);
                    this.mRemote.transact(28, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void updateBlockedUids(int uid, boolean isBlocked) throws RemoteException {
                int i = 0;
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(uid);
                    if (isBlocked) {
                        i = 1;
                    }
                    _data.writeInt(i);
                    this.mRemote.transact(29, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public int getPlugType() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(30, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void setButtonLightMode(int buttonLightMode) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(buttonLightMode);
                    this.mRemote.transact(31, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void notifyKeyguardActive(boolean active) throws RemoteException {
                int i = 0;
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (active) {
                        i = 1;
                    }
                    _data.writeInt(i);
                    this.mRemote.transact(32, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void setHasUnansweredCall(boolean has) throws RemoteException {
                int i = 0;
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (has) {
                        i = 1;
                    }
                    _data.writeInt(i);
                    this.mRemote.transact(33, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void notifyPhoneState(int state) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(state);
                    this.mRemote.transact(34, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void lockNow() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(35, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void lightupNow() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(36, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public int notifyCameraParamLuma(String keyval) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(keyval);
                    this.mRemote.transact(37, _data, _reply, 0);
                    _reply.readException();
                    int _result = _reply.readInt();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void setColorFadeOffAnimationDurationMillis(int offtime) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(offtime);
                    this.mRemote.transact(38, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void wakeUpByWho(long time, String who) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeLong(time);
                    _data.writeString(who);
                    this.mRemote.transact(39, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void sendFingerprintResult(int result) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(result);
                    this.mRemote.transact(40, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public boolean setWakefullnessOverride(int doze) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(doze);
                    this.mRemote.transact(41, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    _reply.recycle();
                    _data.recycle();
                    return _result;
                } catch (Throwable th) {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void setDozeOverrideFromNightPearl(int screenState, int screenBrightness) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(screenState);
                    _data.writeInt(screenBrightness);
                    this.mRemote.transact(42, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public boolean isScreenOn() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(43, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    _reply.recycle();
                    _data.recycle();
                    return _result;
                } catch (Throwable th) {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public boolean isUseProximitySensorLocked() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(44, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    _reply.recycle();
                    _data.recycle();
                    return _result;
                } catch (Throwable th) {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public boolean blockReallyGoToSleepFromDozing(boolean blocked) throws RemoteException {
                int i = 0;
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (blocked) {
                        i = 1;
                    }
                    _data.writeInt(i);
                    this.mRemote.transact(45, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    _reply.recycle();
                    _data.recycle();
                    return _result;
                } catch (Throwable th) {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void setIsNightPearlShowing(boolean isShowing) throws RemoteException {
                int i = 0;
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (isShowing) {
                        i = 1;
                    }
                    _data.writeInt(i);
                    this.mRemote.transact(46, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void blockSleepFromDozingByFingerprint(boolean blocked) throws RemoteException {
                int i = 0;
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (blocked) {
                        i = 1;
                    }
                    _data.writeInt(i);
                    this.mRemote.transact(47, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void setFingerprintOverlayEnable(boolean enable) throws RemoteException {
                int i = 0;
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (enable) {
                        i = 1;
                    }
                    _data.writeInt(i);
                    this.mRemote.transact(48, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void setFingerprintShooterVisible(int visibility) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeInt(visibility);
                    this.mRemote.transact(49, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public boolean onFronzenPackage(String packageName) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(packageName);
                    this.mRemote.transact(50, _data, _reply, 0);
                    _reply.readException();
                    boolean _result = _reply.readInt() != 0;
                    _reply.recycle();
                    _data.recycle();
                    return _result;
                } catch (Throwable th) {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IPowerManager asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin == null || !(iin instanceof IPowerManager)) {
                return new Proxy(obj);
            }
            return (IPowerManager) iin;
        }

        public IBinder asBinder() {
            return this;
        }

        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            IBinder _arg0;
            boolean _result;
            int _result2;
            switch (code) {
                case 1:
                    WorkSource _arg4;
                    data.enforceInterface(DESCRIPTOR);
                    _arg0 = data.readStrongBinder();
                    int _arg1 = data.readInt();
                    String _arg2 = data.readString();
                    String _arg3 = data.readString();
                    if (data.readInt() != 0) {
                        _arg4 = (WorkSource) WorkSource.CREATOR.createFromParcel(data);
                    } else {
                        _arg4 = null;
                    }
                    acquireWakeLock(_arg0, _arg1, _arg2, _arg3, _arg4, data.readString(), android.os.IRemoteCallback.Stub.asInterface(data.readStrongBinder()));
                    reply.writeNoException();
                    return true;
                case 2:
                    data.enforceInterface(DESCRIPTOR);
                    acquireWakeLockWithUid(data.readStrongBinder(), data.readInt(), data.readString(), data.readString(), data.readInt());
                    reply.writeNoException();
                    return true;
                case 3:
                    data.enforceInterface(DESCRIPTOR);
                    releaseWakeLock(data.readStrongBinder(), data.readInt());
                    reply.writeNoException();
                    return true;
                case 4:
                    data.enforceInterface(DESCRIPTOR);
                    updateWakeLockUids(data.readStrongBinder(), data.createIntArray());
                    reply.writeNoException();
                    return true;
                case 5:
                    data.enforceInterface(DESCRIPTOR);
                    powerHint(data.readInt(), data.readInt());
                    return true;
                case 6:
                    WorkSource _arg12;
                    data.enforceInterface(DESCRIPTOR);
                    _arg0 = data.readStrongBinder();
                    if (data.readInt() != 0) {
                        _arg12 = (WorkSource) WorkSource.CREATOR.createFromParcel(data);
                    } else {
                        _arg12 = null;
                    }
                    updateWakeLockWorkSource(_arg0, _arg12, data.readString());
                    reply.writeNoException();
                    return true;
                case 7:
                    data.enforceInterface(DESCRIPTOR);
                    _result = isWakeLockLevelSupported(data.readInt());
                    reply.writeNoException();
                    reply.writeInt(_result ? 1 : 0);
                    return true;
                case 8:
                    data.enforceInterface(DESCRIPTOR);
                    userActivity(data.readLong(), data.readInt(), data.readInt());
                    reply.writeNoException();
                    return true;
                case 9:
                    data.enforceInterface(DESCRIPTOR);
                    wakeUp(data.readLong(), data.readString(), data.readString());
                    reply.writeNoException();
                    return true;
                case 10:
                    data.enforceInterface(DESCRIPTOR);
                    goToSleep(data.readLong(), data.readInt(), data.readInt());
                    reply.writeNoException();
                    return true;
                case 11:
                    data.enforceInterface(DESCRIPTOR);
                    nap(data.readLong());
                    reply.writeNoException();
                    return true;
                case 12:
                    data.enforceInterface(DESCRIPTOR);
                    _result = isInteractive();
                    reply.writeNoException();
                    reply.writeInt(_result ? 1 : 0);
                    return true;
                case 13:
                    data.enforceInterface(DESCRIPTOR);
                    _result = isPowerSaveMode();
                    reply.writeNoException();
                    reply.writeInt(_result ? 1 : 0);
                    return true;
                case 14:
                    data.enforceInterface(DESCRIPTOR);
                    PowerSaveState _result3 = getPowerSaveState(data.readInt());
                    reply.writeNoException();
                    if (_result3 != null) {
                        reply.writeInt(1);
                        _result3.writeToParcel(reply, 1);
                    } else {
                        reply.writeInt(0);
                    }
                    return true;
                case 15:
                    data.enforceInterface(DESCRIPTOR);
                    _result = setPowerSaveMode(data.readInt() != 0);
                    reply.writeNoException();
                    reply.writeInt(_result ? 1 : 0);
                    return true;
                case 16:
                    data.enforceInterface(DESCRIPTOR);
                    _result = isDeviceIdleMode();
                    reply.writeNoException();
                    reply.writeInt(_result ? 1 : 0);
                    return true;
                case 17:
                    data.enforceInterface(DESCRIPTOR);
                    _result = isLightDeviceIdleMode();
                    reply.writeNoException();
                    reply.writeInt(_result ? 1 : 0);
                    return true;
                case 18:
                    data.enforceInterface(DESCRIPTOR);
                    reboot(data.readInt() != 0, data.readString(), data.readInt() != 0);
                    reply.writeNoException();
                    return true;
                case 19:
                    data.enforceInterface(DESCRIPTOR);
                    rebootSafeMode(data.readInt() != 0, data.readInt() != 0);
                    reply.writeNoException();
                    return true;
                case 20:
                    data.enforceInterface(DESCRIPTOR);
                    shutdown(data.readInt() != 0, data.readString(), data.readInt() != 0);
                    reply.writeNoException();
                    return true;
                case 21:
                    data.enforceInterface(DESCRIPTOR);
                    crash(data.readString());
                    reply.writeNoException();
                    return true;
                case 22:
                    data.enforceInterface(DESCRIPTOR);
                    _result2 = getLastShutdownReason();
                    reply.writeNoException();
                    reply.writeInt(_result2);
                    return true;
                case 23:
                    data.enforceInterface(DESCRIPTOR);
                    setStayOnSetting(data.readInt());
                    reply.writeNoException();
                    return true;
                case 24:
                    data.enforceInterface(DESCRIPTOR);
                    boostScreenBrightness(data.readLong());
                    reply.writeNoException();
                    return true;
                case 25:
                    data.enforceInterface(DESCRIPTOR);
                    _result = isScreenBrightnessBoosted();
                    reply.writeNoException();
                    reply.writeInt(_result ? 1 : 0);
                    return true;
                case 26:
                    data.enforceInterface(DESCRIPTOR);
                    setTemporaryScreenBrightnessSettingOverride(data.readInt());
                    reply.writeNoException();
                    return true;
                case 27:
                    data.enforceInterface(DESCRIPTOR);
                    setTemporaryScreenAutoBrightnessAdjustmentSettingOverride(data.readFloat());
                    reply.writeNoException();
                    return true;
                case 28:
                    data.enforceInterface(DESCRIPTOR);
                    setAttentionLight(data.readInt() != 0, data.readInt());
                    reply.writeNoException();
                    return true;
                case 29:
                    data.enforceInterface(DESCRIPTOR);
                    updateBlockedUids(data.readInt(), data.readInt() != 0);
                    reply.writeNoException();
                    return true;
                case 30:
                    data.enforceInterface(DESCRIPTOR);
                    _result2 = getPlugType();
                    reply.writeNoException();
                    reply.writeInt(_result2);
                    return true;
                case 31:
                    data.enforceInterface(DESCRIPTOR);
                    setButtonLightMode(data.readInt());
                    reply.writeNoException();
                    return true;
                case 32:
                    data.enforceInterface(DESCRIPTOR);
                    notifyKeyguardActive(data.readInt() != 0);
                    reply.writeNoException();
                    return true;
                case 33:
                    data.enforceInterface(DESCRIPTOR);
                    setHasUnansweredCall(data.readInt() != 0);
                    reply.writeNoException();
                    return true;
                case 34:
                    data.enforceInterface(DESCRIPTOR);
                    notifyPhoneState(data.readInt());
                    reply.writeNoException();
                    return true;
                case 35:
                    data.enforceInterface(DESCRIPTOR);
                    lockNow();
                    reply.writeNoException();
                    return true;
                case 36:
                    data.enforceInterface(DESCRIPTOR);
                    lightupNow();
                    reply.writeNoException();
                    return true;
                case 37:
                    data.enforceInterface(DESCRIPTOR);
                    _result2 = notifyCameraParamLuma(data.readString());
                    reply.writeNoException();
                    reply.writeInt(_result2);
                    return true;
                case 38:
                    data.enforceInterface(DESCRIPTOR);
                    setColorFadeOffAnimationDurationMillis(data.readInt());
                    reply.writeNoException();
                    return true;
                case 39:
                    data.enforceInterface(DESCRIPTOR);
                    wakeUpByWho(data.readLong(), data.readString());
                    reply.writeNoException();
                    return true;
                case 40:
                    data.enforceInterface(DESCRIPTOR);
                    sendFingerprintResult(data.readInt());
                    reply.writeNoException();
                    return true;
                case 41:
                    data.enforceInterface(DESCRIPTOR);
                    _result = setWakefullnessOverride(data.readInt());
                    reply.writeNoException();
                    reply.writeInt(_result ? 1 : 0);
                    return true;
                case 42:
                    data.enforceInterface(DESCRIPTOR);
                    setDozeOverrideFromNightPearl(data.readInt(), data.readInt());
                    reply.writeNoException();
                    return true;
                case 43:
                    data.enforceInterface(DESCRIPTOR);
                    _result = isScreenOn();
                    reply.writeNoException();
                    reply.writeInt(_result ? 1 : 0);
                    return true;
                case 44:
                    data.enforceInterface(DESCRIPTOR);
                    _result = isUseProximitySensorLocked();
                    reply.writeNoException();
                    reply.writeInt(_result ? 1 : 0);
                    return true;
                case 45:
                    data.enforceInterface(DESCRIPTOR);
                    _result = blockReallyGoToSleepFromDozing(data.readInt() != 0);
                    reply.writeNoException();
                    reply.writeInt(_result ? 1 : 0);
                    return true;
                case 46:
                    data.enforceInterface(DESCRIPTOR);
                    setIsNightPearlShowing(data.readInt() != 0);
                    reply.writeNoException();
                    return true;
                case 47:
                    data.enforceInterface(DESCRIPTOR);
                    blockSleepFromDozingByFingerprint(data.readInt() != 0);
                    reply.writeNoException();
                    return true;
                case 48:
                    data.enforceInterface(DESCRIPTOR);
                    setFingerprintOverlayEnable(data.readInt() != 0);
                    reply.writeNoException();
                    return true;
                case 49:
                    data.enforceInterface(DESCRIPTOR);
                    setFingerprintShooterVisible(data.readInt());
                    reply.writeNoException();
                    return true;
                case 50:
                    data.enforceInterface(DESCRIPTOR);
                    _result = onFronzenPackage(data.readString());
                    reply.writeNoException();
                    reply.writeInt(_result ? 1 : 0);
                    return true;
                case IBinder.INTERFACE_TRANSACTION /*1598968902*/:
                    reply.writeString(DESCRIPTOR);
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }
    }

    void acquireWakeLock(IBinder iBinder, int i, String str, String str2, WorkSource workSource, String str3, IRemoteCallback iRemoteCallback) throws RemoteException;

    void acquireWakeLockWithUid(IBinder iBinder, int i, String str, String str2, int i2) throws RemoteException;

    boolean blockReallyGoToSleepFromDozing(boolean z) throws RemoteException;

    void blockSleepFromDozingByFingerprint(boolean z) throws RemoteException;

    void boostScreenBrightness(long j) throws RemoteException;

    void crash(String str) throws RemoteException;

    int getLastShutdownReason() throws RemoteException;

    int getPlugType() throws RemoteException;

    PowerSaveState getPowerSaveState(int i) throws RemoteException;

    void goToSleep(long j, int i, int i2) throws RemoteException;

    boolean isDeviceIdleMode() throws RemoteException;

    boolean isInteractive() throws RemoteException;

    boolean isLightDeviceIdleMode() throws RemoteException;

    boolean isPowerSaveMode() throws RemoteException;

    boolean isScreenBrightnessBoosted() throws RemoteException;

    boolean isScreenOn() throws RemoteException;

    boolean isUseProximitySensorLocked() throws RemoteException;

    boolean isWakeLockLevelSupported(int i) throws RemoteException;

    void lightupNow() throws RemoteException;

    void lockNow() throws RemoteException;

    void nap(long j) throws RemoteException;

    int notifyCameraParamLuma(String str) throws RemoteException;

    void notifyKeyguardActive(boolean z) throws RemoteException;

    void notifyPhoneState(int i) throws RemoteException;

    boolean onFronzenPackage(String str) throws RemoteException;

    void powerHint(int i, int i2) throws RemoteException;

    void reboot(boolean z, String str, boolean z2) throws RemoteException;

    void rebootSafeMode(boolean z, boolean z2) throws RemoteException;

    void releaseWakeLock(IBinder iBinder, int i) throws RemoteException;

    void sendFingerprintResult(int i) throws RemoteException;

    void setAttentionLight(boolean z, int i) throws RemoteException;

    void setButtonLightMode(int i) throws RemoteException;

    void setColorFadeOffAnimationDurationMillis(int i) throws RemoteException;

    void setDozeOverrideFromNightPearl(int i, int i2) throws RemoteException;

    void setFingerprintOverlayEnable(boolean z) throws RemoteException;

    void setFingerprintShooterVisible(int i) throws RemoteException;

    void setHasUnansweredCall(boolean z) throws RemoteException;

    void setIsNightPearlShowing(boolean z) throws RemoteException;

    boolean setPowerSaveMode(boolean z) throws RemoteException;

    void setStayOnSetting(int i) throws RemoteException;

    void setTemporaryScreenAutoBrightnessAdjustmentSettingOverride(float f) throws RemoteException;

    void setTemporaryScreenBrightnessSettingOverride(int i) throws RemoteException;

    boolean setWakefullnessOverride(int i) throws RemoteException;

    void shutdown(boolean z, String str, boolean z2) throws RemoteException;

    void updateBlockedUids(int i, boolean z) throws RemoteException;

    void updateWakeLockUids(IBinder iBinder, int[] iArr) throws RemoteException;

    void updateWakeLockWorkSource(IBinder iBinder, WorkSource workSource, String str) throws RemoteException;

    void userActivity(long j, int i, int i2) throws RemoteException;

    void wakeUp(long j, String str, String str2) throws RemoteException;

    void wakeUpByWho(long j, String str) throws RemoteException;
}
