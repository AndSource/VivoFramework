package android.os;

import android.app.anr.ANRMonitor;
import android.net.wifi.WifiEnterpriseConfig;
import android.util.Log;
import android.util.Printer;
import android.util.Slog;
import android.util.proto.ProtoOutputStream;

public final class Looper {
    private static final String TAG = "Looper";
    private static Looper sMainLooper;
    static final ThreadLocal<Looper> sThreadLocal = new ThreadLocal();
    private Message mDispatchingMessage = null;
    private Printer mLogging;
    private ANRMonitor mMonitor = null;
    final MessageQueue mQueue;
    private long mSlowDispatchThresholdMs;
    final Thread mThread;
    private long mTraceTag;

    public static void prepare() {
        prepare(true);
    }

    private static void prepare(boolean quitAllowed) {
        if (sThreadLocal.get() != null) {
            throw new RuntimeException("Only one Looper may be created per thread");
        }
        sThreadLocal.set(new Looper(quitAllowed));
    }

    public static void prepareMainLooper() {
        prepare(false);
        synchronized (Looper.class) {
            if (sMainLooper != null) {
                throw new IllegalStateException("The main Looper has already been prepared.");
            }
            sMainLooper = myLooper();
        }
    }

    public static Looper getMainLooper() {
        Looper looper;
        synchronized (Looper.class) {
            looper = sMainLooper;
        }
        return looper;
    }

    public static void loop() {
        Looper me = myLooper();
        if (me == null) {
            throw new RuntimeException("No Looper; Looper.prepare() wasn't called on this thread.");
        }
        MessageQueue queue = me.mQueue;
        Binder.clearCallingIdentity();
        long ident = Binder.clearCallingIdentity();
        while (true) {
            Message msg = queue.next();
            if (msg != null) {
                long end;
                Printer logging = me.mLogging;
                if (logging != null) {
                    logging.println(">>>>> Dispatching to " + msg.target + WifiEnterpriseConfig.CA_CERT_ALIAS_DELIMITER + msg.callback + ": " + msg.what);
                }
                long slowDispatchThresholdMs = me.mSlowDispatchThresholdMs;
                long traceTag = me.mTraceTag;
                if (traceTag != 0 && Trace.isTagEnabled(traceTag)) {
                    Trace.traceBegin(traceTag, msg.target.getTraceName(msg));
                }
                long start = slowDispatchThresholdMs == 0 ? 0 : SystemClock.uptimeMillis();
                ANRMonitor monitor = me.mMonitor;
                if (monitor != null) {
                    try {
                        me.mDispatchingMessage = msg;
                        monitor.beginDispatch(msg);
                        msg.target.dispatchMessage(msg);
                        end = slowDispatchThresholdMs == 0 ? 0 : SystemClock.uptimeMillis();
                        if (traceTag != 0) {
                            Trace.traceEnd(traceTag);
                        }
                        monitor.postDispatch();
                        me.mDispatchingMessage = null;
                    } catch (Throwable th) {
                        monitor.postDispatch();
                        me.mDispatchingMessage = null;
                    }
                } else {
                    try {
                        msg.target.dispatchMessage(msg);
                        end = slowDispatchThresholdMs == 0 ? 0 : SystemClock.uptimeMillis();
                        if (traceTag != 0) {
                            Trace.traceEnd(traceTag);
                        }
                    } catch (Throwable th2) {
                        if (traceTag != 0) {
                            Trace.traceEnd(traceTag);
                        }
                    }
                }
                if (slowDispatchThresholdMs > 0) {
                    long time = end - start;
                    if (time > slowDispatchThresholdMs) {
                        Slog.w(TAG, "Dispatch took " + time + "ms on " + Thread.currentThread().getName() + ", h=" + msg.target + " cb=" + msg.callback + " msg=" + msg.what);
                    }
                }
                if (logging != null) {
                    logging.println("<<<<< Finished to " + msg.target + WifiEnterpriseConfig.CA_CERT_ALIAS_DELIMITER + msg.callback);
                }
                long newIdent = Binder.clearCallingIdentity();
                if (ident != newIdent) {
                    Log.wtf(TAG, "Thread identity changed from 0x" + Long.toHexString(ident) + " to 0x" + Long.toHexString(newIdent) + " while dispatching to " + msg.target.getClass().getName() + WifiEnterpriseConfig.CA_CERT_ALIAS_DELIMITER + msg.callback + " what=" + msg.what);
                }
                msg.recycleUnchecked();
            } else {
                return;
            }
        }
    }

    public static Looper myLooper() {
        return (Looper) sThreadLocal.get();
    }

    public static MessageQueue myQueue() {
        return myLooper().mQueue;
    }

    private Looper(boolean quitAllowed) {
        this.mQueue = new MessageQueue(quitAllowed);
        this.mThread = Thread.currentThread();
    }

    public void setMonitor(ANRMonitor monitor) {
        this.mMonitor = monitor;
    }

    public boolean isCurrentThread() {
        return Thread.currentThread() == this.mThread;
    }

    public void setMessageLogging(Printer printer) {
        this.mLogging = printer;
    }

    public void setTraceTag(long traceTag) {
        this.mTraceTag = traceTag;
    }

    public void setSlowDispatchThresholdMs(long slowDispatchThresholdMs) {
        this.mSlowDispatchThresholdMs = slowDispatchThresholdMs;
    }

    public void quit() {
        this.mQueue.quit(false);
    }

    public void quitSafely() {
        this.mQueue.quit(true);
    }

    public Thread getThread() {
        return this.mThread;
    }

    public MessageQueue getQueue() {
        return this.mQueue;
    }

    public void dump(Printer pw, String prefix) {
        pw.println(prefix + toString());
        Message msg = this.mDispatchingMessage;
        String tmp = null;
        if (msg != null) {
            try {
                tmp = msg.toString();
            } catch (Exception e) {
            }
        }
        pw.println(prefix + "  Now dispatching: " + tmp);
        this.mQueue.dump(pw, prefix + "  ", null);
    }

    public void dump(Printer pw, String prefix, Handler handler) {
        pw.println(prefix + toString());
        Message msg = this.mDispatchingMessage;
        String tmp = null;
        if (msg != null) {
            try {
                tmp = msg.toString();
            } catch (Exception e) {
            }
        }
        pw.println(prefix + "  Now dispatching: " + tmp);
        this.mQueue.dump(pw, prefix + "  ", handler);
    }

    public void writeToProto(ProtoOutputStream proto, long fieldId) {
        long looperToken = proto.start(fieldId);
        proto.write(1159641169921L, this.mThread.getName());
        proto.write(LooperProto.THREAD_ID, this.mThread.getId());
        proto.write(1112396529667L, System.identityHashCode(this));
        this.mQueue.writeToProto(proto, LooperProto.QUEUE);
        proto.end(looperToken);
    }

    public String toString() {
        return "Looper (" + this.mThread.getName() + ", tid " + this.mThread.getId() + ") {" + Integer.toHexString(System.identityHashCode(this)) + "}";
    }
}
