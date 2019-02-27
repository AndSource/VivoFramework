package com.android.internal.telephony;

import android.annotation.VivoHook;
import android.annotation.VivoHook.VivoHookType;
import android.content.Context;
import android.net.LocalServerSocket;
import android.os.HandlerThread;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings.Global;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.util.LocalLog;
import com.android.internal.telephony.dataconnection.TelephonyNetworkFactory;
import com.android.internal.telephony.euicc.EuiccController;
import com.android.internal.telephony.ims.ImsResolver;
import com.android.internal.telephony.imsphone.ImsPhoneFactory;
import com.android.internal.telephony.sip.SipPhone;
import com.android.internal.telephony.sip.SipPhoneFactory;
import com.android.internal.telephony.uicc.IccCardProxy;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.util.NotificationChannelController;
import com.android.internal.util.IndentingPrintWriter;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

public class PhoneFactory {
    static final boolean DBG = false;
    static final String LOG_TAG = "PhoneFactory";
    public static final int MAX_ACTIVE_PHONES = 1;
    static final int SOCKET_OPEN_MAX_RETRY = 3;
    static final int SOCKET_OPEN_RETRY_MILLIS = 2000;
    private static CommandsInterface sCommandsInterface = null;
    private static CommandsInterface[] sCommandsInterfaces = null;
    private static Context sContext;
    private static EuiccController sEuiccController;
    @VivoHook(hookType = VivoHookType.NEW_FIELD)
    private static HandlerThread sHandlerThread;
    private static ImsResolver sImsResolver;
    private static IntentBroadcaster sIntentBroadcaster;
    private static final HashMap<String, LocalLog> sLocalLogs = new HashMap();
    static final Object sLockProxyPhones = new Object();
    private static boolean sMadeDefaults = false;
    private static NotificationChannelController sNotificationChannelController;
    private static Phone sPhone = null;
    private static PhoneNotifier sPhoneNotifier;
    private static PhoneSwitcher sPhoneSwitcher;
    private static Phone[] sPhones = null;
    private static ProxyController sProxyController;
    private static SubscriptionInfoUpdater sSubInfoRecordUpdater = null;
    private static SubscriptionMonitor sSubscriptionMonitor;
    private static TelephonyNetworkFactory[] sTelephonyNetworkFactories;
    @VivoHook(hookType = VivoHookType.NEW_FIELD)
    private static HandlerThread sTimeZoneHandlerThread;
    private static UiccController sUiccController;

    public static void makeDefaultPhones(Context context) {
        makeDefaultPhone(context);
    }

    /* JADX WARNING: Missing block: B:13:0x001e, code:
            if (sHandlerThread != null) goto L_0x002f;
     */
    /* JADX WARNING: Missing block: B:14:0x0020, code:
            sHandlerThread = new android.os.HandlerThread("commonHandlerThread");
            sHandlerThread.start();
     */
    /* JADX WARNING: Missing block: B:16:0x0031, code:
            if (sTimeZoneHandlerThread != null) goto L_0x0042;
     */
    /* JADX WARNING: Missing block: B:17:0x0033, code:
            sTimeZoneHandlerThread = new android.os.HandlerThread("TimeZoneHandlerThread");
            sTimeZoneHandlerThread.start();
     */
    /* JADX WARNING: Missing block: B:18:0x0042, code:
            com.android.internal.telephony.LocationUpdateHelper.makeDefaultUpdater(r34, sTimeZoneHandlerThread.getLooper());
            com.android.internal.telephony.CustomPlmnOperatorOverride.init(r34);
            com.android.internal.telephony.NitzTimeUpdatePolicy.getInstance(sContext);
            sPhoneNotifier = new com.android.internal.telephony.DefaultPhoneNotifier();
            r2 = com.android.internal.telephony.TelephonyComponentFactory.getInstance();
            r22 = com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager.getDefault(r34);
            android.telephony.Rlog.i(LOG_TAG, "Cdma Subscription set to " + r22);
     */
    /* JADX WARNING: Missing block: B:19:0x008b, code:
            if (r34.getPackageManager().hasSystemFeature("android.hardware.telephony.euicc") == false) goto L_0x0093;
     */
    /* JADX WARNING: Missing block: B:20:0x008d, code:
            sEuiccController = com.android.internal.telephony.euicc.EuiccController.init(r34);
     */
    /* JADX WARNING: Missing block: B:21:0x0093, code:
            r9 = android.telephony.TelephonyManager.getDefault().getPhoneCount();
            r28 = new int[r9];
            sPhones = new com.android.internal.telephony.Phone[r9];
            sCommandsInterfaces = new com.android.internal.telephony.RIL[r9];
            sTelephonyNetworkFactories = new com.android.internal.telephony.dataconnection.TelephonyNetworkFactory[r9];
            com.android.internal.telephony.EmergencyNumberUpdateHelper.makeDefaultUpdater(r34, sHandlerThread.getLooper());
            r6 = 0;
     */
    /* JADX WARNING: Missing block: B:22:0x00b7, code:
            if (r6 >= r9) goto L_0x0112;
     */
    /* JADX WARNING: Missing block: B:23:0x00b9, code:
            r28[r6] = 22;
            android.telephony.Rlog.i(LOG_TAG, "Network Mode set to " + java.lang.Integer.toString(r28[r6]));
            sCommandsInterfaces[r6] = r2.makeRIL(r34, r28[r6], r22, java.lang.Integer.valueOf(r6));
            r6 = r6 + 1;
     */
    /* JADX WARNING: Missing block: B:40:?, code:
            android.telephony.Rlog.i(LOG_TAG, "Creating SubscriptionController");
            r2.initSubscriptionController(r34, sCommandsInterfaces);
            sUiccController = com.android.internal.telephony.uicc.UiccController.make(r34, sCommandsInterfaces);
            r6 = 0;
     */
    /* JADX WARNING: Missing block: B:41:0x012d, code:
            if (r6 >= r9) goto L_0x018c;
     */
    /* JADX WARNING: Missing block: B:42:0x012f, code:
            r30 = null;
            r31 = android.telephony.TelephonyManager.getPhoneType(r28[r6]);
     */
    /* JADX WARNING: Missing block: B:43:0x013a, code:
            if (r31 != 1) goto L_0x0178;
     */
    /* JADX WARNING: Missing block: B:44:0x013c, code:
            r30 = r2.makePhone(r34, sCommandsInterfaces[r6], sPhoneNotifier, r6, 1, r2);
     */
    /* JADX WARNING: Missing block: B:45:0x014a, code:
            android.telephony.Rlog.i(LOG_TAG, "Creating Phone with type = " + r31 + " sub = " + r6);
            sPhones[r6] = r30;
            r6 = r6 + 1;
     */
    /* JADX WARNING: Missing block: B:47:0x017b, code:
            if (r31 != 2) goto L_0x014a;
     */
    /* JADX WARNING: Missing block: B:48:0x017d, code:
            r30 = r2.makePhone(r34, sCommandsInterfaces[r6], sPhoneNotifier, r6, 6, r2);
     */
    /* JADX WARNING: Missing block: B:49:0x018c, code:
            sPhone = sPhones[0];
            sCommandsInterface = sCommandsInterfaces[0];
            r24 = sContext.getResources().getString(17039694);
            android.telephony.Rlog.i(LOG_TAG, "ImsResolver: defaultImsPackage: " + r24);
            sImsResolver = new com.android.internal.telephony.ims.ImsResolver(sContext, r24, r9);
            sImsResolver.populateCacheAndStartBind();
            r23 = com.android.internal.telephony.SmsApplication.getDefaultSmsApplication(r34, true);
            r29 = "NONE";
     */
    /* JADX WARNING: Missing block: B:50:0x01dd, code:
            if (r23 == null) goto L_0x01e3;
     */
    /* JADX WARNING: Missing block: B:51:0x01df, code:
            r29 = r23.getPackageName();
     */
    /* JADX WARNING: Missing block: B:52:0x01e3, code:
            android.telephony.Rlog.i(LOG_TAG, "defaultSmsApplication: " + r29);
            com.android.internal.telephony.SmsApplication.initSmsPackageMonitor(r34);
            sMadeDefaults = true;
            android.telephony.Rlog.i(LOG_TAG, "Creating SubInfoRecordUpdater ");
            sSubInfoRecordUpdater = r2.makeSubscriptionInfoUpdater(com.android.internal.os.BackgroundThread.get().getLooper(), r34, sPhones, sCommandsInterfaces);
            com.android.internal.telephony.SubscriptionController.getInstance().updatePhonesAvailability(sPhones);
            r6 = 0;
     */
    /* JADX WARNING: Missing block: B:53:0x022c, code:
            if (r6 >= r9) goto L_0x0238;
     */
    /* JADX WARNING: Missing block: B:54:0x022e, code:
            sPhones[r6].startMonitoringImsService();
            r6 = r6 + 1;
     */
    /* JADX WARNING: Missing block: B:55:0x0238, code:
            r13 = com.android.internal.telephony.ITelephonyRegistry.Stub.asInterface(android.os.ServiceManager.getService("telephony.registry"));
            r11 = com.android.internal.telephony.SubscriptionController.getInstance();
            sSubscriptionMonitor = new com.android.internal.telephony.SubscriptionMonitor(r13, sContext, r11, r9);
            sPhoneSwitcher = r2.makePhoneSwitcher(1, r9, sContext, r11, android.os.Looper.myLooper(), r13, sCommandsInterfaces, sPhones);
            sProxyController = com.android.internal.telephony.ProxyController.getInstance(r34, sPhones, sUiccController, sCommandsInterfaces, sPhoneSwitcher);
            sIntentBroadcaster = com.android.internal.telephony.IntentBroadcaster.getInstance(r34);
            sNotificationChannelController = new com.android.internal.telephony.util.NotificationChannelController(r34);
            sTelephonyNetworkFactories = new com.android.internal.telephony.dataconnection.TelephonyNetworkFactory[r9];
            r6 = 0;
     */
    /* JADX WARNING: Missing block: B:56:0x0286, code:
            if (r6 >= r9) goto L_0x02aa;
     */
    /* JADX WARNING: Missing block: B:57:0x0288, code:
            sTelephonyNetworkFactories[r6] = new com.android.internal.telephony.dataconnection.TelephonyNetworkFactory(sPhoneSwitcher, r11, sSubscriptionMonitor, android.os.Looper.myLooper(), sContext, r6, sPhones[r6].mDcTracker);
            r6 = r6 + 1;
     */
    /* JADX WARNING: Missing block: B:58:0x02aa, code:
            r2.makeExtTelephonyClasses(r34, sPhones, sCommandsInterfaces);
            com.android.internal.telephony.RoamingStatusCollectHelper.init(getCommonLooper(), r34);
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    @VivoHook(hookType = VivoHookType.CHANGE_CODE)
    public static void makeDefaultPhone(Context context) {
        synchronized (sLockProxyPhones) {
            if (!sMadeDefaults) {
                sContext = context;
                TelephonyDevController.create();
                int retryCount = 0;
                while (true) {
                    boolean hasException = false;
                    retryCount++;
                    try {
                        LocalServerSocket localServerSocket = new LocalServerSocket("com.android.internal.telephony");
                    } catch (IOException e) {
                        hasException = true;
                    }
                    if (!hasException) {
                        break;
                    } else if (retryCount > 3) {
                        throw new RuntimeException("PhoneFactory probably already running");
                    } else {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e2) {
                        }
                    }
                }
            }
        }
    }

    public static Phone getDefaultPhone() {
        Phone phone;
        synchronized (sLockProxyPhones) {
            if (sMadeDefaults) {
                phone = sPhone;
            } else {
                throw new IllegalStateException("Default phones haven't been made yet!");
            }
        }
        return phone;
    }

    public static Phone getPhone(int phoneId) {
        Phone phone;
        String dbgInfo = "";
        synchronized (sLockProxyPhones) {
            if (sMadeDefaults) {
                if (phoneId == Integer.MAX_VALUE) {
                    phone = sPhone;
                } else {
                    if (phoneId >= 0) {
                        if (phoneId < TelephonyManager.getDefault().getPhoneCount()) {
                            phone = sPhones[phoneId];
                        }
                    }
                    phone = null;
                }
            } else {
                throw new IllegalStateException("Default phones haven't been made yet!");
            }
        }
        return phone;
    }

    public static Phone[] getPhones() {
        Phone[] phoneArr;
        synchronized (sLockProxyPhones) {
            if (sMadeDefaults) {
                phoneArr = sPhones;
            } else {
                throw new IllegalStateException("Default phones haven't been made yet!");
            }
        }
        return phoneArr;
    }

    public static ImsResolver getImsResolver() {
        return sImsResolver;
    }

    public static SipPhone makeSipPhone(String sipUri) {
        return SipPhoneFactory.makePhone(sipUri, sContext, sPhoneNotifier);
    }

    @VivoHook(hookType = VivoHookType.CHANGE_CODE)
    public static int calculatePreferredNetworkType(Context context, int phoneSubId) {
        int phoneId = SubscriptionController.getInstance().getPhoneId(phoneSubId);
        int phoneIdNetworkType = 22;
        try {
            phoneIdNetworkType = TelephonyManager.getIntAtIndex(context.getContentResolver(), "preferred_network_mode", phoneId);
        } catch (SettingNotFoundException e) {
            Rlog.e(LOG_TAG, "Settings Exception Reading Valuefor phoneID");
        }
        int networkType = phoneIdNetworkType;
        Rlog.d(LOG_TAG, "calculatePreferredNetworkType: phoneId = " + phoneId + " phoneIdNetworkType = " + phoneIdNetworkType);
        return networkType;
    }

    public static int getDefaultSubscription() {
        return SubscriptionController.getInstance().getDefaultSubId();
    }

    public static boolean isSMSPromptEnabled() {
        int value = 0;
        try {
            value = Global.getInt(sContext.getContentResolver(), "multi_sim_sms_prompt");
        } catch (SettingNotFoundException e) {
            Rlog.e(LOG_TAG, "Settings Exception Reading Dual Sim SMS Prompt Values");
        }
        boolean prompt = value != 0;
        Rlog.d(LOG_TAG, "SMS Prompt option:" + prompt);
        return prompt;
    }

    public static Phone makeImsPhone(PhoneNotifier phoneNotifier, Phone defaultPhone) {
        return ImsPhoneFactory.makePhone(sContext, phoneNotifier, defaultPhone);
    }

    public static void requestEmbeddedSubscriptionInfoListRefresh(Runnable callback) {
        sSubInfoRecordUpdater.requestEmbeddedSubscriptionInfoListRefresh(callback);
    }

    public static void addLocalLog(String key, int size) {
        synchronized (sLocalLogs) {
            if (sLocalLogs.containsKey(key)) {
                throw new IllegalArgumentException("key " + key + " already present");
            }
            sLocalLogs.put(key, new LocalLog(size));
        }
    }

    public static void localLog(String key, String log) {
        synchronized (sLocalLogs) {
            if (sLocalLogs.containsKey(key)) {
                ((LocalLog) sLocalLogs.get(key)).log(log);
            } else {
                throw new IllegalArgumentException("key " + key + " not found");
            }
        }
    }

    @VivoHook(hookType = VivoHookType.NEW_METHOD)
    public static Looper getCommonLooper() {
        if (sHandlerThread != null) {
            return sHandlerThread.getLooper();
        }
        return null;
    }

    public static void dump(FileDescriptor fd, PrintWriter printwriter, String[] args) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printwriter, "  ");
        pw.println("PhoneFactory:");
        pw.println(" sMadeDefaults=" + sMadeDefaults);
        sPhoneSwitcher.dump(fd, pw, args);
        pw.println();
        Phone[] phones = getPhones();
        for (int i = 0; i < phones.length; i++) {
            pw.increaseIndent();
            Phone phone = phones[i];
            try {
                phone.dump(fd, pw, args);
                pw.flush();
                pw.println("++++++++++++++++++++++++++++++++");
                sTelephonyNetworkFactories[i].dump(fd, pw, args);
                pw.flush();
                pw.println("++++++++++++++++++++++++++++++++");
                try {
                    ((IccCardProxy) phone.getIccCard()).dump(fd, pw, args);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                pw.flush();
                pw.decreaseIndent();
                pw.println("++++++++++++++++++++++++++++++++");
            } catch (Exception e2) {
                pw.println("Telephony DebugService: Could not get Phone[" + i + "] e=" + e2);
            }
        }
        pw.println("SubscriptionMonitor:");
        pw.increaseIndent();
        try {
            sSubscriptionMonitor.dump(fd, pw, args);
        } catch (Exception e22) {
            e22.printStackTrace();
        }
        pw.decreaseIndent();
        pw.println("++++++++++++++++++++++++++++++++");
        pw.println("UiccController:");
        pw.increaseIndent();
        try {
            sUiccController.dump(fd, pw, args);
        } catch (Exception e222) {
            e222.printStackTrace();
        }
        pw.flush();
        pw.decreaseIndent();
        pw.println("++++++++++++++++++++++++++++++++");
        if (sEuiccController != null) {
            pw.println("EuiccController:");
            pw.increaseIndent();
            try {
                sEuiccController.dump(fd, pw, args);
            } catch (Exception e2222) {
                e2222.printStackTrace();
            }
            pw.flush();
            pw.decreaseIndent();
            pw.println("++++++++++++++++++++++++++++++++");
        }
        pw.println("SubscriptionController:");
        pw.increaseIndent();
        try {
            SubscriptionController.getInstance().dump(fd, pw, args);
        } catch (Exception e22222) {
            e22222.printStackTrace();
        }
        pw.flush();
        pw.decreaseIndent();
        pw.println("++++++++++++++++++++++++++++++++");
        pw.println("SubInfoRecordUpdater:");
        pw.increaseIndent();
        try {
            sSubInfoRecordUpdater.dump(fd, pw, args);
        } catch (Exception e222222) {
            e222222.printStackTrace();
        }
        pw.flush();
        pw.decreaseIndent();
        pw.println("++++++++++++++++++++++++++++++++");
        pw.println("LocalLogs:");
        pw.increaseIndent();
        synchronized (sLocalLogs) {
            for (String key : sLocalLogs.keySet()) {
                pw.println(key);
                pw.increaseIndent();
                ((LocalLog) sLocalLogs.get(key)).dump(fd, pw, args);
                pw.decreaseIndent();
            }
            pw.flush();
        }
        pw.decreaseIndent();
        pw.println("++++++++++++++++++++++++++++++++");
        pw.println("SharedPreferences:");
        pw.increaseIndent();
        try {
            if (sContext != null) {
                Map spValues = PreferenceManager.getDefaultSharedPreferences(sContext).getAll();
                for (Object key2 : spValues.keySet()) {
                    pw.println(key2 + " : " + spValues.get(key2));
                }
            }
        } catch (Exception e2222222) {
            e2222222.printStackTrace();
        }
        pw.flush();
        pw.decreaseIndent();
    }

    public static SubscriptionInfoUpdater getSubscriptionInfoUpdater() {
        return sSubInfoRecordUpdater;
    }
}
