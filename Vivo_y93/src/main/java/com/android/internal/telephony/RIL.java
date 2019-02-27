package com.android.internal.telephony;

import android.annotation.VivoHook;
import android.annotation.VivoHook.VivoHookType;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.radio.V1_0.ApnTypes;
import android.hardware.radio.V1_0.CallForwardInfo;
import android.hardware.radio.V1_0.Carrier;
import android.hardware.radio.V1_0.CarrierRestrictions;
import android.hardware.radio.V1_0.CdmaBroadcastSmsConfigInfo;
import android.hardware.radio.V1_0.CdmaSmsAck;
import android.hardware.radio.V1_0.CdmaSmsMessage;
import android.hardware.radio.V1_0.CdmaSmsSubaddress;
import android.hardware.radio.V1_0.CdmaSmsWriteArgs;
import android.hardware.radio.V1_0.CellInfoCdma;
import android.hardware.radio.V1_0.CellInfoGsm;
import android.hardware.radio.V1_0.CellInfoLte;
import android.hardware.radio.V1_0.CellInfoWcdma;
import android.hardware.radio.V1_0.DataProfileInfo;
import android.hardware.radio.V1_0.Dial;
import android.hardware.radio.V1_0.GsmBroadcastSmsConfigInfo;
import android.hardware.radio.V1_0.GsmSmsMessage;
import android.hardware.radio.V1_0.HardwareConfig;
import android.hardware.radio.V1_0.HardwareConfigModem;
import android.hardware.radio.V1_0.HardwareConfigSim;
import android.hardware.radio.V1_0.IRadio;
import android.hardware.radio.V1_0.IccIo;
import android.hardware.radio.V1_0.ImsSmsMessage;
import android.hardware.radio.V1_0.LceDataInfo;
import android.hardware.radio.V1_0.NvWriteItem;
import android.hardware.radio.V1_0.RadioCapability;
import android.hardware.radio.V1_0.RadioError;
import android.hardware.radio.V1_0.RadioResponseInfo;
import android.hardware.radio.V1_0.SelectUiccSub;
import android.hardware.radio.V1_0.SetupDataCallResult;
import android.hardware.radio.V1_0.SimApdu;
import android.hardware.radio.V1_0.SmsWriteArgs;
import android.hardware.radio.V1_0.UusInfo;
import android.hardware.radio.deprecated.V1_0.IOemHook;
import android.net.ConnectivityManager;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IHwBinder.DeathRecipient;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.WorkSource;
import android.service.carrier.CarrierIdentifier;
import android.telephony.CellInfo;
import android.telephony.ClientRequestStats;
import android.telephony.FtTelephony;
import android.telephony.FtTelephonyAdapter;
import android.telephony.ImsiEncryptionInfo;
import android.telephony.ModemActivityInfo;
import android.telephony.NeighboringCellInfo;
import android.telephony.NetworkScanRequest;
import android.telephony.PhoneNumberUtils;
import android.telephony.RadioAccessFamily;
import android.telephony.RadioAccessSpecifier;
import android.telephony.Rlog;
import android.telephony.SignalStrength;
import android.telephony.TelephonyHistogram;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.DriverCall.State;
import com.android.internal.telephony.cdma.CdmaInformationRecords;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaDisplayInfoRec;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaLineControlInfoRec;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaNumberInfoRec;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaRedirectingNumberInfoRec;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaSignalInfoRec;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaT53AudioControlInfoRec;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaT53ClirInfoRec;
import com.android.internal.telephony.cdma.CdmaSmsBroadcastConfigInfo;
import com.android.internal.telephony.dataconnection.DataCallResponse;
import com.android.internal.telephony.dataconnection.DataProfile;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.metrics.TelephonyMetrics;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.util.Preconditions;
import com.google.android.mms.pdu.CharacterSets;
import com.vivo.services.daemon.VivoDmServiceProxy;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class RIL extends BaseCommands implements CommandsInterface {
    @VivoHook(hookType = VivoHookType.NEW_FIELD)
    private static final String AUTOCALLLOG_BROADCAST = "android.vivo.autolog.open";
    private static final int DEFAULT_ACK_WAKE_LOCK_TIMEOUT_MS = 200;
    private static final int DEFAULT_BLOCKING_MESSAGE_RESPONSE_TIMEOUT_MS = 2000;
    private static final int DEFAULT_WAKE_LOCK_TIMEOUT_MS = 60000;
    static final int EVENT_ACK_WAKE_LOCK_TIMEOUT = 4;
    static final int EVENT_BLOCKING_RESPONSE_TIMEOUT = 5;
    static final int EVENT_RADIO_PROXY_DEAD = 6;
    static final int EVENT_WAKE_LOCK_TIMEOUT = 2;
    public static final int FOR_ACK_WAKELOCK = 1;
    public static final int FOR_WAKELOCK = 0;
    static final String[] HIDL_SERVICE_NAME = new String[]{"slot1", "slot2", "slot3"};
    private static final int INT_SIZE = 4;
    public static final int INVALID_WAKELOCK = -1;
    static final int IRADIO_GET_SERVICE_DELAY_MILLIS = 4000;
    private static final int OEMHOOK_BASE = 524288;
    private static final String OEM_IDENTIFIER = "QOEMHOOK";
    private static final String OPEN_BBLOG_BROADCAST = "android.vivo.bbklog.action.startlog";
    private static final String PERSIST_AUTO_CALLLOG = "persist.sys.vivo.autologcall";
    private static final String PERSIST_CALLLOG_FLAG = "persist.radio.vivo.autologflag";
    private static final int QCRIL_EVT_HOOK_UNSOL_MCC_REPORT = 526290;
    private static final int QCRIL_EVT_HOOK_UNSOL_MISC_INFO = 526292;
    private static final int QCRIL_EVT_HOOK_UNSOL_PD_STATE = 526291;
    @VivoHook(hookType = VivoHookType.NEW_FIELD)
    private static final int QCRIL_EVT_HOOK_UNSOL_SPEECH_CODEC_INFO = 531289;
    private static final int QCRIL_EVT_HOOK_UNSOL_UI_STATUS_NOT_READY = 526289;
    static final String RILJ_ACK_WAKELOCK_NAME = "RILJ_ACK_WL";
    static final boolean RILJ_LOGD = true;
    static final boolean RILJ_LOGV = false;
    static final String RILJ_LOG_TAG = "RILJ";
    static final int RIL_HISTOGRAM_BUCKET_COUNT = 5;
    @VivoHook(hookType = VivoHookType.NEW_FIELD)
    private static String last_strInvalidSimTacLac;
    @VivoHook(hookType = VivoHookType.NEW_FIELD)
    private static String last_strVopsTac;
    private static boolean mNeedKillRild = false;
    private static boolean mOnceKillRild = false;
    static SparseArray<TelephonyHistogram> mRilTimeHistograms = new SparseArray();
    private final int MISC_INFO_CDMA_LOCK_IND;
    private final int MISC_INFO_ESN_MEID_IND;
    private final int MISC_INFO_HADOOP_IND;
    private final int MISC_INFO_INVALID_SIM_IND;
    private final int MSIC_INFO_BANDSIGNALS_ONE_COLLECTION_IND;
    private final int MSIC_INFO_BANDSIGNALS_TWO_COLLECTION_IND;
    @VivoHook(hookType = VivoHookType.NEW_FIELD)
    private final int MSIC_INFO_MODEM_DATA_COLLECTION_IND;
    final WakeLock mAckWakeLock;
    final int mAckWakeLockTimeout;
    volatile int mAckWlSequenceNum;
    private WorkSource mActiveWakelockWorkSource;
    private BroadcastReceiver mBandSignalReceiver;
    private final ClientWakelockTracker mClientWakelockTracker;
    private int mGetOemServiceThreadNum;
    private int mGetServiceThreadNum;
    @VivoHook(hookType = VivoHookType.NEW_FIELD)
    HangupInfoCollectHelper mHangupInfoCollectHelper;
    int mHeaderSize;
    protected boolean mIsMobileNetworkSupported;
    Object[] mLastNITZTimeInfo;
    private Object mLock;
    private TelephonyMetrics mMetrics;
    OemHookIndication mOemHookIndication;
    volatile IOemHook mOemHookProxy;
    OemHookResponse mOemHookResponse;
    private Object mOemLock;
    final Integer mPhoneId;
    protected WorkSource mRILDefaultWorkSource;
    RadioIndication mRadioIndication;
    volatile IRadio mRadioProxy;
    final AtomicLong mRadioProxyCookie;
    final RadioProxyDeathRecipient mRadioProxyDeathRecipient;
    RadioResponse mRadioResponse;
    SparseArray<RILRequest> mRequestList;
    final RilHandler mRilHandler;
    private Object mTestLock;
    AtomicBoolean mTestingEmergencyCall;
    final WakeLock mWakeLock;
    int mWakeLockCount;
    final int mWakeLockTimeout;
    volatile int mWlSequenceNum;

    final class RadioProxyDeathRecipient implements DeathRecipient {
        RadioProxyDeathRecipient() {
        }

        public void serviceDied(long cookie) {
            RIL.this.riljLog("serviceDied");
            RIL.this.mRilHandler.sendMessageDelayed(RIL.this.mRilHandler.obtainMessage(6, Long.valueOf(cookie)), 4000);
        }
    }

    class RilHandler extends Handler {
        RilHandler() {
        }

        public void handleMessage(Message msg) {
            RILRequest rr;
            switch (msg.what) {
                case 2:
                    synchronized (RIL.this.mRequestList) {
                        if (msg.arg1 == RIL.this.mWlSequenceNum && RIL.this.clearWakeLock(0)) {
                            int count = RIL.this.mRequestList.size();
                            Rlog.d(RIL.RILJ_LOG_TAG, "WAKE_LOCK_TIMEOUT  mRequestList=" + count);
                            for (int i = 0; i < count; i++) {
                                rr = (RILRequest) RIL.this.mRequestList.valueAt(i);
                                Rlog.d(RIL.RILJ_LOG_TAG, i + ": [" + rr.mSerial + "] " + RIL.requestToString(rr.mRequest));
                            }
                        }
                    }
                    return;
                case 4:
                    if (msg.arg1 == RIL.this.mAckWlSequenceNum) {
                        boolean -wrap2 = RIL.this.clearWakeLock(1);
                        return;
                    }
                    return;
                case 5:
                    rr = RIL.this.findAndRemoveRequestFromList(msg.arg1);
                    if (rr != null) {
                        if (rr.mResult != null) {
                            AsyncResult.forMessage(rr.mResult, RIL.getResponseForTimedOutRILRequest(rr), null);
                            rr.mResult.sendToTarget();
                            RIL.this.mMetrics.writeOnRilTimeoutResponse(RIL.this.mPhoneId.intValue(), rr.mSerial, rr.mRequest);
                        }
                        RIL.this.decrementWakeLock(rr);
                        rr.release();
                        return;
                    }
                    return;
                case 6:
                    RIL.this.riljLog("handleMessage: EVENT_RADIO_PROXY_DEAD cookie = " + msg.obj + " mRadioProxyCookie = " + RIL.this.mRadioProxyCookie.get());
                    if (((Long) msg.obj).longValue() == RIL.this.mRadioProxyCookie.get()) {
                        RIL.this.resetProxyAndRequestList();
                        RIL.this.getRadioProxy(null);
                        RIL.this.getOemHookProxy(null);
                        return;
                    }
                    return;
                default:
                    return;
            }
        }
    }

    public static List<TelephonyHistogram> getTelephonyRILTimingHistograms() {
        List<TelephonyHistogram> list;
        synchronized (mRilTimeHistograms) {
            list = new ArrayList(mRilTimeHistograms.size());
            for (int i = 0; i < mRilTimeHistograms.size(); i++) {
                list.add(new TelephonyHistogram((TelephonyHistogram) mRilTimeHistograms.valueAt(i)));
            }
        }
        return list;
    }

    private static Object getResponseForTimedOutRILRequest(RILRequest rr) {
        if (rr == null) {
            return null;
        }
        Object timeoutResponse = null;
        switch (rr.mRequest) {
            case 135:
                timeoutResponse = new ModemActivityInfo(0, 0, 0, new int[5], 0, 0);
                break;
        }
        return timeoutResponse;
    }

    protected void resetProxyAndRequestList() {
        this.mRadioProxy = null;
        this.mOemHookProxy = null;
        this.mRadioProxyCookie.incrementAndGet();
        setRadioState(RadioState.RADIO_UNAVAILABLE);
        RILRequest.resetSerial();
        clearRequestList(1, false);
    }

    private IRadio getRadioProxy(Message result) {
        if (!this.mIsMobileNetworkSupported) {
            if (result != null) {
                AsyncResult.forMessage(result, null, CommandException.fromRilErrno(1));
                result.sendToTarget();
            }
            return null;
        } else if (this.mRadioProxy != null) {
            return this.mRadioProxy;
        } else {
            riljLog("thread num = " + this.mGetServiceThreadNum);
            if (this.mGetServiceThreadNum >= 20) {
                if (result != null) {
                    AsyncResult.forMessage(result, null, CommandException.fromRilErrno(1));
                    result.sendToTarget();
                }
                return null;
            }
            synchronized (this.mLock) {
                new Thread() {
                    /* JADX WARNING: Removed duplicated region for block: B:18:0x007e A:{Splitter: B:1:0x000e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
                    /* JADX WARNING: Missing block: B:18:0x007e, code:
            r0 = move-exception;
     */
                    /* JADX WARNING: Missing block: B:19:0x007f, code:
            r7.this$0.mRadioProxy = null;
            r7.this$0.riljLoge("RadioProxy getService/setResponseFunctions: " + r0);
     */
                    /* Code decompiled incorrectly, please refer to instructions dump. */
                    public void run() {
                        RIL ril = RIL.this;
                        ril.mGetServiceThreadNum = ril.mGetServiceThreadNum + 1;
                        try {
                            RIL.mNeedKillRild = true;
                            RIL.this.mRadioProxy = IRadio.getService(RIL.HIDL_SERVICE_NAME[RIL.this.mPhoneId == null ? 0 : RIL.this.mPhoneId.intValue()]);
                            if (RIL.this.mRadioProxy != null) {
                                RIL.this.mRadioProxy.linkToDeath(RIL.this.mRadioProxyDeathRecipient, RIL.this.mRadioProxyCookie.incrementAndGet());
                                RIL.this.mRadioProxy.setResponseFunctions(RIL.this.mRadioResponse, RIL.this.mRadioIndication);
                            } else {
                                RIL.this.riljLoge("getRadioProxy: mRadioProxy == null");
                            }
                        } catch (Exception e) {
                        }
                        RIL.mNeedKillRild = false;
                        synchronized (RIL.this.mLock) {
                            RIL.this.mLock.notify();
                        }
                        ril = RIL.this;
                        ril.mGetServiceThreadNum = ril.mGetServiceThreadNum - 1;
                    }
                }.start();
                try {
                    riljLog("wait begin");
                    this.mLock.wait(3000);
                    riljLog("wait end");
                } catch (InterruptedException e) {
                    riljLog("wait exception");
                }
            }
            riljLog("mNeedKillRild = " + mNeedKillRild + " mOnceKillRild = " + mOnceKillRild);
            if (mNeedKillRild && (mOnceKillRild ^ 1) != 0) {
                mOnceKillRild = true;
                killRild();
            }
            if (this.mRadioProxy != null) {
                return this.mRadioProxy;
            }
            if (result != null) {
                AsyncResult.forMessage(result, null, CommandException.fromRilErrno(1));
                result.sendToTarget();
            }
            this.mRilHandler.sendMessageDelayed(this.mRilHandler.obtainMessage(6, Long.valueOf(this.mRadioProxyCookie.incrementAndGet())), 4000);
            return null;
        }
    }

    private void killRild() {
        String cmdRe = "";
        try {
            VivoDmServiceProxy vivoDmSrvProxy = VivoDmServiceProxy.asInterface(ServiceManager.getService("vivo_daemon.service"));
            riljLog("vivoDmSrvProxy = " + vivoDmSrvProxy);
            if (vivoDmSrvProxy != null) {
                if (SystemProperties.get("persist.vivo.vivo_daemon", "") != "") {
                    cmdRe = vivoDmSrvProxy.runShellWithResult("Ocg899gEtg9vke34o1cwZsTEwNNIM7eDhOpYsKMI+MpRsCM4eoUWtYBr1HR2hTTzpOqZgks5wt9T0eIrPelsbbJeJHEjhkJ4lfKXRyK4SgW34wkKZtOEOA0FwWMMiG0+PP+e9ebr3/QXmzayp3WYtFFCQ2sRjdgn+Xt+OPJ9FtoNCwm2o5K0yz+YGzKHUYTDG57Idrmvz6iZTTSiIDdVKqltM2R2NoQs4/PmRCvcW3RhQidutdTfmnUENQFWwDI/TObk8PgNXXyXWGYUd4ziWDRL0nuk2nJ8Hk0Cqk2DDRp6XPM+Tab2Su4EOblKTToG3HWoasI03ZEefihltIQxQg==");
                } else {
                    cmdRe = vivoDmSrvProxy.runShellWithResult("ps -A | grep rild");
                }
                riljLog("result:" + cmdRe);
            }
            if (!TextUtils.isEmpty(cmdRe)) {
                for (String subStr : cmdRe.split("\n")) {
                    riljLog("subStr begin = " + subStr);
                    if (subStr.length() > 18) {
                        String ss = subStr.substring(12, 18);
                        riljLog("subStr end = " + ss + "!");
                        if (vivoDmSrvProxy != null) {
                            String str = "";
                            if (SystemProperties.get("persist.vivo.vivo_daemon", "") != "") {
                                str = "dfC6JoUJJ13J7zj5U5qgCG4HjSvtm5+/88N7NBJ0kPENYogs9etT7iU2VNyUjK6JYLS3wPBiFWMNzUxhZYJDFWKu2fKMdOMPwlLXoKSSpyntWLjeuAp34TQlEm5r6Xqr9yFgA5FNGfBvdcYAiNbw1qNmYyArAC6oBG7vIZA488KYiooOy9iiErj9O/zR1h5wlsbSa+Ru0+X2FQNCzpMykTC6vBjlXzJHTAOoejpxjPwM1cnuMpCmfipJ014zyo/Jl573rpDqaOk0XnNmrR9l1pyofMbGJtO53TuorxyUedEsLi1KXmqN+1QxWzcRol6BSOMRJJwRNraGOjJkcALXBw==?" + ss;
                            } else {
                                str = "kill -9 " + ss;
                            }
                            vivoDmSrvProxy.runShell(str);
                        }
                    }
                }
            }
        } catch (Exception e) {
            riljLog("get vivo_daemon.service failed e = " + e);
        }
    }

    private IOemHook getOemHookProxy(Message result) {
        if (!this.mIsMobileNetworkSupported) {
            if (result != null) {
                AsyncResult.forMessage(result, null, CommandException.fromRilErrno(1));
                result.sendToTarget();
            }
            return null;
        } else if (this.mOemHookProxy != null) {
            return this.mOemHookProxy;
        } else {
            if (this.mGetOemServiceThreadNum > 10) {
                if (result != null) {
                    AsyncResult.forMessage(result, null, CommandException.fromRilErrno(1));
                    result.sendToTarget();
                }
                return null;
            }
            synchronized (this.mOemLock) {
                new Thread() {
                    /* JADX WARNING: Removed duplicated region for block: B:18:0x0063 A:{Splitter: B:1:0x000c, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
                    /* JADX WARNING: Missing block: B:18:0x0063, code:
            r0 = move-exception;
     */
                    /* JADX WARNING: Missing block: B:19:0x0064, code:
            r5.this$0.mOemHookProxy = null;
            r5.this$0.riljLoge("OemHookProxy getService/setResponseFunctions: " + r0);
     */
                    /* Code decompiled incorrectly, please refer to instructions dump. */
                    public void run() {
                        RIL ril = RIL.this;
                        ril.mGetOemServiceThreadNum = ril.mGetOemServiceThreadNum + 1;
                        try {
                            RIL.this.mOemHookProxy = IOemHook.getService(RIL.HIDL_SERVICE_NAME[RIL.this.mPhoneId == null ? 0 : RIL.this.mPhoneId.intValue()]);
                            if (RIL.this.mOemHookProxy != null) {
                                RIL.this.mOemHookProxy.setResponseFunctions(RIL.this.mOemHookResponse, RIL.this.mOemHookIndication);
                            } else {
                                RIL.this.riljLoge("getOemHookProxy: mOemHookProxy == null");
                            }
                        } catch (Exception e) {
                        }
                        synchronized (RIL.this.mOemLock) {
                            RIL.this.mOemLock.notify();
                        }
                        ril = RIL.this;
                        ril.mGetOemServiceThreadNum = ril.mGetOemServiceThreadNum - 1;
                    }
                }.start();
                try {
                    riljLog("wait begin");
                    this.mOemLock.wait(3000);
                    riljLog("wait end");
                } catch (InterruptedException e) {
                    riljLog("wait exception");
                }
            }
            if (this.mOemHookProxy == null) {
                if (result != null) {
                    AsyncResult.forMessage(result, null, CommandException.fromRilErrno(1));
                    result.sendToTarget();
                }
                this.mRilHandler.sendMessageDelayed(this.mRilHandler.obtainMessage(6, Long.valueOf(this.mRadioProxyCookie.incrementAndGet())), 4000);
            }
            return this.mOemHookProxy;
        }
    }

    public RIL(Context context, int preferredNetworkType, int cdmaSubscription) {
        this(context, preferredNetworkType, cdmaSubscription, null);
    }

    public RIL(Context context, int preferredNetworkType, int cdmaSubscription, Integer instanceId) {
        super(context);
        this.mClientWakelockTracker = new ClientWakelockTracker();
        this.mHeaderSize = OEM_IDENTIFIER.length() + 8;
        this.mWlSequenceNum = 0;
        this.mAckWlSequenceNum = 0;
        this.mRequestList = new SparseArray();
        this.mTestingEmergencyCall = new AtomicBoolean(false);
        this.mMetrics = TelephonyMetrics.getInstance();
        this.mRadioProxy = null;
        this.mOemHookProxy = null;
        this.mRadioProxyCookie = new AtomicLong(0);
        this.MISC_INFO_HADOOP_IND = 1;
        this.MISC_INFO_INVALID_SIM_IND = 4;
        this.MISC_INFO_ESN_MEID_IND = 5;
        this.MISC_INFO_CDMA_LOCK_IND = 7;
        this.MSIC_INFO_BANDSIGNALS_ONE_COLLECTION_IND = 11;
        this.MSIC_INFO_BANDSIGNALS_TWO_COLLECTION_IND = 12;
        this.MSIC_INFO_MODEM_DATA_COLLECTION_IND = 13;
        this.mLock = new Object();
        this.mOemLock = new Object();
        this.mTestLock = new Object();
        this.mGetServiceThreadNum = 0;
        this.mGetOemServiceThreadNum = 0;
        this.mBandSignalReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(RIL.AUTOCALLLOG_BROADCAST)) {
                    int calllogflag = intent.getExtras().getInt("callflag");
                    RIL.this.riljLog("autolog received brocast  calllogflag=" + calllogflag);
                    if (calllogflag == 1) {
                        SystemProperties.set(RIL.PERSIST_AUTO_CALLLOG, "on");
                    } else {
                        SystemProperties.set(RIL.PERSIST_AUTO_CALLLOG, "off");
                    }
                }
            }
        };
        if ("on".equals(SystemProperties.get(PERSIST_AUTO_CALLLOG, "off"))) {
            SystemProperties.set(PERSIST_CALLLOG_FLAG, "0");
        }
        IntentFilter bandSignalfilter = new IntentFilter();
        bandSignalfilter.addAction(AUTOCALLLOG_BROADCAST);
        context.registerReceiver(this.mBandSignalReceiver, bandSignalfilter);
        riljLog("RIL: init preferredNetworkType=" + preferredNetworkType + " cdmaSubscription=" + cdmaSubscription + ")");
        this.mContext = context;
        this.mCdmaSubscription = cdmaSubscription;
        this.mPreferredNetworkType = preferredNetworkType;
        this.mPhoneType = 0;
        this.mPhoneId = instanceId;
        this.mIsMobileNetworkSupported = ((ConnectivityManager) context.getSystemService("connectivity")).isNetworkSupported(0);
        this.mRadioResponse = new RadioResponse(this);
        this.mRadioIndication = new RadioIndication(this);
        this.mOemHookResponse = new OemHookResponse(this);
        this.mOemHookIndication = new OemHookIndication(this);
        this.mRilHandler = new RilHandler();
        this.mRadioProxyDeathRecipient = new RadioProxyDeathRecipient();
        PowerManager pm = (PowerManager) context.getSystemService("power");
        this.mWakeLock = pm.newWakeLock(1, RILJ_LOG_TAG);
        this.mWakeLock.setReferenceCounted(false);
        this.mAckWakeLock = pm.newWakeLock(1, RILJ_ACK_WAKELOCK_NAME);
        this.mAckWakeLock.setReferenceCounted(false);
        this.mWakeLockTimeout = SystemProperties.getInt("ro.ril.wake_lock_timeout", 60000);
        this.mAckWakeLockTimeout = SystemProperties.getInt("ro.ril.wake_lock_timeout", 200);
        this.mWakeLockCount = 0;
        Looper lp = PhoneFactory.getCommonLooper();
        if (lp == null) {
            HandlerThread handlerThread = new HandlerThread("handlerThread" + instanceId);
            handlerThread.start();
            lp = handlerThread.getLooper();
        }
        this.mHangupInfoCollectHelper = new HangupInfoCollectHelper(context, lp, this, instanceId);
        this.mRILDefaultWorkSource = new WorkSource(context.getApplicationInfo().uid, context.getPackageName());
        TelephonyDevController tdc = TelephonyDevController.getInstance();
        TelephonyDevController.registerRIL(this);
        getRadioProxy(null);
        getOemHookProxy(null);
    }

    public void setOnNITZTime(Handler h, int what, Object obj) {
        super.setOnNITZTime(h, what, obj);
        if (this.mLastNITZTimeInfo != null) {
            this.mNITZTimeRegistrant.notifyRegistrant(new AsyncResult(null, this.mLastNITZTimeInfo, null));
        }
    }

    private void addRequest(RILRequest rr) {
        acquireWakeLock(rr, 0);
        synchronized (this.mRequestList) {
            rr.mStartTimeMs = SystemClock.elapsedRealtime();
            this.mRequestList.append(rr.mSerial, rr);
        }
    }

    private RILRequest obtainRequest(int request, Message result, WorkSource workSource) {
        RILRequest rr = RILRequest.obtain(request, result, workSource);
        addRequest(rr);
        return rr;
    }

    protected int obtainRequestSerial(int request, Message result, WorkSource workSource) {
        RILRequest rr = RILRequest.obtain(request, result, workSource);
        addRequest(rr);
        return rr.mSerial;
    }

    private void handleRadioProxyExceptionForRR(RILRequest rr, String caller, Exception e) {
        riljLoge(caller + ": " + e);
        resetProxyAndRequestList();
        this.mRilHandler.sendMessageDelayed(this.mRilHandler.obtainMessage(6, Long.valueOf(this.mRadioProxyCookie.incrementAndGet())), 4000);
    }

    private String convertNullToEmptyString(String string) {
        return string != null ? string : "";
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0038 A:{Splitter: B:3:0x0032, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0038, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0039, code:
            handleRadioProxyExceptionForRR(r2, "getIccCardStatus", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getIccCardStatus(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(1, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getIccCardStatus(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    public void supplyIccPin(String pin, Message result) {
        supplyIccPinForApp(pin, null, result);
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x004b A:{Splitter: B:3:0x003d, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x004b, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x004c, code:
            handleRadioProxyExceptionForRR(r2, "supplyIccPinForApp", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void supplyIccPinForApp(String pin, String aid, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(2, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " aid = " + aid);
            try {
                radioProxy.supplyIccPinForApp(rr.mSerial, convertNullToEmptyString(pin), convertNullToEmptyString(aid));
            } catch (Exception e) {
            }
        }
    }

    public void supplyIccPuk(String puk, String newPin, Message result) {
        supplyIccPukForApp(puk, newPin, null, result);
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x004f A:{Splitter: B:3:0x003d, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x004f, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0050, code:
            handleRadioProxyExceptionForRR(r2, "supplyIccPukForApp", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void supplyIccPukForApp(String puk, String newPin, String aid, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(3, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " aid = " + aid);
            try {
                radioProxy.supplyIccPukForApp(rr.mSerial, convertNullToEmptyString(puk), convertNullToEmptyString(newPin), convertNullToEmptyString(aid));
            } catch (Exception e) {
            }
        }
    }

    public void supplyIccPin2(String pin, Message result) {
        supplyIccPin2ForApp(pin, null, result);
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x004b A:{Splitter: B:3:0x003d, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x004b, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x004c, code:
            handleRadioProxyExceptionForRR(r2, "supplyIccPin2ForApp", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void supplyIccPin2ForApp(String pin, String aid, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(4, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " aid = " + aid);
            try {
                radioProxy.supplyIccPin2ForApp(rr.mSerial, convertNullToEmptyString(pin), convertNullToEmptyString(aid));
            } catch (Exception e) {
            }
        }
    }

    public void supplyIccPuk2(String puk2, String newPin2, Message result) {
        supplyIccPuk2ForApp(puk2, newPin2, null, result);
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x004f A:{Splitter: B:3:0x003d, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x004f, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0050, code:
            handleRadioProxyExceptionForRR(r2, "supplyIccPuk2ForApp", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void supplyIccPuk2ForApp(String puk, String newPin2, String aid, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(5, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " aid = " + aid);
            try {
                radioProxy.supplyIccPuk2ForApp(rr.mSerial, convertNullToEmptyString(puk), convertNullToEmptyString(newPin2), convertNullToEmptyString(aid));
            } catch (Exception e) {
            }
        }
    }

    public void changeIccPin(String oldPin, String newPin, Message result) {
        changeIccPinForApp(oldPin, newPin, null, result);
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0065 A:{Splitter: B:3:0x0053, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0065, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0066, code:
            handleRadioProxyExceptionForRR(r2, "changeIccPinForApp", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void changeIccPinForApp(String oldPin, String newPin, String aid, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(6, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " oldPin = " + oldPin + " newPin = " + newPin + " aid = " + aid);
            try {
                radioProxy.changeIccPinForApp(rr.mSerial, convertNullToEmptyString(oldPin), convertNullToEmptyString(newPin), convertNullToEmptyString(aid));
            } catch (Exception e) {
            }
        }
    }

    public void changeIccPin2(String oldPin2, String newPin2, Message result) {
        changeIccPin2ForApp(oldPin2, newPin2, null, result);
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0065 A:{Splitter: B:3:0x0053, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0065, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0066, code:
            handleRadioProxyExceptionForRR(r2, "changeIccPin2ForApp", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void changeIccPin2ForApp(String oldPin2, String newPin2, String aid, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(7, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " oldPin = " + oldPin2 + " newPin = " + newPin2 + " aid = " + aid);
            try {
                radioProxy.changeIccPin2ForApp(rr.mSerial, convertNullToEmptyString(oldPin2), convertNullToEmptyString(newPin2), convertNullToEmptyString(aid));
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0048 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0048, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0049, code:
            handleRadioProxyExceptionForRR(r2, "supplyNetworkDepersonalization", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void supplyNetworkDepersonalization(String netpin, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(8, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " netpin = " + netpin);
            try {
                radioProxy.supplyNetworkDepersonalization(rr.mSerial, convertNullToEmptyString(netpin));
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "getCurrentCalls", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getCurrentCalls(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(9, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getCurrentCalls(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    private void openAutoCallBbklog(int flag) {
        int calllog_flag = SystemProperties.getInt(PERSIST_CALLLOG_FLAG, 0);
        boolean bbkAutoLogFlag = "on".equals(SystemProperties.get(PERSIST_AUTO_CALLLOG, "off"));
        if (bbkAutoLogFlag && calllog_flag == 0) {
            Intent intent1 = new Intent(OPEN_BBLOG_BROADCAST);
            intent1.putExtra("logtype", "2");
            this.mContext.sendBroadcast(intent1);
            try {
                Thread.sleep(800);
            } catch (InterruptedException e) {
            }
            SystemProperties.set(PERSIST_CALLLOG_FLAG, "1");
        }
        Rlog.d(RILJ_LOG_TAG, "autolog openAutoCallBbklog  ,,bbkAutoLogFlag=" + bbkAutoLogFlag + ",,calllog_flag=" + calllog_flag + ",,,,flag = " + flag);
    }

    public void dial(String address, int clirMode, Message result) {
        dial(address, clirMode, null, result);
    }

    /* JADX WARNING: Removed duplicated region for block: B:10:0x007f A:{Splitter: B:6:0x0074, ExcHandler: android.os.RemoteException (r1_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:10:0x007f, code:
            r1 = move-exception;
     */
    /* JADX WARNING: Missing block: B:11:0x0080, code:
            handleRadioProxyExceptionForRR(r4, "dial", r1);
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void dial(String address, int clirMode, UUSInfo uusInfo, Message result) {
        RoamingStatusCollectHelper.getInstance().setCallNumber(this.mPhoneId.intValue(), address);
        openAutoCallBbklog(1);
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(10, result, this.mRILDefaultWorkSource);
            Dial dialInfo = new Dial();
            dialInfo.address = convertNullToEmptyString(address);
            dialInfo.clir = clirMode;
            if (uusInfo != null) {
                UusInfo info = new UusInfo();
                info.uusType = uusInfo.getType();
                info.uusDcs = uusInfo.getDcs();
                info.uusData = new String(uusInfo.getUserData());
                dialInfo.uusInfo.add(info);
            }
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.dial(rr.mSerial, dialInfo);
            } catch (Exception e) {
            }
        }
        this.mHangupInfoCollectHelper.dialSend();
    }

    public void getIMSI(Message result) {
        getIMSIForApp(null, result);
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0048 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0048, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0049, code:
            handleRadioProxyExceptionForRR(r2, "getIMSIForApp", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getIMSIForApp(String aid, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(11, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + ">  " + requestToString(rr.mRequest) + " aid = " + aid);
            try {
                radioProxy.getImsiForApp(rr.mSerial, convertNullToEmptyString(aid));
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:7:0x0049 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:7:0x0049, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:8:0x004a, code:
            handleRadioProxyExceptionForRR(r2, "hangupConnection", r0);
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void hangupConnection(int gsmIndex, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(12, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " gsmIndex = " + gsmIndex);
            try {
                radioProxy.hangup(rr.mSerial, gsmIndex);
            } catch (Exception e) {
            }
        }
        this.mHangupInfoCollectHelper.hangupSend(gsmIndex);
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "hangupWaitingOrBackground", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void hangupWaitingOrBackground(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(13, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.hangupWaitingOrBackground(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "hangupForegroundResumeBackground", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void hangupForegroundResumeBackground(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(14, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.hangupForegroundResumeBackground(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "switchWaitingOrHoldingAndActive", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void switchWaitingOrHoldingAndActive(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(15, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.switchWaitingOrHoldingAndActive(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "conference", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void conference(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(16, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.conference(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "rejectCall", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void rejectCall(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(17, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.rejectCall(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "getLastCallFailCause", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getLastCallFailCause(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(18, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getLastCallFailCause(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "getSignalStrength", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getSignalStrength(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(19, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getSignalStrength(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "getVoiceRegistrationState", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getVoiceRegistrationState(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(20, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getVoiceRegistrationState(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "getDataRegistrationState", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getDataRegistrationState(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(21, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getDataRegistrationState(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "getOperator", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getOperator(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(22, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getOperator(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0044 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0044, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0045, code:
            handleRadioProxyExceptionForRR(r2, "setRadioPower", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setRadioPower(boolean on, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(23, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " on = " + on);
            try {
                radioProxy.setRadioPower(rr.mSerial, on);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x004d A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x004d, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x004e, code:
            handleRadioProxyExceptionForRR(r2, "sendDtmf", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void sendDtmf(char c, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(24, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.sendDtmf(rr.mSerial, c + "");
            } catch (Exception e) {
            }
        }
    }

    private GsmSmsMessage constructGsmSendSmsRilRequest(String smscPdu, String pdu) {
        GsmSmsMessage msg = new GsmSmsMessage();
        if (smscPdu == null) {
            smscPdu = "";
        }
        msg.smscPdu = smscPdu;
        if (pdu == null) {
            pdu = "";
        }
        msg.pdu = pdu;
        return msg;
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x004c A:{Splitter: B:3:0x0037, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x004c, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x004d, code:
            handleRadioProxyExceptionForRR(r3, "sendSMS", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void sendSMS(String smscPdu, String pdu, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(25, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.sendSms(rr.mSerial, constructGsmSendSmsRilRequest(smscPdu, pdu));
                this.mMetrics.writeRilSendSms(this.mPhoneId.intValue(), rr.mSerial, 1, 1);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x004c A:{Splitter: B:3:0x0037, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x004c, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x004d, code:
            handleRadioProxyExceptionForRR(r3, "sendSMSExpectMore", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void sendSMSExpectMore(String smscPdu, String pdu, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(26, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.sendSMSExpectMore(rr.mSerial, constructGsmSendSmsRilRequest(smscPdu, pdu));
                this.mMetrics.writeRilSendSms(this.mPhoneId.intValue(), rr.mSerial, 1, 1);
            } catch (Exception e) {
            }
        }
    }

    private static int convertToHalMvnoType(String mvnoType) {
        if (mvnoType.equals("imsi")) {
            return 1;
        }
        if (mvnoType.equals("gid")) {
            return 2;
        }
        if (mvnoType.equals("spn")) {
            return 3;
        }
        return 0;
    }

    private static DataProfileInfo convertToHalDataProfile(DataProfile dp) {
        DataProfileInfo dpi = new DataProfileInfo();
        dpi.profileId = dp.profileId;
        dpi.apn = dp.apn;
        dpi.protocol = dp.protocol;
        dpi.roamingProtocol = dp.roamingProtocol;
        dpi.authType = dp.authType;
        dpi.user = dp.user;
        dpi.password = dp.password;
        dpi.type = dp.type;
        dpi.maxConnsTime = dp.maxConnsTime;
        dpi.maxConns = dp.maxConns;
        dpi.waitTime = dp.waitTime;
        dpi.enabled = dp.enabled;
        dpi.supportedApnTypesBitmap = dp.supportedApnTypesBitmap;
        dpi.bearerBitmap = dp.bearerBitmap;
        dpi.mtu = dp.mtu;
        dpi.mvnoType = convertToHalMvnoType(dp.mvnoType);
        dpi.mvnoMatchData = dp.mvnoMatchData;
        return dpi;
    }

    private static int convertToHalResetNvType(int resetType) {
        switch (resetType) {
            case 1:
                return 0;
            case 2:
                return 1;
            case 3:
                return 2;
            default:
                return -1;
        }
    }

    static DataCallResponse convertDataCallResult(SetupDataCallResult dcResult) {
        return new DataCallResponse(dcResult.status, dcResult.suggestedRetryTime, dcResult.cid, dcResult.active, dcResult.type, dcResult.ifname, dcResult.addresses, dcResult.dnses, dcResult.gateways, dcResult.pcscf, dcResult.mtu);
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0096 A:{Splitter: B:3:0x006f, ExcHandler: android.os.RemoteException (r13_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0096, code:
            r13 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0097, code:
            handleRadioProxyExceptionForRR(r14, "setupDataCall", r13);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setupDataCall(int radioTechnology, DataProfile dataProfile, boolean isRoaming, boolean allowRoaming, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(27, result, this.mRILDefaultWorkSource);
            DataProfileInfo dpi = convertToHalDataProfile(dataProfile);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ",radioTechnology=" + radioTechnology + ",isRoaming=" + isRoaming + ",allowRoaming=" + allowRoaming + "," + dataProfile);
            try {
                radioProxy.setupDataCall(rr.mSerial, radioTechnology, dpi, dataProfile.modemCognitive, allowRoaming, isRoaming);
                this.mMetrics.writeRilSetupDataCall(this.mPhoneId.intValue(), rr.mSerial, radioTechnology, dpi.profileId, dpi.apn, dpi.authType, dpi.protocol);
            } catch (Exception e) {
            }
        }
    }

    public void iccIO(int command, int fileId, String path, int p1, int p2, int p3, String data, String pin2, Message result) {
        iccIOForApp(command, fileId, path, p1, p2, p3, data, pin2, null, result);
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x00c4 A:{Splitter: B:3:0x00be, ExcHandler: android.os.RemoteException (r1_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x00c4, code:
            r1 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x00c5, code:
            handleRadioProxyExceptionForRR(r4, "iccIOForApp", r1);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void iccIOForApp(int command, int fileId, String path, int p1, int p2, int p3, String data, String pin2, String aid, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(28, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> iccIO: " + requestToString(rr.mRequest) + " command = 0x" + Integer.toHexString(command) + " fileId = 0x" + Integer.toHexString(fileId) + " path = " + path + " p1 = " + p1 + " p2 = " + p2 + " p3 = " + " data = " + data + " aid = " + aid);
            IccIo iccIo = new IccIo();
            iccIo.command = command;
            iccIo.fileId = fileId;
            iccIo.path = convertNullToEmptyString(path);
            iccIo.p1 = p1;
            iccIo.p2 = p2;
            iccIo.p3 = p3;
            iccIo.data = convertNullToEmptyString(data);
            iccIo.pin2 = convertNullToEmptyString(pin2);
            iccIo.aid = convertNullToEmptyString(aid);
            try {
                radioProxy.iccIOForApp(rr.mSerial, iccIo);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x004b A:{Splitter: B:3:0x0041, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x004b, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x004c, code:
            handleRadioProxyExceptionForRR(r3, "sendUSSD", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void sendUSSD(String ussd, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(29, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " ussd = " + "*******");
            try {
                radioProxy.sendUssd(rr.mSerial, convertNullToEmptyString(ussd));
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "cancelPendingUssd", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void cancelPendingUssd(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(30, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.cancelPendingUssd(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "getCLIR", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getCLIR(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(31, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getClir(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0044 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0044, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0045, code:
            handleRadioProxyExceptionForRR(r2, "setCLIR", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setCLIR(int clirMode, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(32, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " clirMode = " + clirMode);
            try {
                radioProxy.setClir(rr.mSerial, clirMode);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0067 A:{Splitter: B:3:0x0061, ExcHandler: android.os.RemoteException (r1_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0067, code:
            r1 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0068, code:
            handleRadioProxyExceptionForRR(r3, "queryCallForwardStatus", r1);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void queryCallForwardStatus(int cfReason, int serviceClass, String number, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(33, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " cfreason = " + cfReason + " serviceClass = " + serviceClass);
            CallForwardInfo cfInfo = new CallForwardInfo();
            cfInfo.reason = cfReason;
            cfInfo.serviceClass = serviceClass;
            cfInfo.toa = PhoneNumberUtils.toaFromString(number);
            cfInfo.number = convertNullToEmptyString(number);
            cfInfo.timeSeconds = 0;
            try {
                radioProxy.getCallForwardStatus(rr.mSerial, cfInfo);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x007e A:{Splitter: B:3:0x0078, ExcHandler: android.os.RemoteException (r1_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x007e, code:
            r1 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x007f, code:
            handleRadioProxyExceptionForRR(r3, "setCallForward", r1);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setCallForward(int action, int cfReason, int serviceClass, String number, int timeSeconds, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(34, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " action = " + action + " cfReason = " + cfReason + " serviceClass = " + serviceClass + " timeSeconds = " + timeSeconds);
            CallForwardInfo cfInfo = new CallForwardInfo();
            cfInfo.status = action;
            cfInfo.reason = cfReason;
            cfInfo.serviceClass = serviceClass;
            cfInfo.toa = PhoneNumberUtils.toaFromString(number);
            cfInfo.number = convertNullToEmptyString(number);
            cfInfo.timeSeconds = timeSeconds;
            try {
                radioProxy.setCallForward(rr.mSerial, cfInfo);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0044 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0044, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0045, code:
            handleRadioProxyExceptionForRR(r2, "queryCallWaiting", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void queryCallWaiting(int serviceClass, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(35, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " serviceClass = " + serviceClass);
            try {
                radioProxy.getCallWaiting(rr.mSerial, serviceClass);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x004f A:{Splitter: B:3:0x0049, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x004f, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0050, code:
            handleRadioProxyExceptionForRR(r2, "setCallWaiting", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setCallWaiting(boolean enable, int serviceClass, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(36, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " enable = " + enable + " serviceClass = " + serviceClass);
            try {
                radioProxy.setCallWaiting(rr.mSerial, enable, serviceClass);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x004f A:{Splitter: B:3:0x0049, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x004f, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0050, code:
            handleRadioProxyExceptionForRR(r2, "acknowledgeLastIncomingGsmSms", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void acknowledgeLastIncomingGsmSms(boolean success, int cause, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(37, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " success = " + success + " cause = " + cause);
            try {
                radioProxy.acknowledgeLastIncomingGsmSms(rr.mSerial, success, cause);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0056 A:{Splitter: B:3:0x0043, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0056, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0057, code:
            handleRadioProxyExceptionForRR(r2, "acceptCall", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void acceptCall(Message result) {
        RoamingStatusCollectHelper.getInstance().setCallNumber(this.mPhoneId.intValue(), "");
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(40, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.acceptCall(rr.mSerial);
                this.mMetrics.writeRilAnswer(this.mPhoneId.intValue(), rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:8:0x0061 A:{Splitter: B:3:0x004a, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:8:0x0061, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:9:0x0062, code:
            handleRadioProxyExceptionForRR(r2, "deactivateDataCall", r0);
     */
    /* JADX WARNING: Missing block: B:12:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void deactivateDataCall(int cid, int reason, Message result) {
        boolean z = false;
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(41, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " cid = " + cid + " reason = " + reason);
            try {
                int i = rr.mSerial;
                if (reason != 0) {
                    z = true;
                }
                radioProxy.deactivateDataCall(i, cid, z);
                this.mMetrics.writeRilDeactivateDataCall(this.mPhoneId.intValue(), rr.mSerial, cid, reason);
            } catch (Exception e) {
            }
        }
    }

    public void queryFacilityLock(String facility, String password, int serviceClass, Message result) {
        queryFacilityLockForApp(facility, password, serviceClass, null, result);
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0067 A:{Splitter: B:3:0x0054, ExcHandler: android.os.RemoteException (r6_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0067, code:
            r6 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0068, code:
            handleRadioProxyExceptionForRR(r7, "getFacilityLockForApp", r6);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void queryFacilityLockForApp(String facility, String password, int serviceClass, String appId, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(42, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " facility = " + facility + " serviceClass = " + serviceClass + " appId = " + appId);
            try {
                radioProxy.getFacilityLockForApp(rr.mSerial, convertNullToEmptyString(facility), convertNullToEmptyString(password), serviceClass, convertNullToEmptyString(appId));
            } catch (Exception e) {
            }
        }
    }

    public void setFacilityLock(String facility, boolean lockState, String password, int serviceClass, Message result) {
        setFacilityLockForApp(facility, lockState, password, serviceClass, null, result);
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0073 A:{Splitter: B:3:0x005f, ExcHandler: android.os.RemoteException (r7_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0073, code:
            r7 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0074, code:
            handleRadioProxyExceptionForRR(r8, "setFacilityLockForApp", r7);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setFacilityLockForApp(String facility, boolean lockState, String password, int serviceClass, String appId, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(43, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " facility = " + facility + " lockstate = " + lockState + " serviceClass = " + serviceClass + " appId = " + appId);
            try {
                radioProxy.setFacilityLockForApp(rr.mSerial, convertNullToEmptyString(facility), lockState, convertNullToEmptyString(password), serviceClass, convertNullToEmptyString(appId));
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0050 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0050, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0051, code:
            handleRadioProxyExceptionForRR(r2, "changeBarringPassword", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void changeBarringPassword(String facility, String oldPwd, String newPwd, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(44, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + "facility = " + facility);
            try {
                radioProxy.setBarringPassword(rr.mSerial, convertNullToEmptyString(facility), convertNullToEmptyString(oldPwd), convertNullToEmptyString(newPwd));
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "getNetworkSelectionMode", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getNetworkSelectionMode(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(45, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getNetworkSelectionMode(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "setNetworkSelectionModeAutomatic", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setNetworkSelectionModeAutomatic(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(46, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.setNetworkSelectionModeAutomatic(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0048 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0048, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0049, code:
            handleRadioProxyExceptionForRR(r2, "setNetworkSelectionModeManual", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setNetworkSelectionModeManual(String operatorNumeric, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(47, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " operatorNumeric = " + operatorNumeric);
            try {
                radioProxy.setNetworkSelectionModeManual(rr.mSerial, convertNullToEmptyString(operatorNumeric));
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "getAvailableNetworks", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getAvailableNetworks(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(48, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getAvailableNetworks(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:32:0x00fe A:{Splitter: B:30:0x00f7, ExcHandler: android.os.RemoteException (r5_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:32:0x00fe, code:
            r5 = move-exception;
     */
    /* JADX WARNING: Missing block: B:33:0x00ff, code:
            handleRadioProxyExceptionForRR(r10, "startNetworkScan", r5);
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void startNetworkScan(NetworkScanRequest nsr, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            android.hardware.radio.V1_1.IRadio radioProxy11 = android.hardware.radio.V1_1.IRadio.castFrom(radioProxy);
            if (radioProxy11 != null) {
                android.hardware.radio.V1_1.NetworkScanRequest request = new android.hardware.radio.V1_1.NetworkScanRequest();
                request.type = nsr.scanType;
                request.interval = 60;
                for (RadioAccessSpecifier ras : nsr.specifiers) {
                    List<Integer> bands;
                    android.hardware.radio.V1_1.RadioAccessSpecifier s = new android.hardware.radio.V1_1.RadioAccessSpecifier();
                    s.radioAccessNetwork = ras.radioAccessNetwork;
                    switch (ras.radioAccessNetwork) {
                        case 1:
                            bands = s.geranBands;
                            break;
                        case 2:
                            bands = s.utranBands;
                            break;
                        case 3:
                            bands = s.eutranBands;
                            break;
                        default:
                            Log.wtf(RILJ_LOG_TAG, "radioAccessNetwork " + ras.radioAccessNetwork + " not supported!");
                            return;
                    }
                    if (ras.bands != null) {
                        for (int band : ras.bands) {
                            bands.add(Integer.valueOf(band));
                        }
                    }
                    if (ras.channels != null) {
                        for (int channel : ras.channels) {
                            s.channels.add(Integer.valueOf(channel));
                        }
                    }
                    request.specifiers.add(s);
                }
                RILRequest rr = obtainRequest(142, result, this.mRILDefaultWorkSource);
                riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
                try {
                    radioProxy11.startNetworkScan(rr.mSerial, request);
                } catch (Exception e) {
                }
            } else if (result != null) {
                AsyncResult.forMessage(result, null, CommandException.fromRilErrno(6));
                result.sendToTarget();
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:9:0x004e A:{Splitter: B:7:0x0048, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:9:0x004e, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:10:0x004f, code:
            handleRadioProxyExceptionForRR(r3, "stopNetworkScan", r0);
     */
    /* JADX WARNING: Missing block: B:14:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void stopNetworkScan(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            android.hardware.radio.V1_1.IRadio radioProxy11 = android.hardware.radio.V1_1.IRadio.castFrom(radioProxy);
            if (radioProxy11 != null) {
                RILRequest rr = obtainRequest(143, result, this.mRILDefaultWorkSource);
                riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
                try {
                    radioProxy11.stopNetworkScan(rr.mSerial);
                } catch (Exception e) {
                }
            } else if (result != null) {
                AsyncResult.forMessage(result, null, CommandException.fromRilErrno(6));
                result.sendToTarget();
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x004d A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x004d, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x004e, code:
            handleRadioProxyExceptionForRR(r2, "startDtmf", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void startDtmf(char c, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(49, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.startDtmf(rr.mSerial, c + "");
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "stopDtmf", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void stopDtmf(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(50, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.stopDtmf(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0044 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0044, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0045, code:
            handleRadioProxyExceptionForRR(r2, "separateConnection", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void separateConnection(int gsmIndex, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(52, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " gsmIndex = " + gsmIndex);
            try {
                radioProxy.separateConnection(rr.mSerial, gsmIndex);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "getBasebandVersion", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getBasebandVersion(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(51, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getBasebandVersion(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0044 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0044, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0045, code:
            handleRadioProxyExceptionForRR(r2, "setMute", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setMute(boolean enableMute, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(53, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " enableMute = " + enableMute);
            try {
                radioProxy.setMute(rr.mSerial, enableMute);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "getMute", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getMute(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(54, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getMute(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "queryCLIP", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void queryCLIP(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(55, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getClip(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    @Deprecated
    public void getPDPContextList(Message result) {
        getDataCallList(result);
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "getDataCallList", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getDataCallList(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(57, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getDataCallList(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0053 A:{Splitter: B:3:0x0049, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0053, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0054, code:
            handleRadioProxyExceptionForRR(r2, "invokeOemRilRequestStrings", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void invokeOemRilRequestRaw(byte[] data, Message response) {
        IOemHook oemHookProxy = getOemHookProxy(response);
        if (oemHookProxy != null) {
            RILRequest rr = obtainRequest(59, response, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + "[" + IccUtils.bytesToHexString(data) + "]");
            try {
                oemHookProxy.sendRequestRaw(rr.mSerial, primitiveArrayToArrayList(data));
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:9:0x0071 A:{Splitter: B:7:0x0062, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:9:0x0071, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:10:0x0072, code:
            handleRadioProxyExceptionForRR(r4, "invokeOemRilRequestStrings", r0);
     */
    /* JADX WARNING: Missing block: B:14:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void invokeOemRilRequestStrings(String[] strings, Message result) {
        IOemHook oemHookProxy = getOemHookProxy(result);
        if (oemHookProxy != null) {
            RILRequest rr = obtainRequest(60, result, this.mRILDefaultWorkSource);
            String logStr = "";
            for (String str : strings) {
                logStr = logStr + str + " ";
            }
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " strings = " + logStr);
            try {
                oemHookProxy.sendRequestStrings(rr.mSerial, new ArrayList(Arrays.asList(strings)));
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0044 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0044, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0045, code:
            handleRadioProxyExceptionForRR(r2, "setSuppServiceNotifications", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setSuppServiceNotifications(boolean enable, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(62, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " enable = " + enable);
            try {
                radioProxy.setSuppServiceNotifications(rr.mSerial, enable);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x002b A:{Splitter: B:3:0x0025, ExcHandler: android.os.RemoteException (r1_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x002b, code:
            r1 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x002c, code:
            handleRadioProxyExceptionForRR(r3, "writeSmsToSim", r1);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void writeSmsToSim(int status, String smsc, String pdu, Message result) {
        status = translateStatus(status);
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(63, result, this.mRILDefaultWorkSource);
            SmsWriteArgs args = new SmsWriteArgs();
            args.status = status;
            args.smsc = convertNullToEmptyString(smsc);
            args.pdu = convertNullToEmptyString(pdu);
            try {
                radioProxy.writeSmsToSim(rr.mSerial, args);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0014 A:{Splitter: B:3:0x000e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0014, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0015, code:
            handleRadioProxyExceptionForRR(r2, "deleteSmsOnSim", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void deleteSmsOnSim(int index, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(64, result, this.mRILDefaultWorkSource);
            try {
                radioProxy.deleteSmsOnSim(rr.mSerial, index);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0044 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0044, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0045, code:
            handleRadioProxyExceptionForRR(r2, "setBandMode", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setBandMode(int bandMode, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(65, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " bandMode = " + bandMode);
            try {
                radioProxy.setBandMode(rr.mSerial, bandMode);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "queryAvailableBandMode", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void queryAvailableBandMode(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(66, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getAvailableBandModes(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0048 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0048, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0049, code:
            handleRadioProxyExceptionForRR(r2, "sendEnvelope", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void sendEnvelope(String contents, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(69, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " contents = " + contents);
            try {
                radioProxy.sendEnvelope(rr.mSerial, convertNullToEmptyString(contents));
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0048 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0048, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0049, code:
            handleRadioProxyExceptionForRR(r2, "sendTerminalResponse", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void sendTerminalResponse(String contents, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(70, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " contents = " + contents);
            try {
                radioProxy.sendTerminalResponseToSim(rr.mSerial, convertNullToEmptyString(contents));
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0048 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0048, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0049, code:
            handleRadioProxyExceptionForRR(r2, "sendEnvelopeWithStatus", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void sendEnvelopeWithStatus(String contents, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(107, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " contents = " + contents);
            try {
                radioProxy.sendEnvelopeWithStatus(rr.mSerial, convertNullToEmptyString(contents));
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "explicitCallTransfer", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void explicitCallTransfer(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(72, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.explicitCallTransfer(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0051 A:{Splitter: B:3:0x004b, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0051, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0052, code:
            handleRadioProxyExceptionForRR(r2, "setPreferredNetworkType", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setPreferredNetworkType(int networkType, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(73, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " networkType = " + networkType);
            this.mPreferredNetworkType = networkType;
            this.mMetrics.writeSetPreferredNetworkType(this.mPhoneId.intValue(), networkType);
            try {
                radioProxy.setPreferredNetworkType(rr.mSerial, networkType);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "getPreferredNetworkType", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getPreferredNetworkType(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(74, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getPreferredNetworkType(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x003b A:{Splitter: B:3:0x0035, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x003b, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003c, code:
            handleRadioProxyExceptionForRR(r2, "getNeighboringCids", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getNeighboringCids(Message result, WorkSource workSource) {
        workSource = getDeafultWorkSourceIfInvalid(workSource);
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(75, result, workSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getNeighboringCids(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0044 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0044, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0045, code:
            handleRadioProxyExceptionForRR(r2, "setLocationUpdates", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setLocationUpdates(boolean enable, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(76, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " enable = " + enable);
            try {
                radioProxy.setLocationUpdates(rr.mSerial, enable);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0045 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0045, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0046, code:
            handleRadioProxyExceptionForRR(r2, "setCdmaSubscriptionSource", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setCdmaSubscriptionSource(int cdmaSubscription, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(77, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " cdmaSubscription = " + cdmaSubscription);
            try {
                radioProxy.setCdmaSubscriptionSource(rr.mSerial, 0);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "queryCdmaRoamingPreference", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void queryCdmaRoamingPreference(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(79, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getCdmaRoamingPreference(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0044 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0044, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0045, code:
            handleRadioProxyExceptionForRR(r2, "setCdmaRoamingPreference", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setCdmaRoamingPreference(int cdmaRoamingType, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(78, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " cdmaRoamingType = " + cdmaRoamingType);
            try {
                radioProxy.setCdmaRoamingPreference(rr.mSerial, cdmaRoamingType);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "queryTTYMode", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void queryTTYMode(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(81, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getTTYMode(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0044 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0044, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0045, code:
            handleRadioProxyExceptionForRR(r2, "setTTYMode", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setTTYMode(int ttyMode, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(80, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " ttyMode = " + ttyMode);
            try {
                radioProxy.setTTYMode(rr.mSerial, ttyMode);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0044 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0044, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0045, code:
            handleRadioProxyExceptionForRR(r2, "setPreferredVoicePrivacy", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setPreferredVoicePrivacy(boolean enable, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(82, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " enable = " + enable);
            try {
                radioProxy.setPreferredVoicePrivacy(rr.mSerial, enable);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "getPreferredVoicePrivacy", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getPreferredVoicePrivacy(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(83, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getPreferredVoicePrivacy(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0048 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0048, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0049, code:
            handleRadioProxyExceptionForRR(r2, "sendCDMAFeatureCode", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void sendCDMAFeatureCode(String featureCode, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(84, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " featureCode = " + featureCode);
            try {
                radioProxy.sendCDMAFeatureCode(rr.mSerial, convertNullToEmptyString(featureCode));
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x005e A:{Splitter: B:3:0x0054, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x005e, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x005f, code:
            handleRadioProxyExceptionForRR(r2, "sendBurstDtmf", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void sendBurstDtmf(String dtmfString, int on, int off, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(85, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " dtmfString = " + dtmfString + " on = " + on + " off = " + off);
            try {
                radioProxy.sendBurstDtmf(rr.mSerial, convertNullToEmptyString(dtmfString), on, off);
            } catch (Exception e) {
            }
        }
    }

    private void constructCdmaSendSmsRilRequest(CdmaSmsMessage msg, byte[] pdu) {
        boolean z = true;
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(pdu));
        try {
            boolean z2;
            int i;
            msg.teleserviceId = dis.readInt();
            if (((byte) dis.readInt()) == (byte) 1) {
                z2 = true;
            } else {
                z2 = false;
            }
            msg.isServicePresent = z2;
            msg.serviceCategory = dis.readInt();
            msg.address.digitMode = dis.read();
            msg.address.numberMode = dis.read();
            msg.address.numberType = dis.read();
            msg.address.numberPlan = dis.read();
            int addrNbrOfDigits = (byte) dis.read();
            for (i = 0; i < addrNbrOfDigits; i++) {
                msg.address.digits.add(Byte.valueOf(dis.readByte()));
            }
            msg.subAddress.subaddressType = dis.read();
            CdmaSmsSubaddress cdmaSmsSubaddress = msg.subAddress;
            if (((byte) dis.read()) != (byte) 1) {
                z = false;
            }
            cdmaSmsSubaddress.odd = z;
            int subaddrNbrOfDigits = (byte) dis.read();
            for (i = 0; i < subaddrNbrOfDigits; i++) {
                msg.subAddress.digits.add(Byte.valueOf(dis.readByte()));
            }
            int bearerDataLength = dis.read();
            for (i = 0; i < bearerDataLength; i++) {
                msg.bearerData.add(Byte.valueOf(dis.readByte()));
            }
        } catch (IOException ex) {
            riljLog("sendSmsCdma: conversion from input stream to object failed: " + ex);
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0050 A:{Splitter: B:3:0x003b, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0050, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0051, code:
            handleRadioProxyExceptionForRR(r3, "sendCdmaSms", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void sendCdmaSms(byte[] pdu, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(87, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            CdmaSmsMessage msg = new CdmaSmsMessage();
            constructCdmaSendSmsRilRequest(msg, pdu);
            try {
                radioProxy.sendCdmaSms(rr.mSerial, msg);
                this.mMetrics.writeRilSendSms(this.mPhoneId.intValue(), rr.mSerial, 2, 2);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:9:0x005d A:{Splitter: B:6:0x0055, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:9:0x005d, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:10:0x005e, code:
            handleRadioProxyExceptionForRR(r3, "acknowledgeLastIncomingCdmaSms", r0);
     */
    /* JADX WARNING: Missing block: B:13:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void acknowledgeLastIncomingCdmaSms(boolean success, int cause, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(88, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " success = " + success + " cause = " + cause);
            CdmaSmsAck msg = new CdmaSmsAck();
            msg.errorClass = success ? 0 : 1;
            msg.smsCauseCode = cause;
            try {
                radioProxy.acknowledgeLastIncomingCdmaSms(rr.mSerial, msg);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "getGsmBroadcastConfig", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getGsmBroadcastConfig(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(89, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getGsmBroadcastConfig(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:11:0x0098 A:{Splitter: B:9:0x0092, ExcHandler: android.os.RemoteException (r1_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:11:0x0098, code:
            r1 = move-exception;
     */
    /* JADX WARNING: Missing block: B:12:0x0099, code:
            handleRadioProxyExceptionForRR(r6, "setGsmBroadcastConfig", r1);
     */
    /* JADX WARNING: Missing block: B:16:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setGsmBroadcastConfig(SmsBroadcastConfigInfo[] config, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            int i;
            RILRequest rr = obtainRequest(90, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " with " + config.length + " configs : ");
            for (SmsBroadcastConfigInfo smsBroadcastConfigInfo : config) {
                riljLog(smsBroadcastConfigInfo.toString());
            }
            ArrayList<GsmBroadcastSmsConfigInfo> configs = new ArrayList();
            int numOfConfig = config.length;
            for (i = 0; i < numOfConfig; i++) {
                GsmBroadcastSmsConfigInfo info = new GsmBroadcastSmsConfigInfo();
                info.fromServiceId = config[i].getFromServiceId();
                info.toServiceId = config[i].getToServiceId();
                info.fromCodeScheme = config[i].getFromCodeScheme();
                info.toCodeScheme = config[i].getToCodeScheme();
                info.selected = config[i].isSelected();
                configs.add(info);
            }
            try {
                radioProxy.setGsmBroadcastConfig(rr.mSerial, configs);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0044 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0044, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0045, code:
            handleRadioProxyExceptionForRR(r2, "setGsmBroadcastActivation", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setGsmBroadcastActivation(boolean activate, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(91, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " activate = " + activate);
            try {
                radioProxy.setGsmBroadcastActivation(rr.mSerial, activate);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "getCdmaBroadcastConfig", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getCdmaBroadcastConfig(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(92, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getCdmaBroadcastConfig(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:15:0x0098 A:{Splitter: B:13:0x0092, ExcHandler: android.os.RemoteException (r3_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:15:0x0098, code:
            r3 = move-exception;
     */
    /* JADX WARNING: Missing block: B:16:0x0099, code:
            handleRadioProxyExceptionForRR(r8, "setCdmaBroadcastConfig", r3);
     */
    /* JADX WARNING: Missing block: B:21:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setCdmaBroadcastConfig(CdmaSmsBroadcastConfigInfo[] configs, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(93, result, this.mRILDefaultWorkSource);
            ArrayList<CdmaBroadcastSmsConfigInfo> halConfigs = new ArrayList();
            for (CdmaSmsBroadcastConfigInfo config : configs) {
                for (int i = config.getFromServiceCategory(); i <= config.getToServiceCategory(); i++) {
                    CdmaBroadcastSmsConfigInfo info = new CdmaBroadcastSmsConfigInfo();
                    info.serviceCategory = i;
                    info.language = config.getLanguage();
                    info.selected = config.isSelected();
                    halConfigs.add(info);
                }
            }
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " with " + halConfigs.size() + " configs : ");
            for (CdmaBroadcastSmsConfigInfo config2 : halConfigs) {
                riljLog(config2.toString());
            }
            try {
                radioProxy.setCdmaBroadcastConfig(rr.mSerial, halConfigs);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0044 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0044, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0045, code:
            handleRadioProxyExceptionForRR(r2, "setCdmaBroadcastActivation", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setCdmaBroadcastActivation(boolean activate, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(94, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " activate = " + activate);
            try {
                radioProxy.setCdmaBroadcastActivation(rr.mSerial, activate);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "getCDMASubscription", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getCDMASubscription(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(95, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getCDMASubscription(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0028 A:{Splitter: B:3:0x0022, ExcHandler: android.os.RemoteException (r1_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0028, code:
            r1 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0029, code:
            handleRadioProxyExceptionForRR(r3, "writeSmsToRuim", r1);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void writeSmsToRuim(int status, String pdu, Message result) {
        status = translateStatus(status);
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(96, result, this.mRILDefaultWorkSource);
            CdmaSmsWriteArgs args = new CdmaSmsWriteArgs();
            args.status = status;
            constructCdmaSendSmsRilRequest(args.message, IccUtils.hexStringToBytes(pdu));
            try {
                radioProxy.writeSmsToRuim(rr.mSerial, args);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0014 A:{Splitter: B:3:0x000e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0014, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0015, code:
            handleRadioProxyExceptionForRR(r2, "deleteSmsOnRuim", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void deleteSmsOnRuim(int index, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(97, result, this.mRILDefaultWorkSource);
            try {
                radioProxy.deleteSmsOnRuim(rr.mSerial, index);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "getDeviceIdentity", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getDeviceIdentity(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(98, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getDeviceIdentity(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "exitEmergencyCallbackMode", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void exitEmergencyCallbackMode(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(99, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.exitEmergencyCallbackMode(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "getSmscAddress", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getSmscAddress(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(100, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getSmscAddress(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0048 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0048, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0049, code:
            handleRadioProxyExceptionForRR(r2, "setSmscAddress", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setSmscAddress(String address, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(101, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " address = " + address);
            try {
                radioProxy.setSmscAddress(rr.mSerial, convertNullToEmptyString(address));
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0044 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0044, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0045, code:
            handleRadioProxyExceptionForRR(r2, "reportSmsMemoryStatus", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void reportSmsMemoryStatus(boolean available, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(102, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " available = " + available);
            try {
                radioProxy.reportSmsMemoryStatus(rr.mSerial, available);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "reportStkServiceIsRunning", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void reportStkServiceIsRunning(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(103, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.reportStkServiceIsRunning(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "getCdmaSubscriptionSource", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getCdmaSubscriptionSource(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(104, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getCdmaSubscriptionSource(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0048 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0048, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0049, code:
            handleRadioProxyExceptionForRR(r2, "requestIsimAuthentication", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void requestIsimAuthentication(String nonce, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(105, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " nonce = " + nonce);
            try {
                radioProxy.requestIsimAuthentication(rr.mSerial, convertNullToEmptyString(nonce));
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0048 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0048, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0049, code:
            handleRadioProxyExceptionForRR(r2, "acknowledgeIncomingGsmSmsWithPdu", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void acknowledgeIncomingGsmSmsWithPdu(boolean success, String ackPdu, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(106, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " success = " + success);
            try {
                radioProxy.acknowledgeIncomingGsmSmsWithPdu(rr.mSerial, success, convertNullToEmptyString(ackPdu));
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "getVoiceRadioTechnology", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getVoiceRadioTechnology(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(108, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getVoiceRadioTechnology(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x003b A:{Splitter: B:3:0x0035, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x003b, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003c, code:
            handleRadioProxyExceptionForRR(r2, "getCellInfoList", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getCellInfoList(Message result, WorkSource workSource) {
        workSource = getDeafultWorkSourceIfInvalid(workSource);
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(109, result, workSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getCellInfoList(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0046 A:{Splitter: B:3:0x0040, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0046, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0047, code:
            handleRadioProxyExceptionForRR(r2, "setCellInfoListRate", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setCellInfoListRate(int rateInMillis, Message result, WorkSource workSource) {
        workSource = getDeafultWorkSourceIfInvalid(workSource);
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(110, result, workSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " rateInMillis = " + rateInMillis);
            try {
                radioProxy.setCellInfoListRate(rr.mSerial, rateInMillis);
            } catch (Exception e) {
            }
        }
    }

    @VivoHook(hookType = VivoHookType.CHANGE_CODE)
    void setCellInfoListRate() {
        setCellInfoListRate(5000, null, this.mRILDefaultWorkSource);
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0043 A:{Splitter: B:3:0x0037, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0043, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0044, code:
            handleRadioProxyExceptionForRR(r2, "setInitialAttachApn", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setInitialAttachApn(DataProfile dataProfile, boolean isRoaming, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(111, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + dataProfile);
            try {
                radioProxy.setInitialAttachApn(rr.mSerial, convertToHalDataProfile(dataProfile), dataProfile.modemCognitive, isRoaming);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "getImsRegistrationState", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getImsRegistrationState(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(112, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getImsRegistrationState(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:8:0x0063 A:{Splitter: B:5:0x004c, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:8:0x0063, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:9:0x0064, code:
            handleRadioProxyExceptionForRR(r4, "sendImsGsmSms", r0);
     */
    /* JADX WARNING: Missing block: B:12:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void sendImsGsmSms(String smscPdu, String pdu, int retry, int messageRef, Message result) {
        boolean z = true;
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(113, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            ImsSmsMessage msg = new ImsSmsMessage();
            msg.tech = 1;
            if (((byte) retry) <= (byte) 0) {
                z = false;
            }
            msg.retry = z;
            msg.messageRef = messageRef;
            msg.gsmMessage.add(constructGsmSendSmsRilRequest(smscPdu, pdu));
            try {
                radioProxy.sendImsSms(rr.mSerial, msg);
                this.mMetrics.writeRilSendSms(this.mPhoneId.intValue(), rr.mSerial, 3, 1);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:8:0x0068 A:{Splitter: B:5:0x0051, ExcHandler: android.os.RemoteException (r1_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:8:0x0068, code:
            r1 = move-exception;
     */
    /* JADX WARNING: Missing block: B:9:0x0069, code:
            handleRadioProxyExceptionForRR(r4, "sendImsCdmaSms", r1);
     */
    /* JADX WARNING: Missing block: B:12:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void sendImsCdmaSms(byte[] pdu, int retry, int messageRef, Message result) {
        boolean z = true;
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(113, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            ImsSmsMessage msg = new ImsSmsMessage();
            msg.tech = 2;
            if (((byte) retry) <= (byte) 0) {
                z = false;
            }
            msg.retry = z;
            msg.messageRef = messageRef;
            CdmaSmsMessage cdmaMsg = new CdmaSmsMessage();
            constructCdmaSendSmsRilRequest(cdmaMsg, pdu);
            msg.cdmaMessage.add(cdmaMsg);
            try {
                radioProxy.sendImsSms(rr.mSerial, msg);
                this.mMetrics.writeRilSendSms(this.mPhoneId.intValue(), rr.mSerial, 3, 1);
            } catch (Exception e) {
            }
        }
    }

    private SimApdu createSimApdu(int channel, int cla, int instruction, int p1, int p2, int p3, String data) {
        SimApdu msg = new SimApdu();
        msg.sessionId = channel;
        msg.cla = cla;
        msg.instruction = instruction;
        msg.p1 = p1;
        msg.p2 = p2;
        msg.p3 = p3;
        msg.data = convertNullToEmptyString(data);
        return msg;
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0091 A:{Splitter: B:3:0x008b, ExcHandler: android.os.RemoteException (r9_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0091, code:
            r9 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0092, code:
            handleRadioProxyExceptionForRR(r12, "iccTransmitApduBasicChannel", r9);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void iccTransmitApduBasicChannel(int cla, int instruction, int p1, int p2, int p3, String data, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(114, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " cla = " + cla + " instruction = " + instruction + " p1 = " + p1 + " p2 = " + " p3 = " + p3 + " data = " + data);
            try {
                radioProxy.iccTransmitApduBasicChannel(rr.mSerial, createSimApdu(0, cla, instruction, p1, p2, p3, data));
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0053 A:{Splitter: B:3:0x0049, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0053, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0054, code:
            handleRadioProxyExceptionForRR(r2, "iccOpenLogicalChannel", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void iccOpenLogicalChannel(String aid, int p2, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(115, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " aid = " + aid + " p2 = " + p2);
            try {
                radioProxy.iccOpenLogicalChannel(rr.mSerial, convertNullToEmptyString(aid), p2);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0044 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0044, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0045, code:
            handleRadioProxyExceptionForRR(r2, "iccCloseLogicalChannel", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void iccCloseLogicalChannel(int channel, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(116, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " channel = " + channel);
            try {
                radioProxy.iccCloseLogicalChannel(rr.mSerial, channel);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:8:0x00a2 A:{Splitter: B:6:0x009c, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:8:0x00a2, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:9:0x00a3, code:
            handleRadioProxyExceptionForRR(r3, "iccTransmitApduLogicalChannel", r0);
     */
    /* JADX WARNING: Missing block: B:12:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void iccTransmitApduLogicalChannel(int channel, int cla, int instruction, int p1, int p2, int p3, String data, Message result) {
        if (channel <= 0) {
            throw new RuntimeException("Invalid channel in iccTransmitApduLogicalChannel: " + channel);
        }
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(117, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " channel = " + channel + " cla = " + cla + " instruction = " + instruction + " p1 = " + p1 + " p2 = " + " p3 = " + p3 + " data = " + data);
            try {
                radioProxy.iccTransmitApduLogicalChannel(rr.mSerial, createSimApdu(channel, cla, instruction, p1, p2, p3, data));
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0044 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0044, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0045, code:
            handleRadioProxyExceptionForRR(r2, "nvReadItem", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void nvReadItem(int itemID, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(118, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " itemId = " + itemID);
            try {
                radioProxy.nvReadItem(rr.mSerial, itemID);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x005c A:{Splitter: B:3:0x0056, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x005c, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x005d, code:
            handleRadioProxyExceptionForRR(r3, "nvWriteItem", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void nvWriteItem(int itemId, String itemValue, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(119, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " itemId = " + itemId + " itemValue = " + itemValue);
            NvWriteItem item = new NvWriteItem();
            item.itemId = itemId;
            item.value = convertNullToEmptyString(itemValue);
            try {
                radioProxy.nvWriteItem(rr.mSerial, item);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:8:0x005d A:{Splitter: B:6:0x0057, ExcHandler: android.os.RemoteException (r1_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:8:0x005d, code:
            r1 = move-exception;
     */
    /* JADX WARNING: Missing block: B:9:0x005e, code:
            handleRadioProxyExceptionForRR(r4, "nvWriteCdmaPrl", r1);
     */
    /* JADX WARNING: Missing block: B:12:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void nvWriteCdmaPrl(byte[] preferredRoamingList, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(120, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " PreferredRoamingList = 0x" + IccUtils.bytesToHexString(preferredRoamingList));
            ArrayList<Byte> arrList = new ArrayList();
            for (byte valueOf : preferredRoamingList) {
                arrList.add(Byte.valueOf(valueOf));
            }
            try {
                radioProxy.nvWriteCdmaPrl(rr.mSerial, arrList);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0048 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0048, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0049, code:
            handleRadioProxyExceptionForRR(r2, "nvResetConfig", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void nvResetConfig(int resetType, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(121, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " resetType = " + resetType);
            try {
                radioProxy.nvResetConfig(rr.mSerial, convertToHalResetNvType(resetType));
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0072 A:{Splitter: B:3:0x006c, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0072, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0073, code:
            handleRadioProxyExceptionForRR(r3, "setUiccSubscription", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setUiccSubscription(int slotId, int appIndex, int subId, int subStatus, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(122, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " slot = " + slotId + " appIndex = " + appIndex + " subId = " + subId + " subStatus = " + subStatus);
            SelectUiccSub info = new SelectUiccSub();
            info.slot = slotId;
            info.appIndex = appIndex;
            info.subType = subId;
            info.actStatus = subStatus;
            try {
                radioProxy.setUiccSubscription(rr.mSerial, info);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0044 A:{Splitter: B:3:0x003e, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0044, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0045, code:
            handleRadioProxyExceptionForRR(r2, "setDataAllowed", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setDataAllowed(boolean allowed, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(123, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " allowed = " + allowed);
            try {
                radioProxy.setDataAllowed(rr.mSerial, allowed);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "getHardwareConfig", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getHardwareConfig(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(124, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getHardwareConfig(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0041 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0041, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0042, code:
            handleRadioProxyExceptionForRR(r2, "requestIccSimAuthentication", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void requestIccSimAuthentication(int authContext, String data, String aid, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(125, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.requestIccSimAuthentication(rr.mSerial, authContext, convertNullToEmptyString(data), convertNullToEmptyString(aid));
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:10:0x0065 A:{Splitter: B:8:0x005f, ExcHandler: android.os.RemoteException (r2_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:10:0x0065, code:
            r2 = move-exception;
     */
    /* JADX WARNING: Missing block: B:11:0x0066, code:
            handleRadioProxyExceptionForRR(r5, "setDataProfile", r2);
     */
    /* JADX WARNING: Missing block: B:15:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setDataProfile(DataProfile[] dps, boolean isRoaming, Message result) {
        int i = 0;
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(128, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " with data profiles : ");
            for (DataProfile profile : dps) {
                riljLog(profile.toString());
            }
            ArrayList<DataProfileInfo> dpis = new ArrayList();
            int length = dps.length;
            while (i < length) {
                dpis.add(convertToHalDataProfile(dps[i]));
                i++;
            }
            try {
                radioProxy.setDataProfile(rr.mSerial, dpis, isRoaming);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "requestShutdown", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void requestShutdown(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(129, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.requestShutdown(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "getRadioCapability", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getRadioCapability(Message response) {
        IRadio radioProxy = getRadioProxy(response);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(130, response, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getRadioCapability(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    public void setRadioCapability(RadioCapability rc, Message response) {
        IRadio radioProxy = getRadioProxy(response);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(131, response, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " RadioCapability = " + rc.toString());
            RadioCapability halRc = new RadioCapability();
            halRc.session = rc.getSession();
            halRc.phase = rc.getPhase();
            halRc.raf = rc.getRadioAccessFamily();
            halRc.logicalModemUuid = convertNullToEmptyString(rc.getLogicalModemUuid());
            halRc.status = rc.getStatus();
            try {
                radioProxy.setRadioCapability(rr.mSerial, halRc);
            } catch (Exception e) {
                handleRadioProxyExceptionForRR(rr, "setRadioCapability", e);
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x004f A:{Splitter: B:3:0x0049, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x004f, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0050, code:
            handleRadioProxyExceptionForRR(r2, "startLceService", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void startLceService(int reportIntervalMs, boolean pullMode, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(132, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " reportIntervalMs = " + reportIntervalMs + " pullMode = " + pullMode);
            try {
                radioProxy.startLceService(rr.mSerial, reportIntervalMs, pullMode);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "stopLceService", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void stopLceService(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(133, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.stopLceService(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "pullLceData", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void pullLceData(Message response) {
        IRadio radioProxy = getRadioProxy(response);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(134, response, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.pullLceData(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x004e A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x004e, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x004f, code:
            handleRadioProxyExceptionForRR(r3, "getModemActivityInfo", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getModemActivityInfo(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(135, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getModemActivityInfo(rr.mSerial);
                Message msg = this.mRilHandler.obtainMessage(5);
                msg.obj = null;
                msg.arg1 = rr.mSerial;
                this.mRilHandler.sendMessageDelayed(msg, 2000);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:28:0x0101 A:{Splitter: B:26:0x00fb, ExcHandler: android.os.RemoteException (r6_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:28:0x0101, code:
            r6 = move-exception;
     */
    /* JADX WARNING: Missing block: B:29:0x0102, code:
            handleRadioProxyExceptionForRR(r12, "setAllowedCarriers", r6);
     */
    /* JADX WARNING: Missing block: B:38:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setAllowedCarriers(List<CarrierIdentifier> carriers, Message result) {
        Preconditions.checkNotNull(carriers, "Allowed carriers list cannot be null.");
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            boolean allAllowed;
            RILRequest rr = obtainRequest(136, result, this.mRILDefaultWorkSource);
            String logStr = "";
            for (int i = 0; i < carriers.size(); i++) {
                logStr = logStr + carriers.get(i) + " ";
            }
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " carriers = " + logStr);
            if (carriers.size() == 0) {
                allAllowed = true;
            } else {
                allAllowed = false;
            }
            CarrierRestrictions carrierList = new CarrierRestrictions();
            for (CarrierIdentifier ci : carriers) {
                Carrier c = new Carrier();
                c.mcc = convertNullToEmptyString(ci.getMcc());
                c.mnc = convertNullToEmptyString(ci.getMnc());
                int matchType = 0;
                String matchData = null;
                if (!TextUtils.isEmpty(ci.getSpn())) {
                    matchType = 1;
                    matchData = ci.getSpn();
                } else if (!TextUtils.isEmpty(ci.getImsi())) {
                    matchType = 2;
                    matchData = ci.getImsi();
                } else if (!TextUtils.isEmpty(ci.getGid1())) {
                    matchType = 3;
                    matchData = ci.getGid1();
                } else if (!TextUtils.isEmpty(ci.getGid2())) {
                    matchType = 4;
                    matchData = ci.getGid2();
                }
                c.matchType = matchType;
                c.matchData = convertNullToEmptyString(matchData);
                carrierList.allowedCarriers.add(c);
            }
            try {
                radioProxy.setAllowedCarriers(rr.mSerial, allAllowed, carrierList);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "getAllowedCarriers", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void getAllowedCarriers(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(137, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.getAllowedCarriers(rr.mSerial);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x004f A:{Splitter: B:3:0x0049, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x004f, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x0050, code:
            handleRadioProxyExceptionForRR(r2, "sendDeviceState", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void sendDeviceState(int stateType, boolean state, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(138, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + stateType + ":" + state);
            try {
                radioProxy.sendDeviceState(rr.mSerial, stateType, state);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:13:0x0070 A:{Splitter: B:8:0x0057, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:13:0x0070, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:14:0x0071, code:
            handleRadioProxyExceptionForRR(r2, "setIndicationFilter", r0);
     */
    /* JADX WARNING: Missing block: B:17:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setUnsolResponseFilter(int filter, Message result) {
        AsyncResult signalResult;
        if ((filter & 1) == 0) {
            signalResult = new AsyncResult(null, Integer.valueOf(0), null);
            if (this.mSignalPollRegistrant != null) {
                this.mSignalPollRegistrant.notifyRegistrant(signalResult);
            }
        } else {
            signalResult = new AsyncResult(null, Integer.valueOf(1), null);
            if (this.mSignalPollRegistrant != null) {
                this.mSignalPollRegistrant.notifyRegistrant(signalResult);
            }
        }
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(139, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + filter);
            try {
                radioProxy.setIndicationFilter(rr.mSerial, filter);
            } catch (Exception e) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:10:0x005d A:{Splitter: B:7:0x004a, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Removed duplicated region for block: B:16:0x0072 A:{Splitter: B:14:0x006c, ExcHandler: android.os.RemoteException (r0_1 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:16:0x0072, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:17:0x0073, code:
            handleRadioProxyExceptionForRR(r3, "setSimCardPower", r0);
     */
    /* JADX WARNING: Missing block: B:22:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setSimCardPower(int state, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(140, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + state);
            android.hardware.radio.V1_1.IRadio radioProxy11 = android.hardware.radio.V1_1.IRadio.castFrom(radioProxy);
            if (radioProxy11 == null) {
                switch (state) {
                    case 0:
                        radioProxy.setSimCardPower(rr.mSerial, false);
                        return;
                    case 1:
                        radioProxy.setSimCardPower(rr.mSerial, true);
                        return;
                    default:
                        if (result != null) {
                            try {
                                AsyncResult.forMessage(result, null, CommandException.fromRilErrno(6));
                                result.sendToTarget();
                                return;
                            } catch (Exception e) {
                            }
                        } else {
                            return;
                        }
                }
                handleRadioProxyExceptionForRR(rr, "setSimCardPower", e);
                return;
            }
            try {
                radioProxy11.setSimCardPower_1_1(rr.mSerial, state);
            } catch (Exception e2) {
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:15:0x0096 A:{Splitter: B:7:0x004e, ExcHandler: android.os.RemoteException (r1_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:15:0x0096, code:
            r1 = move-exception;
     */
    /* JADX WARNING: Missing block: B:16:0x0097, code:
            handleRadioProxyExceptionForRR(r5, "setCarrierInfoForImsiEncryption", r1);
     */
    /* JADX WARNING: Missing block: B:21:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setCarrierInfoForImsiEncryption(ImsiEncryptionInfo imsiEncryptionInfo, Message result) {
        Preconditions.checkNotNull(imsiEncryptionInfo, "ImsiEncryptionInfo cannot be null.");
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            android.hardware.radio.V1_1.IRadio radioProxy11 = android.hardware.radio.V1_1.IRadio.castFrom(radioProxy);
            if (radioProxy11 != null) {
                RILRequest rr = obtainRequest(141, result, this.mRILDefaultWorkSource);
                riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
                try {
                    android.hardware.radio.V1_1.ImsiEncryptionInfo halImsiInfo = new android.hardware.radio.V1_1.ImsiEncryptionInfo();
                    halImsiInfo.mnc = imsiEncryptionInfo.getMnc();
                    halImsiInfo.mcc = imsiEncryptionInfo.getMcc();
                    halImsiInfo.keyIdentifier = imsiEncryptionInfo.getKeyIdentifier();
                    if (imsiEncryptionInfo.getExpirationTime() != null) {
                        halImsiInfo.expirationTime = imsiEncryptionInfo.getExpirationTime().getTime();
                    }
                    for (byte b : imsiEncryptionInfo.getPublicKey().getEncoded()) {
                        halImsiInfo.carrierKey.add(new Byte(b));
                    }
                    radioProxy11.setCarrierInfoForImsiEncryption(rr.mSerial, halImsiInfo);
                } catch (Exception e) {
                }
            } else if (result != null) {
                AsyncResult.forMessage(result, null, CommandException.fromRilErrno(6));
                result.sendToTarget();
            }
        }
    }

    public void getAtr(Message response) {
    }

    public void getIMEI(Message result) {
    }

    public void getIMEISV(Message result) {
    }

    @Deprecated
    public void getLastPdpFailCause(Message result) {
        throw new RuntimeException("getLastPdpFailCause not expected to be called");
    }

    public void getLastDataCallFailCause(Message result) {
        throw new RuntimeException("getLastDataCallFailCause not expected to be called");
    }

    private int translateStatus(int status) {
        switch (status & 7) {
            case 1:
                return 1;
            case 3:
                return 0;
            case 5:
                return 3;
            case 7:
                return 2;
            default:
                return 1;
        }
    }

    public void resetRadio(Message result) {
        throw new RuntimeException("resetRadio not expected to be called");
    }

    /* JADX WARNING: Removed duplicated region for block: B:5:0x0039 A:{Splitter: B:3:0x0033, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:5:0x0039, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:6:0x003a, code:
            handleRadioProxyExceptionForRR(r2, "getAllowedCarriers", r0);
     */
    /* JADX WARNING: Missing block: B:9:?, code:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void handleCallSetupRequestFromSim(boolean accept, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(71, result, this.mRILDefaultWorkSource);
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            try {
                radioProxy.handleStkCallSetupRequestFromSim(rr.mSerial, accept);
            } catch (Exception e) {
            }
        }
    }

    void processIndication(int indicationType) {
        if (indicationType == 1) {
            sendAck();
            riljLog("Unsol response received; Sending ack to ril.cpp");
        }
    }

    void processRequestAck(int serial) {
        RILRequest rr;
        synchronized (this.mRequestList) {
            rr = (RILRequest) this.mRequestList.get(serial);
        }
        if (rr == null) {
            Rlog.w(RILJ_LOG_TAG, "processRequestAck: Unexpected solicited ack response! serial: " + serial);
            return;
        }
        decrementWakeLock(rr);
        riljLog(rr.serialString() + " Ack < " + requestToString(rr.mRequest));
    }

    protected RILRequest processResponse(RadioResponseInfo responseInfo) {
        int serial = responseInfo.serial;
        int error = responseInfo.error;
        int type = responseInfo.type;
        RILRequest rr;
        if (type == 1) {
            synchronized (this.mRequestList) {
                rr = (RILRequest) this.mRequestList.get(serial);
            }
            if (rr == null) {
                Rlog.w(RILJ_LOG_TAG, "Unexpected solicited ack response! sn: " + serial);
            } else {
                decrementWakeLock(rr);
                riljLog(rr.serialString() + " Ack < " + requestToString(rr.mRequest));
            }
            return rr;
        }
        rr = findAndRemoveRequestFromList(serial);
        if (rr == null) {
            Rlog.e(RILJ_LOG_TAG, "processResponse: Unexpected response! serial: " + serial + " error: " + error);
            return null;
        }
        addToRilHistogram(rr);
        if (type == 2) {
            sendAck();
            riljLog("Response received for " + rr.serialString() + " " + requestToString(rr.mRequest) + " Sending ack to ril.cpp");
        }
        switch (rr.mRequest) {
            case 3:
            case 5:
                if (this.mIccStatusChangedRegistrants != null) {
                    riljLog("ON enter sim puk fakeSimStatusChanged: reg count=" + this.mIccStatusChangedRegistrants.size());
                    this.mIccStatusChangedRegistrants.notifyRegistrants();
                    break;
                }
                break;
            case 129:
                setRadioState(RadioState.RADIO_UNAVAILABLE);
                break;
        }
        if (error == 0) {
            switch (rr.mRequest) {
                case 14:
                    if (this.mTestingEmergencyCall.getAndSet(false) && this.mEmergencyCallbackModeRegistrant != null) {
                        riljLog("testing emergency call, notify ECM Registrants");
                        this.mEmergencyCallbackModeRegistrant.notifyRegistrant();
                        break;
                    }
            }
        }
        switch (rr.mRequest) {
            case 2:
            case 4:
            case 6:
            case 7:
            case 43:
                if (this.mIccStatusChangedRegistrants != null) {
                    riljLog("ON some errors fakeSimStatusChanged: reg count=" + this.mIccStatusChangedRegistrants.size());
                    this.mIccStatusChangedRegistrants.notifyRegistrants();
                    break;
                }
                break;
        }
        return rr;
    }

    protected Message getMessageFromRequest(Object request) {
        RILRequest rr = (RILRequest) request;
        if (rr != null) {
            return rr.mResult;
        }
        return null;
    }

    void processResponseDone(RILRequest rr, RadioResponseInfo responseInfo, Object ret) {
        if (responseInfo.error == 0) {
            riljLog(rr.serialString() + "< " + requestToString(rr.mRequest) + " " + retToString(rr.mRequest, ret));
        } else {
            riljLog(rr.serialString() + "< " + requestToString(rr.mRequest) + " error " + responseInfo.error);
            rr.onError(responseInfo.error, ret);
        }
        switch (rr.mRequest) {
            case 12:
                this.mHangupInfoCollectHelper.hangupReceive();
                break;
        }
        if (responseInfo.error != 0) {
            switch (rr.mRequest) {
                case 10:
                    this.mHangupInfoCollectHelper.addDialError();
                    break;
                case 25:
                    if (((SmsResponse) ret).mErrorCode != 0) {
                        RoamingStatusCollectHelper.getInstance().setSmsErrorCode(this.mPhoneId.intValue(), ((SmsResponse) ret).mErrorCode);
                        break;
                    }
                    break;
                case 87:
                    if (((SmsResponse) ret).mErrorCode != 0) {
                        RoamingStatusCollectHelper.getInstance().setSmsErrorCode(this.mPhoneId.intValue(), ((SmsResponse) ret).mErrorCode);
                        break;
                    }
                    break;
            }
        }
        this.mMetrics.writeOnRilSolicitedResponse(this.mPhoneId.intValue(), rr.mSerial, responseInfo.error, rr.mRequest, ret);
        if (rr != null) {
            if (responseInfo.type == 0) {
                decrementWakeLock(rr);
            }
            rr.release();
        }
    }

    protected void processResponseDone(Object request, RadioResponseInfo responseInfo, Object ret) {
        processResponseDone((RILRequest) request, responseInfo, ret);
    }

    /* JADX WARNING: Removed duplicated region for block: B:6:0x001a A:{Splitter: B:2:0x0013, ExcHandler: android.os.RemoteException (r0_0 'e' java.lang.Exception)} */
    /* JADX WARNING: Missing block: B:6:0x001a, code:
            r0 = move-exception;
     */
    /* JADX WARNING: Missing block: B:7:0x001b, code:
            handleRadioProxyExceptionForRR(r2, "sendAck", r0);
            riljLoge("sendAck: " + r0);
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void sendAck() {
        RILRequest rr = RILRequest.obtain(800, null, this.mRILDefaultWorkSource);
        acquireWakeLock(rr, 1);
        IRadio radioProxy = getRadioProxy(null);
        if (radioProxy != null) {
            try {
                radioProxy.responseAcknowledgement();
            } catch (Exception e) {
            }
        } else {
            Rlog.e(RILJ_LOG_TAG, "Error trying to send ack, radioProxy = null");
        }
        rr.release();
    }

    private WorkSource getDeafultWorkSourceIfInvalid(WorkSource workSource) {
        if (workSource == null) {
            return this.mRILDefaultWorkSource;
        }
        return workSource;
    }

    private String getWorkSourceClientId(WorkSource workSource) {
        if (workSource != null) {
            return String.valueOf(workSource.get(0)) + ":" + workSource.getName(0);
        }
        return null;
    }

    private void acquireWakeLock(RILRequest rr, int wakeLockType) {
        synchronized (rr) {
            if (rr.mWakeLockType != -1) {
                Rlog.d(RILJ_LOG_TAG, "Failed to aquire wakelock for " + rr.serialString());
                return;
            }
            Message msg;
            switch (wakeLockType) {
                case 0:
                    synchronized (this.mWakeLock) {
                        this.mWakeLock.acquire();
                        this.mWakeLockCount++;
                        this.mWlSequenceNum++;
                        if (!this.mClientWakelockTracker.isClientActive(getWorkSourceClientId(rr.mWorkSource))) {
                            if (this.mActiveWakelockWorkSource != null) {
                                this.mActiveWakelockWorkSource.add(rr.mWorkSource);
                            } else {
                                this.mActiveWakelockWorkSource = rr.mWorkSource;
                            }
                            this.mWakeLock.setWorkSource(this.mActiveWakelockWorkSource);
                        }
                        this.mClientWakelockTracker.startTracking(rr.mClientId, rr.mRequest, rr.mSerial, this.mWakeLockCount);
                        msg = this.mRilHandler.obtainMessage(2);
                        msg.arg1 = this.mWlSequenceNum;
                        this.mRilHandler.sendMessageDelayed(msg, (long) this.mWakeLockTimeout);
                    }
                case 1:
                    synchronized (this.mAckWakeLock) {
                        this.mAckWakeLock.acquire();
                        this.mAckWlSequenceNum++;
                        msg = this.mRilHandler.obtainMessage(4);
                        msg.arg1 = this.mAckWlSequenceNum;
                        this.mRilHandler.sendMessageDelayed(msg, (long) this.mAckWakeLockTimeout);
                    }
                default:
                    Rlog.w(RILJ_LOG_TAG, "Acquiring Invalid Wakelock type " + wakeLockType);
                    return;
            }
            rr.mWakeLockType = wakeLockType;
        }
    }

    private void decrementWakeLock(RILRequest rr) {
        int i = 0;
        synchronized (rr) {
            switch (rr.mWakeLockType) {
                case -1:
                case 1:
                    break;
                case 0:
                    synchronized (this.mWakeLock) {
                        ClientWakelockTracker clientWakelockTracker = this.mClientWakelockTracker;
                        String str = rr.mClientId;
                        int i2 = rr.mRequest;
                        int i3 = rr.mSerial;
                        if (this.mWakeLockCount > 1) {
                            i = this.mWakeLockCount - 1;
                        }
                        clientWakelockTracker.stopTracking(str, i2, i3, i);
                        if (!(this.mClientWakelockTracker.isClientActive(getWorkSourceClientId(rr.mWorkSource)) || this.mActiveWakelockWorkSource == null)) {
                            this.mActiveWakelockWorkSource.remove(rr.mWorkSource);
                            if (this.mActiveWakelockWorkSource.size() == 0) {
                                this.mActiveWakelockWorkSource = null;
                            }
                            this.mWakeLock.setWorkSource(this.mActiveWakelockWorkSource);
                        }
                        if (this.mWakeLockCount > 1) {
                            this.mWakeLockCount--;
                        } else {
                            this.mWakeLockCount = 0;
                            this.mWakeLock.release();
                        }
                    }
                default:
                    Rlog.w(RILJ_LOG_TAG, "Decrementing Invalid Wakelock type " + rr.mWakeLockType);
                    break;
            }
            rr.mWakeLockType = -1;
        }
    }

    private boolean clearWakeLock(int wakeLockType) {
        if (wakeLockType == 0) {
            synchronized (this.mWakeLock) {
                if (this.mWakeLockCount != 0 || (this.mWakeLock.isHeld() ^ 1) == 0) {
                    Rlog.d(RILJ_LOG_TAG, "NOTE: mWakeLockCount is " + this.mWakeLockCount + "at time of clearing");
                    this.mWakeLockCount = 0;
                    this.mWakeLock.release();
                    this.mClientWakelockTracker.stopTrackingAll();
                    this.mActiveWakelockWorkSource = null;
                    return true;
                }
                return false;
            }
        }
        synchronized (this.mAckWakeLock) {
            if (this.mAckWakeLock.isHeld()) {
                this.mAckWakeLock.release();
                return true;
            }
            return false;
        }
    }

    private void clearRequestList(int error, boolean loggable) {
        synchronized (this.mRequestList) {
            int count = this.mRequestList.size();
            if (loggable) {
                Rlog.d(RILJ_LOG_TAG, "clearRequestList  mWakeLockCount=" + this.mWakeLockCount + " mRequestList=" + count);
            }
            for (int i = 0; i < count; i++) {
                RILRequest rr = (RILRequest) this.mRequestList.valueAt(i);
                if (loggable) {
                    Rlog.d(RILJ_LOG_TAG, i + ": [" + rr.mSerial + "] " + requestToString(rr.mRequest));
                }
                rr.onError(error, null);
                decrementWakeLock(rr);
                rr.release();
            }
            this.mRequestList.clear();
        }
    }

    private RILRequest findAndRemoveRequestFromList(int serial) {
        RILRequest rr;
        synchronized (this.mRequestList) {
            rr = (RILRequest) this.mRequestList.get(serial);
            if (rr != null) {
                this.mRequestList.remove(serial);
            }
        }
        return rr;
    }

    private void addToRilHistogram(RILRequest rr) {
        int totalTime = (int) (SystemClock.elapsedRealtime() - rr.mStartTimeMs);
        synchronized (mRilTimeHistograms) {
            TelephonyHistogram entry = (TelephonyHistogram) mRilTimeHistograms.get(rr.mRequest);
            if (entry == null) {
                entry = new TelephonyHistogram(1, rr.mRequest, 5);
                mRilTimeHistograms.put(rr.mRequest, entry);
            }
            entry.addTimeTaken(totalTime);
        }
    }

    RadioCapability makeStaticRadioCapability() {
        int raf = 1;
        String rafString = this.mContext.getResources().getString(17039709);
        if (!TextUtils.isEmpty(rafString)) {
            raf = RadioAccessFamily.rafTypeFromString(rafString);
        }
        RadioCapability rc = new RadioCapability(this.mPhoneId.intValue(), 0, 0, raf, "", 1);
        riljLog("Faking RIL_REQUEST_GET_RADIO_CAPABILITY response using " + raf);
        return rc;
    }

    static String retToString(int req, Object ret) {
        if (ret == null) {
            return "";
        }
        switch (req) {
            case 11:
            case 38:
            case 39:
            case 115:
            case 117:
                return "";
            default:
                String s;
                int length;
                StringBuilder stringBuilder;
                int i;
                int i2;
                if (ret instanceof int[]) {
                    int[] intArray = (int[]) ret;
                    length = intArray.length;
                    stringBuilder = new StringBuilder("{");
                    if (length > 0) {
                        stringBuilder.append(intArray[0]);
                        i = 1;
                        while (i < length) {
                            i2 = i + 1;
                            stringBuilder.append(", ").append(intArray[i]);
                            i = i2;
                        }
                    }
                    stringBuilder.append("}");
                    s = stringBuilder.toString();
                } else if (ret instanceof String[]) {
                    String[] strings = (String[]) ret;
                    length = strings.length;
                    stringBuilder = new StringBuilder("{");
                    if (length > 0) {
                        stringBuilder.append(strings[0]);
                        i = 1;
                        while (i < length) {
                            i2 = i + 1;
                            stringBuilder.append(", ").append(strings[i]);
                            i = i2;
                        }
                    }
                    stringBuilder.append("}");
                    s = stringBuilder.toString();
                } else if (req == 9) {
                    ArrayList<DriverCall> calls = (ArrayList) ret;
                    stringBuilder = new StringBuilder("{");
                    for (DriverCall dc : calls) {
                        stringBuilder.append("[").append(dc).append("] ");
                    }
                    stringBuilder.append("}");
                    s = stringBuilder.toString();
                } else if (req == 75) {
                    ArrayList<NeighboringCellInfo> cells = (ArrayList) ret;
                    stringBuilder = new StringBuilder("{");
                    for (NeighboringCellInfo cell : cells) {
                        stringBuilder.append("[").append(cell).append("] ");
                    }
                    stringBuilder.append("}");
                    s = stringBuilder.toString();
                } else if (req == 33) {
                    stringBuilder = new StringBuilder("{");
                    for (Object append : (CallForwardInfo[]) ret) {
                        stringBuilder.append("[").append(append).append("] ");
                    }
                    stringBuilder.append("}");
                    s = stringBuilder.toString();
                } else if (req == 124) {
                    ArrayList<HardwareConfig> hwcfgs = (ArrayList) ret;
                    stringBuilder = new StringBuilder(" ");
                    for (HardwareConfig hwcfg : hwcfgs) {
                        stringBuilder.append("[").append(hwcfg).append("] ");
                    }
                    s = stringBuilder.toString();
                } else {
                    s = ret.toString();
                }
                return s;
        }
    }

    void writeMetricsNewSms(int tech, int format) {
        this.mMetrics.writeRilNewSms(this.mPhoneId.intValue(), tech, format);
    }

    void writeMetricsCallRing(char[] response) {
        this.mMetrics.writeRilCallRing(this.mPhoneId.intValue(), response);
    }

    void writeMetricsSrvcc(int state) {
        this.mMetrics.writeRilSrvcc(this.mPhoneId.intValue(), state);
    }

    void writeMetricsModemRestartEvent(String reason) {
        this.mMetrics.writeModemRestartEvent(this.mPhoneId.intValue(), reason);
    }

    @VivoHook(hookType = VivoHookType.NEW_METHOD)
    private void broadcastHadoopInvalidSim(String str) {
        String INVALID_SIM_PLMN = "invalid sim:plmn =";
        String strInvalidSimPlmn = "0";
        String strInvalidSimLai = "";
        String strInvalidSimRat = "";
        String strInvalidSimCause = "";
        try {
            int strIndex = str.indexOf("invalid sim:plmn =");
            if (strIndex > -1) {
                int commaIndex = str.indexOf(",", strIndex);
                if (commaIndex > -1) {
                    strInvalidSimPlmn = str.substring("invalid sim:plmn =".length() + strIndex, commaIndex);
                }
                strIndex = str.indexOf("=", commaIndex) + 1;
                commaIndex = str.indexOf(",", strIndex);
                if (strIndex > -1 && commaIndex > -1) {
                    strInvalidSimRat = str.substring(strIndex, commaIndex);
                }
                strIndex = str.indexOf("=", commaIndex) + 1;
                commaIndex = str.indexOf(",", strIndex);
                if (strIndex > -1 && commaIndex > -1) {
                    strInvalidSimLai = str.substring(strIndex, commaIndex);
                }
                strIndex = str.indexOf("=", commaIndex) + 1;
                if (strIndex > -1 && commaIndex > -1) {
                    strInvalidSimCause = str.substring(strIndex);
                }
                riljLog("vivo hadoop broadcast InvalidSim strInvalidSimPlmn=" + strInvalidSimPlmn + ",,strInvalidSimRat=" + strInvalidSimRat + ",strInvalidSimLai=" + strInvalidSimLai + ",,strInvalidSimCause=" + strInvalidSimCause);
                if (!strInvalidSimPlmn.equals("0")) {
                    Intent intent2;
                    if (last_strInvalidSimTacLac == null || last_strInvalidSimTacLac.equals("")) {
                        intent2 = new Intent("vivo.intent.action.invalidsim");
                        intent2.setPackage("com.vivo.networkimprove");
                        intent2.putExtra("PLMN", strInvalidSimPlmn);
                        intent2.putExtra("TACLAC", strInvalidSimLai);
                        intent2.putExtra("CAUSE", strInvalidSimRat + ",, " + strInvalidSimCause);
                        this.mContext.sendBroadcast(intent2);
                        last_strInvalidSimTacLac = strInvalidSimLai;
                    } else if (!last_strInvalidSimTacLac.equals(strInvalidSimLai)) {
                        intent2 = new Intent("vivo.intent.action.invalidsim");
                        intent2.setPackage("com.vivo.networkimprove");
                        intent2.putExtra("PLMN", strInvalidSimPlmn);
                        intent2.putExtra("TACLAC", strInvalidSimLai);
                        intent2.putExtra("CAUSE", strInvalidSimRat + ",, " + strInvalidSimCause);
                        this.mContext.sendBroadcast(intent2);
                        last_strInvalidSimTacLac = strInvalidSimLai;
                    }
                }
            }
        } catch (Throwable tr) {
            riljLog("vivo broadcastHadoopInvalidSim uncaught exception" + tr.toString());
        }
    }

    @VivoHook(hookType = VivoHookType.NEW_METHOD)
    private void broadcastHadoopInfo(String str) {
        riljLog("vivo hadoop ready to broadcast buffer " + str);
        String VOPS_PLMN = "vops_plmn:";
        String VOPS_TAC = "vops_tac:";
        String INVALID_SIM_PLMN = "invalid_sim_plmn:";
        String INVALID_SIM_TAC_LAC = "invalid_sim_tac_lac:";
        String strVopsPlmn = "";
        String strVopsTac = "";
        String strInvalidSimPlmn = "";
        String strInvalidSimTacLac = "";
        try {
            int commaIndex;
            int strIndex = str.indexOf("vops_plmn:");
            if (strIndex > -1) {
                commaIndex = str.indexOf(",", strIndex);
                if (commaIndex > -1) {
                    strVopsPlmn = str.substring("vops_plmn:".length() + strIndex, commaIndex);
                }
            }
            strIndex = str.indexOf("vops_tac:");
            if (strIndex > -1) {
                commaIndex = str.indexOf(",", strIndex);
                if (commaIndex > -1) {
                    strVopsTac = str.substring("vops_tac:".length() + strIndex, commaIndex);
                }
            }
            strIndex = str.indexOf("invalid_sim_plmn:");
            if (strIndex > -1) {
                commaIndex = str.indexOf(",", strIndex);
                if (commaIndex > -1) {
                    strInvalidSimPlmn = str.substring("invalid_sim_plmn:".length() + strIndex, commaIndex);
                }
            }
            strIndex = str.indexOf("invalid_sim_tac_lac:");
            if (strIndex > -1) {
                commaIndex = str.indexOf(",", strIndex);
                if (commaIndex > -1) {
                    strInvalidSimTacLac = str.substring("invalid_sim_tac_lac:".length() + strIndex, commaIndex);
                }
            }
            riljLog("vivo hadoop broadcast msg: vops_plmn:" + strVopsPlmn + " " + "vops_tac:" + strVopsTac + " " + "invalid_sim_plmn:" + strInvalidSimPlmn + " " + "invalid_sim_tac_lac:" + strInvalidSimTacLac);
            if (!strInvalidSimPlmn.equals("0")) {
                Intent intent2;
                if (last_strInvalidSimTacLac == null || last_strInvalidSimTacLac.equals("")) {
                    intent2 = new Intent("vivo.intent.action.invalidsim");
                    intent2.setPackage("com.vivo.networkimprove");
                    intent2.putExtra("PLMN", strInvalidSimPlmn);
                    intent2.putExtra("TACLAC", strInvalidSimTacLac);
                    intent2.putExtra("CAUSE", "NULL");
                    this.mContext.sendBroadcast(intent2);
                    last_strInvalidSimTacLac = strInvalidSimTacLac;
                } else if (!last_strInvalidSimTacLac.equals(strInvalidSimTacLac)) {
                    intent2 = new Intent("vivo.intent.action.invalidsim");
                    intent2.setPackage("com.vivo.networkimprove");
                    intent2.putExtra("PLMN", strInvalidSimPlmn);
                    intent2.putExtra("TACLAC", strInvalidSimTacLac);
                    intent2.putExtra("CAUSE", "NULL");
                    this.mContext.sendBroadcast(intent2);
                    last_strInvalidSimTacLac = strInvalidSimTacLac;
                }
            }
        } catch (Throwable tr) {
            riljLog("vivo hadoop uncaught exception" + tr.toString());
        }
    }

    @VivoHook(hookType = VivoHookType.NEW_METHOD)
    private void broadcastBandSignal(String str, int slot) {
        if (str != null && (str.equals("") ^ 1) != 0) {
            riljLog("responseMisc: broadcastBandSignal  slot=" + slot + ",str=" + str + ",str.length=" + str.length());
            if (slot == 1) {
                Intent intent = new Intent("vivo.intent.action.bandsignalsone");
                intent.setPackage("com.vivo.networkimprove");
                intent.putExtra("DATA", str);
                this.mContext.sendBroadcast(intent);
                return;
            }
            Intent intent1 = new Intent("vivo.intent.action.bandsignalstwo");
            intent1.setPackage("com.vivo.networkimprove");
            intent1.putExtra("DATA", str);
            this.mContext.sendBroadcast(intent1);
        }
    }

    void notifyRegistrantsRilConnectionChanged(int rilVer) {
        this.mRilVersion = rilVer;
        if (this.mRilConnectedRegistrants != null) {
            this.mRilConnectedRegistrants.notifyRegistrants(new AsyncResult(null, new Integer(rilVer), null));
        }
    }

    void notifyRegistrantsCdmaInfoRec(CdmaInformationRecords infoRec) {
        if (infoRec.record instanceof CdmaDisplayInfoRec) {
            if (this.mDisplayInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mDisplayInfoRegistrants.notifyRegistrants(new AsyncResult(null, infoRec.record, null));
            }
        } else if (infoRec.record instanceof CdmaSignalInfoRec) {
            if (this.mSignalInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mSignalInfoRegistrants.notifyRegistrants(new AsyncResult(null, infoRec.record, null));
            }
        } else if (infoRec.record instanceof CdmaNumberInfoRec) {
            if (this.mNumberInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mNumberInfoRegistrants.notifyRegistrants(new AsyncResult(null, infoRec.record, null));
            }
        } else if (infoRec.record instanceof CdmaRedirectingNumberInfoRec) {
            if (this.mRedirNumInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mRedirNumInfoRegistrants.notifyRegistrants(new AsyncResult(null, infoRec.record, null));
            }
        } else if (infoRec.record instanceof CdmaLineControlInfoRec) {
            if (this.mLineControlInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mLineControlInfoRegistrants.notifyRegistrants(new AsyncResult(null, infoRec.record, null));
            }
        } else if (infoRec.record instanceof CdmaT53ClirInfoRec) {
            if (this.mT53ClirInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mT53ClirInfoRegistrants.notifyRegistrants(new AsyncResult(null, infoRec.record, null));
            }
        } else if ((infoRec.record instanceof CdmaT53AudioControlInfoRec) && this.mT53AudCntrlInfoRegistrants != null) {
            unsljLogRet(1027, infoRec.record);
            this.mT53AudCntrlInfoRegistrants.notifyRegistrants(new AsyncResult(null, infoRec.record, null));
        }
    }

    static String requestToString(int request) {
        switch (request) {
            case 1:
                return "GET_SIM_STATUS";
            case 2:
                return "ENTER_SIM_PIN";
            case 3:
                return "ENTER_SIM_PUK";
            case 4:
                return "ENTER_SIM_PIN2";
            case 5:
                return "ENTER_SIM_PUK2";
            case 6:
                return "CHANGE_SIM_PIN";
            case 7:
                return "CHANGE_SIM_PIN2";
            case 8:
                return "ENTER_NETWORK_DEPERSONALIZATION";
            case 9:
                return "GET_CURRENT_CALLS";
            case 10:
                return "DIAL";
            case 11:
                return "GET_IMSI";
            case 12:
                return "HANGUP";
            case 13:
                return "HANGUP_WAITING_OR_BACKGROUND";
            case 14:
                return "HANGUP_FOREGROUND_RESUME_BACKGROUND";
            case 15:
                return "REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE";
            case 16:
                return "CONFERENCE";
            case 17:
                return "UDUB";
            case 18:
                return "LAST_CALL_FAIL_CAUSE";
            case 19:
                return "SIGNAL_STRENGTH";
            case 20:
                return "VOICE_REGISTRATION_STATE";
            case 21:
                return "DATA_REGISTRATION_STATE";
            case 22:
                return "OPERATOR";
            case 23:
                return "RADIO_POWER";
            case 24:
                return "DTMF";
            case 25:
                return "SEND_SMS";
            case 26:
                return "SEND_SMS_EXPECT_MORE";
            case 27:
                return "SETUP_DATA_CALL";
            case 28:
                return "SIM_IO";
            case 29:
                return "SEND_USSD";
            case 30:
                return "CANCEL_USSD";
            case 31:
                return "GET_CLIR";
            case 32:
                return "SET_CLIR";
            case 33:
                return "QUERY_CALL_FORWARD_STATUS";
            case 34:
                return "SET_CALL_FORWARD";
            case 35:
                return "QUERY_CALL_WAITING";
            case 36:
                return "SET_CALL_WAITING";
            case 37:
                return "SMS_ACKNOWLEDGE";
            case 38:
                return "GET_IMEI";
            case 39:
                return "GET_IMEISV";
            case 40:
                return "ANSWER";
            case 41:
                return "DEACTIVATE_DATA_CALL";
            case 42:
                return "QUERY_FACILITY_LOCK";
            case 43:
                return "SET_FACILITY_LOCK";
            case 44:
                return "CHANGE_BARRING_PASSWORD";
            case 45:
                return "QUERY_NETWORK_SELECTION_MODE";
            case 46:
                return "SET_NETWORK_SELECTION_AUTOMATIC";
            case 47:
                return "SET_NETWORK_SELECTION_MANUAL";
            case 48:
                return "QUERY_AVAILABLE_NETWORKS ";
            case 49:
                return "DTMF_START";
            case 50:
                return "DTMF_STOP";
            case 51:
                return "BASEBAND_VERSION";
            case 52:
                return "SEPARATE_CONNECTION";
            case 53:
                return "SET_MUTE";
            case 54:
                return "GET_MUTE";
            case 55:
                return "QUERY_CLIP";
            case 56:
                return "LAST_DATA_CALL_FAIL_CAUSE";
            case 57:
                return "DATA_CALL_LIST";
            case 58:
                return "RESET_RADIO";
            case 59:
                return "OEM_HOOK_RAW";
            case RadioError.NETWORK_NOT_READY /*60*/:
                return "OEM_HOOK_STRINGS";
            case RadioError.NOT_PROVISIONED /*61*/:
                return "SCREEN_STATE";
            case RadioError.NO_SUBSCRIPTION /*62*/:
                return "SET_SUPP_SVC_NOTIFICATION";
            case 63:
                return "WRITE_SMS_TO_SIM";
            case 64:
                return "DELETE_SMS_ON_SIM";
            case 65:
                return "SET_BAND_MODE";
            case 66:
                return "QUERY_AVAILABLE_BAND_MODE";
            case 67:
                return "REQUEST_STK_GET_PROFILE";
            case 68:
                return "REQUEST_STK_SET_PROFILE";
            case 69:
                return "REQUEST_STK_SEND_ENVELOPE_COMMAND";
            case 70:
                return "REQUEST_STK_SEND_TERMINAL_RESPONSE";
            case 71:
                return "REQUEST_STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM";
            case 72:
                return "REQUEST_EXPLICIT_CALL_TRANSFER";
            case 73:
                return "REQUEST_SET_PREFERRED_NETWORK_TYPE";
            case 74:
                return "REQUEST_GET_PREFERRED_NETWORK_TYPE";
            case 75:
                return "REQUEST_GET_NEIGHBORING_CELL_IDS";
            case 76:
                return "REQUEST_SET_LOCATION_UPDATES";
            case 77:
                return "RIL_REQUEST_CDMA_SET_SUBSCRIPTION_SOURCE";
            case 78:
                return "RIL_REQUEST_CDMA_SET_ROAMING_PREFERENCE";
            case 79:
                return "RIL_REQUEST_CDMA_QUERY_ROAMING_PREFERENCE";
            case RadioNVItems.RIL_NV_LTE_NEXT_SCAN /*80*/:
                return "RIL_REQUEST_SET_TTY_MODE";
            case 81:
                return "RIL_REQUEST_QUERY_TTY_MODE";
            case RadioNVItems.RIL_NV_LTE_BSR_MAX_TIME /*82*/:
                return "RIL_REQUEST_CDMA_SET_PREFERRED_VOICE_PRIVACY_MODE";
            case 83:
                return "RIL_REQUEST_CDMA_QUERY_PREFERRED_VOICE_PRIVACY_MODE";
            case 84:
                return "RIL_REQUEST_CDMA_FLASH";
            case 85:
                return "RIL_REQUEST_CDMA_BURST_DTMF";
            case 86:
                return "RIL_REQUEST_CDMA_VALIDATE_AND_WRITE_AKEY";
            case 87:
                return "RIL_REQUEST_CDMA_SEND_SMS";
            case 88:
                return "RIL_REQUEST_CDMA_SMS_ACKNOWLEDGE";
            case 89:
                return "RIL_REQUEST_GSM_GET_BROADCAST_CONFIG";
            case 90:
                return "RIL_REQUEST_GSM_SET_BROADCAST_CONFIG";
            case 91:
                return "RIL_REQUEST_GSM_BROADCAST_ACTIVATION";
            case 92:
                return "RIL_REQUEST_CDMA_GET_BROADCAST_CONFIG";
            case 93:
                return "RIL_REQUEST_CDMA_SET_BROADCAST_CONFIG";
            case 94:
                return "RIL_REQUEST_CDMA_BROADCAST_ACTIVATION";
            case 95:
                return "RIL_REQUEST_CDMA_SUBSCRIPTION";
            case 96:
                return "RIL_REQUEST_CDMA_WRITE_SMS_TO_RUIM";
            case 97:
                return "RIL_REQUEST_CDMA_DELETE_SMS_ON_RUIM";
            case 98:
                return "RIL_REQUEST_DEVICE_IDENTITY";
            case 99:
                return "REQUEST_EXIT_EMERGENCY_CALLBACK_MODE";
            case 100:
                return "RIL_REQUEST_GET_SMSC_ADDRESS";
            case 101:
                return "RIL_REQUEST_SET_SMSC_ADDRESS";
            case 102:
                return "RIL_REQUEST_REPORT_SMS_MEMORY_STATUS";
            case 103:
                return "RIL_REQUEST_REPORT_STK_SERVICE_IS_RUNNING";
            case 104:
                return "RIL_REQUEST_CDMA_GET_SUBSCRIPTION_SOURCE";
            case 105:
                return "RIL_REQUEST_ISIM_AUTHENTICATION";
            case 106:
                return "RIL_REQUEST_ACKNOWLEDGE_INCOMING_GSM_SMS_WITH_PDU";
            case 107:
                return "RIL_REQUEST_STK_SEND_ENVELOPE_WITH_STATUS";
            case 108:
                return "RIL_REQUEST_VOICE_RADIO_TECH";
            case 109:
                return "RIL_REQUEST_GET_CELL_INFO_LIST";
            case 110:
                return "RIL_REQUEST_SET_CELL_INFO_LIST_RATE";
            case 111:
                return "RIL_REQUEST_SET_INITIAL_ATTACH_APN";
            case 112:
                return "RIL_REQUEST_IMS_REGISTRATION_STATE";
            case 113:
                return "RIL_REQUEST_IMS_SEND_SMS";
            case 114:
                return "RIL_REQUEST_SIM_TRANSMIT_APDU_BASIC";
            case 115:
                return "RIL_REQUEST_SIM_OPEN_CHANNEL";
            case 116:
                return "RIL_REQUEST_SIM_CLOSE_CHANNEL";
            case 117:
                return "RIL_REQUEST_SIM_TRANSMIT_APDU_CHANNEL";
            case 118:
                return "RIL_REQUEST_NV_READ_ITEM";
            case 119:
                return "RIL_REQUEST_NV_WRITE_ITEM";
            case 120:
                return "RIL_REQUEST_NV_WRITE_CDMA_PRL";
            case 121:
                return "RIL_REQUEST_NV_RESET_CONFIG";
            case 122:
                return "RIL_REQUEST_SET_UICC_SUBSCRIPTION";
            case 123:
                return "RIL_REQUEST_ALLOW_DATA";
            case 124:
                return "GET_HARDWARE_CONFIG";
            case 125:
                return "RIL_REQUEST_SIM_AUTHENTICATION";
            case 128:
                return "RIL_REQUEST_SET_DATA_PROFILE";
            case 129:
                return "RIL_REQUEST_SHUTDOWN";
            case 130:
                return "RIL_REQUEST_GET_RADIO_CAPABILITY";
            case 131:
                return "RIL_REQUEST_SET_RADIO_CAPABILITY";
            case 132:
                return "RIL_REQUEST_START_LCE";
            case 133:
                return "RIL_REQUEST_STOP_LCE";
            case 134:
                return "RIL_REQUEST_PULL_LCEDATA";
            case 135:
                return "RIL_REQUEST_GET_ACTIVITY_INFO";
            case 136:
                return "RIL_REQUEST_SET_ALLOWED_CARRIERS";
            case 137:
                return "RIL_REQUEST_GET_ALLOWED_CARRIERS";
            case 138:
                return "RIL_REQUEST_SEND_DEVICE_STATE";
            case 139:
                return "RIL_REQUEST_SET_UNSOLICITED_RESPONSE_FILTER";
            case 140:
                return "RIL_REQUEST_SET_SIM_CARD_POWER";
            case 141:
                return "RIL_REQUEST_SET_CARRIER_INFO_IMSI_ENCRYPTION";
            case 142:
                return "RIL_REQUEST_START_NETWORK_SCAN";
            case 143:
                return "RIL_REQUEST_STOP_NETWORK_SCAN";
            case 200:
                return "RIL_REQUEST_SIM_QUERY_ATR";
            case 800:
                return "RIL_RESPONSE_ACKNOWLEDGEMENT";
            default:
                return "<unknown request>";
        }
    }

    static String responseToString(int request) {
        switch (request) {
            case 1000:
                return "UNSOL_RESPONSE_RADIO_STATE_CHANGED";
            case 1001:
                return "UNSOL_RESPONSE_CALL_STATE_CHANGED";
            case 1002:
                return "UNSOL_RESPONSE_NETWORK_STATE_CHANGED";
            case 1003:
                return "UNSOL_RESPONSE_NEW_SMS";
            case 1004:
                return "UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT";
            case 1005:
                return "UNSOL_RESPONSE_NEW_SMS_ON_SIM";
            case 1006:
                return "UNSOL_ON_USSD";
            case 1007:
                return "UNSOL_ON_USSD_REQUEST";
            case 1008:
                return "UNSOL_NITZ_TIME_RECEIVED";
            case 1009:
                return "UNSOL_SIGNAL_STRENGTH";
            case 1010:
                return "UNSOL_DATA_CALL_LIST_CHANGED";
            case 1011:
                return "UNSOL_SUPP_SVC_NOTIFICATION";
            case 1012:
                return "UNSOL_STK_SESSION_END";
            case 1013:
                return "UNSOL_STK_PROACTIVE_COMMAND";
            case 1014:
                return "UNSOL_STK_EVENT_NOTIFY";
            case CharacterSets.UTF_16 /*1015*/:
                return "UNSOL_STK_CALL_SETUP";
            case 1016:
                return "UNSOL_SIM_SMS_STORAGE_FULL";
            case 1017:
                return "UNSOL_SIM_REFRESH";
            case 1018:
                return "UNSOL_CALL_RING";
            case 1019:
                return "UNSOL_RESPONSE_SIM_STATUS_CHANGED";
            case 1020:
                return "UNSOL_RESPONSE_CDMA_NEW_SMS";
            case 1021:
                return "UNSOL_RESPONSE_NEW_BROADCAST_SMS";
            case 1022:
                return "UNSOL_CDMA_RUIM_SMS_STORAGE_FULL";
            case ApnTypes.ALL /*1023*/:
                return "UNSOL_RESTRICTED_STATE_CHANGED";
            case android.hardware.radio.V1_0.RadioAccessFamily.HSUPA /*1024*/:
                return "UNSOL_ENTER_EMERGENCY_CALLBACK_MODE";
            case 1025:
                return "UNSOL_CDMA_CALL_WAITING";
            case 1026:
                return "UNSOL_CDMA_OTA_PROVISION_STATUS";
            case 1027:
                return "UNSOL_CDMA_INFO_REC";
            case 1028:
                return "UNSOL_OEM_HOOK_RAW";
            case 1029:
                return "UNSOL_RINGBACK_TONE";
            case 1030:
                return "UNSOL_RESEND_INCALL_MUTE";
            case 1031:
                return "CDMA_SUBSCRIPTION_SOURCE_CHANGED";
            case 1032:
                return "UNSOL_CDMA_PRL_CHANGED";
            case 1033:
                return "UNSOL_EXIT_EMERGENCY_CALLBACK_MODE";
            case 1034:
                return "UNSOL_RIL_CONNECTED";
            case 1035:
                return "UNSOL_VOICE_RADIO_TECH_CHANGED";
            case 1036:
                return "UNSOL_CELL_INFO_LIST";
            case 1037:
                return "UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED";
            case 1038:
                return "RIL_UNSOL_UICC_SUBSCRIPTION_STATUS_CHANGED";
            case 1039:
                return "UNSOL_SRVCC_STATE_NOTIFY";
            case 1040:
                return "RIL_UNSOL_HARDWARE_CONFIG_CHANGED";
            case 1042:
                return "RIL_UNSOL_RADIO_CAPABILITY";
            case 1043:
                return "UNSOL_ON_SS";
            case 1044:
                return "UNSOL_STK_CC_ALPHA_NOTIFY";
            case 1045:
                return "UNSOL_LCE_INFO_RECV";
            case 1046:
                return "UNSOL_PCO_DATA";
            case 1047:
                return "UNSOL_MODEM_RESTART";
            case 1048:
                return "RIL_UNSOL_CARRIER_INFO_IMSI_ENCRYPTION";
            case 1049:
                return "RIL_UNSOL_NETWORK_SCAN_RESULT";
            default:
                return "<unknown response>";
        }
    }

    void riljLog(String msg) {
        Rlog.d(RILJ_LOG_TAG, msg + (this.mPhoneId != null ? " [SUB" + this.mPhoneId + "]" : ""));
    }

    void riljLoge(String msg) {
        Rlog.e(RILJ_LOG_TAG, msg + (this.mPhoneId != null ? " [SUB" + this.mPhoneId + "]" : ""));
    }

    void riljLoge(String msg, Exception e) {
        Rlog.e(RILJ_LOG_TAG, msg + (this.mPhoneId != null ? " [SUB" + this.mPhoneId + "]" : ""), e);
    }

    void riljLogv(String msg) {
        Rlog.v(RILJ_LOG_TAG, msg + (this.mPhoneId != null ? " [SUB" + this.mPhoneId + "]" : ""));
    }

    void unsljLog(int response) {
        riljLog("[UNSL]< " + responseToString(response));
    }

    void unsljLogMore(int response, String more) {
        riljLog("[UNSL]< " + responseToString(response) + " " + more);
    }

    void unsljLogRet(int response, Object ret) {
        riljLog("[UNSL]< " + responseToString(response) + " " + retToString(response, ret));
    }

    void unsljLogvRet(int response, Object ret) {
        riljLogv("[UNSL]< " + responseToString(response) + " " + retToString(response, ret));
    }

    public void setPhoneType(int phoneType) {
        riljLog("setPhoneType=" + phoneType + " old value=" + this.mPhoneType);
        this.mPhoneType = phoneType;
    }

    public void testingEmergencyCall() {
        riljLog("testingEmergencyCall");
        this.mTestingEmergencyCall.set(true);
    }

    /* JADX WARNING: Unexpected end of synchronized block */
    /* JADX WARNING: Missing block: B:6:?, code:
            r9.println(" mWakeLockCount=" + r7.mWakeLockCount);
     */
    /* JADX WARNING: Missing block: B:9:0x0069, code:
            r0 = r7.mRequestList.size();
            r9.println(" mRequestList count=" + r0);
            r1 = 0;
     */
    /* JADX WARNING: Missing block: B:10:0x0087, code:
            if (r1 >= r0) goto L_0x00c4;
     */
    /* JADX WARNING: Missing block: B:11:0x0089, code:
            r2 = (com.android.internal.telephony.RILRequest) r7.mRequestList.valueAt(r1);
            r9.println("  [" + r2.mSerial + "] " + requestToString(r2.mRequest));
            r1 = r1 + 1;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("RIL: " + this);
        pw.println(" mWakeLock=" + this.mWakeLock);
        pw.println(" mWakeLockTimeout=" + this.mWakeLockTimeout);
        synchronized (this.mRequestList) {
            synchronized (this.mWakeLock) {
            }
        }
        pw.println(" mLastNITZTimeInfo=" + Arrays.toString(this.mLastNITZTimeInfo));
        pw.println(" mTestingEmergencyCall=" + this.mTestingEmergencyCall.get());
        this.mClientWakelockTracker.dumpClientRequestTracker(pw);
    }

    public List<ClientRequestStats> getClientRequestStats() {
        return this.mClientWakelockTracker.getClientRequestStats();
    }

    public static ArrayList<Byte> primitiveArrayToArrayList(byte[] arr) {
        ArrayList<Byte> arrayList = new ArrayList(arr.length);
        for (byte b : arr) {
            arrayList.add(Byte.valueOf(b));
        }
        return arrayList;
    }

    public static byte[] arrayListToPrimitiveArray(ArrayList<Byte> bytes) {
        byte[] ret = new byte[bytes.size()];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = ((Byte) bytes.get(i)).byteValue();
        }
        return ret;
    }

    static ArrayList<HardwareConfig> convertHalHwConfigList(ArrayList<HardwareConfig> hwListRil, RIL ril) {
        ArrayList<HardwareConfig> response = new ArrayList(hwListRil.size());
        for (HardwareConfig hwRil : hwListRil) {
            HardwareConfig hw;
            int type = hwRil.type;
            switch (type) {
                case 0:
                    hw = new HardwareConfig(type);
                    HardwareConfigModem hwModem = (HardwareConfigModem) hwRil.modem.get(0);
                    hw.assignModem(hwRil.uuid, hwRil.state, hwModem.rilModel, hwModem.rat, hwModem.maxVoice, hwModem.maxData, hwModem.maxStandby);
                    break;
                case 1:
                    hw = new HardwareConfig(type);
                    hw.assignSim(hwRil.uuid, hwRil.state, ((HardwareConfigSim) hwRil.sim.get(0)).modemUuid);
                    break;
                default:
                    throw new RuntimeException("RIL_REQUEST_GET_HARDWARE_CONFIG invalid hardward type:" + type);
            }
            response.add(hw);
        }
        return response;
    }

    static RadioCapability convertHalRadioCapability(RadioCapability rcRil, RIL ril) {
        int session = rcRil.session;
        int phase = rcRil.phase;
        int rat = rcRil.raf;
        String logicModemUuid = rcRil.logicalModemUuid;
        int status = rcRil.status;
        ril.riljLog("convertHalRadioCapability: session=" + session + ", phase=" + phase + ", rat=" + rat + ", logicModemUuid=" + logicModemUuid + ", status=" + status);
        return new RadioCapability(ril.mPhoneId.intValue(), session, phase, rat, logicModemUuid, status);
    }

    static ArrayList<Integer> convertHalLceData(LceDataInfo lce, RIL ril) {
        ArrayList<Integer> capacityResponse = new ArrayList();
        int capacityDownKbps = lce.lastHopCapacityKbps;
        int confidenceLevel = Byte.toUnsignedInt(lce.confidenceLevel);
        int lceSuspended = lce.lceSuspended ? 1 : 0;
        ril.riljLog("LCE capacity information received: capacity=" + capacityDownKbps + " confidence=" + confidenceLevel + " lceSuspended=" + lceSuspended);
        capacityResponse.add(Integer.valueOf(capacityDownKbps));
        capacityResponse.add(Integer.valueOf(confidenceLevel));
        capacityResponse.add(Integer.valueOf(lceSuspended));
        return capacityResponse;
    }

    static ArrayList<CellInfo> convertHalCellInfoList(ArrayList<android.hardware.radio.V1_0.CellInfo> records) {
        ArrayList<CellInfo> response = new ArrayList(records.size());
        for (android.hardware.radio.V1_0.CellInfo record : records) {
            Parcel p = Parcel.obtain();
            p.writeInt(record.cellInfoType);
            p.writeInt(record.registered ? 1 : 0);
            p.writeInt(record.timeStampType);
            p.writeLong(record.timeStamp);
            switch (record.cellInfoType) {
                case 1:
                    CellInfoGsm cellInfoGsm = (CellInfoGsm) record.gsm.get(0);
                    p.writeInt(Integer.parseInt(cellInfoGsm.cellIdentityGsm.mcc));
                    p.writeInt(Integer.parseInt(cellInfoGsm.cellIdentityGsm.mnc));
                    p.writeInt(cellInfoGsm.cellIdentityGsm.lac);
                    p.writeInt(cellInfoGsm.cellIdentityGsm.cid);
                    p.writeInt(cellInfoGsm.cellIdentityGsm.arfcn);
                    p.writeInt(Byte.toUnsignedInt(cellInfoGsm.cellIdentityGsm.bsic));
                    p.writeInt(cellInfoGsm.signalStrengthGsm.signalStrength);
                    p.writeInt(cellInfoGsm.signalStrengthGsm.bitErrorRate);
                    p.writeInt(cellInfoGsm.signalStrengthGsm.timingAdvance);
                    break;
                case 2:
                    CellInfoCdma cellInfoCdma = (CellInfoCdma) record.cdma.get(0);
                    p.writeInt(cellInfoCdma.cellIdentityCdma.networkId);
                    p.writeInt(cellInfoCdma.cellIdentityCdma.systemId);
                    p.writeInt(cellInfoCdma.cellIdentityCdma.baseStationId);
                    p.writeInt(cellInfoCdma.cellIdentityCdma.longitude);
                    p.writeInt(cellInfoCdma.cellIdentityCdma.latitude);
                    p.writeInt(cellInfoCdma.signalStrengthCdma.dbm);
                    p.writeInt(cellInfoCdma.signalStrengthCdma.ecio);
                    p.writeInt(cellInfoCdma.signalStrengthEvdo.dbm);
                    p.writeInt(cellInfoCdma.signalStrengthEvdo.ecio);
                    p.writeInt(cellInfoCdma.signalStrengthEvdo.signalNoiseRatio);
                    break;
                case 3:
                    CellInfoLte cellInfoLte = (CellInfoLte) record.lte.get(0);
                    p.writeInt(Integer.parseInt(cellInfoLte.cellIdentityLte.mcc));
                    p.writeInt(Integer.parseInt(cellInfoLte.cellIdentityLte.mnc));
                    p.writeInt(cellInfoLte.cellIdentityLte.ci);
                    p.writeInt(cellInfoLte.cellIdentityLte.pci);
                    p.writeInt(cellInfoLte.cellIdentityLte.tac);
                    p.writeInt(cellInfoLte.cellIdentityLte.earfcn);
                    p.writeInt(cellInfoLte.signalStrengthLte.signalStrength);
                    p.writeInt(cellInfoLte.signalStrengthLte.rsrp);
                    p.writeInt(cellInfoLte.signalStrengthLte.rsrq);
                    p.writeInt(cellInfoLte.signalStrengthLte.rssnr);
                    p.writeInt(cellInfoLte.signalStrengthLte.cqi);
                    p.writeInt(cellInfoLte.signalStrengthLte.timingAdvance);
                    break;
                case 4:
                    CellInfoWcdma cellInfoWcdma = (CellInfoWcdma) record.wcdma.get(0);
                    p.writeInt(Integer.parseInt(cellInfoWcdma.cellIdentityWcdma.mcc));
                    p.writeInt(Integer.parseInt(cellInfoWcdma.cellIdentityWcdma.mnc));
                    p.writeInt(cellInfoWcdma.cellIdentityWcdma.lac);
                    p.writeInt(cellInfoWcdma.cellIdentityWcdma.cid);
                    p.writeInt(cellInfoWcdma.cellIdentityWcdma.psc);
                    p.writeInt(cellInfoWcdma.cellIdentityWcdma.uarfcn);
                    p.writeInt(cellInfoWcdma.signalStrengthWcdma.signalStrength);
                    p.writeInt(cellInfoWcdma.signalStrengthWcdma.bitErrorRate);
                    break;
                default:
                    throw new RuntimeException("unexpected cellinfotype: " + record.cellInfoType);
            }
            p.setDataPosition(0);
            CellInfo InfoRec = (CellInfo) CellInfo.CREATOR.createFromParcel(p);
            p.recycle();
            response.add(InfoRec);
        }
        return response;
    }

    static SignalStrength convertHalSignalStrength(android.hardware.radio.V1_0.SignalStrength signalStrength) {
        return new SignalStrength(signalStrength.gw.signalStrength, signalStrength.gw.bitErrorRate, signalStrength.cdma.dbm, signalStrength.cdma.ecio, signalStrength.evdo.dbm, signalStrength.evdo.ecio, signalStrength.evdo.signalNoiseRatio, signalStrength.lte.signalStrength, signalStrength.lte.rsrp, signalStrength.lte.rsrq, signalStrength.lte.rssnr, signalStrength.lte.cqi, signalStrength.tdScdma.rscp, false);
    }

    @VivoHook(hookType = VivoHookType.NEW_METHOD)
    public void processUnsolOemHookRaw(byte[] data) {
        ByteBuffer oemHookResponse = ByteBuffer.wrap(data);
        oemHookResponse.order(ByteOrder.nativeOrder());
        if (isQcUnsolOemHookResp(oemHookResponse)) {
            Rlog.d(RILJ_LOG_TAG, "OEM ID check Passed");
            processUnsolOemhookResponse(oemHookResponse);
        }
    }

    @VivoHook(hookType = VivoHookType.NEW_METHOD)
    public void processUnsolRilConnected() {
        if (this.mHangupInfoCollectHelper != null) {
            this.mHangupInfoCollectHelper.judgeModemTimeout();
        }
    }

    @VivoHook(hookType = VivoHookType.NEW_METHOD)
    public void proceCallStateChanged(State state) {
        this.mHangupInfoCollectHelper.callStateChange(state);
    }

    @VivoHook(hookType = VivoHookType.NEW_METHOD)
    private void broadcastCdmaCardEsnOrMeid(String str) {
        if ("meid_change_flag = 1".equals(str)) {
            riljLog("broadcastCdmaCardEsnOrMeid.");
            this.mContext.sendBroadcast(new Intent("vivo.intent.action.CDMA_CARD_ESN_OR_MEID_QCOM"));
        }
    }

    @VivoHook(hookType = VivoHookType.NEW_METHOD)
    private boolean isQcUnsolOemHookResp(ByteBuffer oemHookResponse) {
        if (oemHookResponse.capacity() < this.mHeaderSize) {
            Rlog.d(RILJ_LOG_TAG, "RIL_UNSOL_OEM_HOOK_RAW data size is " + oemHookResponse.capacity());
            return false;
        }
        byte[] oemIdBytes = new byte[OEM_IDENTIFIER.length()];
        oemHookResponse.get(oemIdBytes);
        String oemIdString = new String(oemIdBytes);
        Rlog.d(RILJ_LOG_TAG, "Oem ID in RIL_UNSOL_OEM_HOOK_RAW is " + oemIdString);
        if (oemIdString.equals(OEM_IDENTIFIER)) {
            return true;
        }
        return false;
    }

    @VivoHook(hookType = VivoHookType.NEW_METHOD)
    protected boolean isInvalidSim() {
        riljLog("isInvalidSim");
        String imsi1 = "";
        String imsi2 = "";
        FtTelephony ftTel = FtTelephonyAdapter.getFtTelephony(this.mContext);
        imsi1 = ftTel.getIMSIBySlotId(0);
        imsi2 = ftTel.getIMSIBySlotId(1);
        if ((imsi1 == null || imsi1.length() < 5) && (imsi2 == null || imsi2.length() < 5)) {
            return true;
        }
        boolean isInvalidSim;
        if (imsi1 != null && imsi1.length() >= 5) {
            imsi1 = imsi1.substring(0, 5);
        }
        if (imsi2 != null && imsi2.length() >= 5) {
            imsi2 = imsi2.substring(0, 5);
        }
        if ("00101".equals(imsi1) || "46099".equals(imsi1) || "00101".equals(imsi2)) {
            isInvalidSim = true;
        } else {
            isInvalidSim = "46099".equals(imsi2);
        }
        Rlog.d(RILJ_LOG_TAG, "isInvalidSim " + isInvalidSim);
        return isInvalidSim;
    }

    @VivoHook(hookType = VivoHookType.NEW_METHOD)
    private void responseMisc(int commandId, String buffer) {
        switch (commandId) {
            case 1:
                broadcastHadoopInfo(buffer);
                return;
            case 4:
                broadcastHadoopInvalidSim(buffer);
                return;
            case 5:
                broadcastCdmaCardEsnOrMeid(buffer);
                return;
            case 7:
                if ((this.mPhoneType == 2 || this.mPhoneType == 6) && !isInvalidSim()) {
                    byte[] requestInfo = new byte[((((OEM_IDENTIFIER.length() + 4) + 4) + 1) + buffer.length())];
                    ByteBuffer buf = ByteBuffer.wrap(requestInfo);
                    buf.order(ByteOrder.nativeOrder());
                    buf.put(OEM_IDENTIFIER.getBytes());
                    buf.putInt(524793);
                    buf.putInt(buffer.length() + 1);
                    buf.put((byte) 13);
                    buf.put(buffer.getBytes());
                    invokeOemRilRequestRaw(requestInfo, null);
                    return;
                }
                return;
            case 11:
                broadcastBandSignal(buffer, 1);
                return;
            case 12:
                broadcastBandSignal(buffer, 2);
                return;
            case 13:
                if (buffer != null) {
                    Intent intent = new Intent("vivo.intent.action.networkbigdata");
                    intent.setPackage("com.vivo.networkimprove");
                    intent.putExtra("info", buffer);
                    this.mContext.sendBroadcast(intent);
                    return;
                }
                return;
            default:
                riljLog("responseMisc: unknown msgType");
                return;
        }
    }

    @VivoHook(hookType = VivoHookType.NEW_METHOD)
    private void processUnsolOemhookResponse(ByteBuffer oemHookResponse) {
        int responseId = oemHookResponse.getInt();
        Rlog.d(RILJ_LOG_TAG, "Response ID in RIL_UNSOL_OEM_HOOK_RAW is " + responseId);
        int responseSize = oemHookResponse.getInt();
        if (responseSize < 0) {
            Rlog.e(RILJ_LOG_TAG, "Response Size is Invalid " + responseSize);
            return;
        }
        byte[] responseData = new byte[responseSize];
        if (oemHookResponse.remaining() == responseSize) {
            oemHookResponse.get(responseData, 0, responseSize);
            switch (responseId) {
                case QCRIL_EVT_HOOK_UNSOL_UI_STATUS_NOT_READY /*526289*/:
                    Rlog.d(RILJ_LOG_TAG, "QCRIL_EVT_HOOK_UNSOL_UI_STATUS_NOT_READY");
                    if (this.mUiNotReadyRegistrant != null) {
                        this.mUiNotReadyRegistrant.notifyRegistrant();
                        break;
                    }
                    break;
                case QCRIL_EVT_HOOK_UNSOL_MCC_REPORT /*526290*/:
                    Rlog.d(RILJ_LOG_TAG, "QCRIL_EVT_HOOK_UNSOL_MCC_REPORT");
                    if (this.mMccChangedRegistrant != null) {
                        this.mMccChangedRegistrant.notifyRegistrant(new AsyncResult(null, responseData, null));
                        break;
                    }
                    break;
                case QCRIL_EVT_HOOK_UNSOL_PD_STATE /*526291*/:
                    Rlog.d(RILJ_LOG_TAG, "QCRIL_EVT_HOOK_UNSOL_PD_STATE");
                    if (this.mAudioPdStateChangeRegistrant != null) {
                        this.mAudioPdStateChangeRegistrant.notifyRegistrant(new AsyncResult(null, responseData, null));
                        break;
                    }
                    break;
                case QCRIL_EVT_HOOK_UNSOL_MISC_INFO /*526292*/:
                    int commandId = responseData[0];
                    String strRst = new String(responseData).substring(1);
                    Rlog.d(RILJ_LOG_TAG, "QCRIL_EVT_HOOK_UNSOL_MISC_INFO, commandId = " + commandId + ", strRst = " + strRst);
                    responseMisc(commandId, strRst);
                    break;
                case QCRIL_EVT_HOOK_UNSOL_SPEECH_CODEC_INFO /*531289*/:
                    Rlog.d(RILJ_LOG_TAG, "QCRIL_EVT_HOOK_UNSOL_SPEECH_CODEC_INFO");
                    AsyncResult ar = new AsyncResult(null, responseData, null);
                    if (this.mSpeechCodecInfoRegistrant != null) {
                        this.mSpeechCodecInfoRegistrant.notifyRegistrant(ar);
                        break;
                    }
                    break;
                default:
                    Rlog.d(RILJ_LOG_TAG, "Response ID " + responseId + " is not served in this process.");
                    break;
            }
            return;
        }
        Rlog.e(RILJ_LOG_TAG, "Response Size(" + responseSize + ") doesnot match remaining bytes(" + oemHookResponse.remaining() + ") in the buffer. So, don't process further");
    }
}
