package android.widget;

import android.app.INotificationManager;
import android.app.ITransientNotification.Stub;
import android.content.Context;
import android.content.res.Resources.NotFoundException;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Telephony.ThreadsColumns;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.BadTokenException;
import android.view.WindowManager.LayoutParams;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import com.android.internal.R;

public class Toast {
    public static final int LENGTH_LONG = 1;
    public static final int LENGTH_SHORT = 0;
    static final String TAG = "Toast";
    static final boolean localLOGV = false;
    private static INotificationManager sService;
    final Context mContext;
    int mDuration;
    View mNextView;
    final TN mTN;

    private static class TN extends Stub {
        private static final int CANCEL = 2;
        private static final int HIDE = 1;
        static final long LONG_DURATION_TIMEOUT = 7000;
        static final long SHORT_DURATION_TIMEOUT = 4000;
        private static final int SHOW = 0;
        int mDuration;
        int mGravity;
        final Handler mHandler;
        float mHorizontalMargin;
        View mNextView;
        String mPackageName;
        private final LayoutParams mParams = new LayoutParams();
        float mVerticalMargin;
        View mView;
        WindowManager mWM;
        int mX;
        int mY;

        TN(String packageName, Looper looper) {
            LayoutParams params = this.mParams;
            params.height = -2;
            params.width = -2;
            params.format = -3;
            params.windowAnimations = R.style.Animation_Toast;
            params.type = LayoutParams.TYPE_TOAST;
            params.setTitle(Toast.TAG);
            params.flags = 152;
            this.mPackageName = packageName;
            if (looper == null) {
                looper = Looper.myLooper();
                if (looper == null) {
                    throw new RuntimeException("Can't toast on a thread that has not called Looper.prepare()");
                }
            }
            this.mHandler = new Handler(looper, null) {
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case 0:
                            TN.this.handleShow(msg.obj);
                            return;
                        case 1:
                            TN.this.handleHide();
                            TN.this.mNextView = null;
                            return;
                        case 2:
                            TN.this.handleHide();
                            TN.this.mNextView = null;
                            try {
                                Toast.getService().cancelToast(TN.this.mPackageName, TN.this);
                                return;
                            } catch (RemoteException e) {
                                return;
                            }
                        default:
                            return;
                    }
                }
            };
        }

        public void show(IBinder windowToken) {
            this.mHandler.obtainMessage(0, windowToken).sendToTarget();
        }

        public void hide() {
            this.mHandler.obtainMessage(1).sendToTarget();
        }

        public void cancel() {
            this.mHandler.obtainMessage(2).sendToTarget();
        }

        /* JADX WARNING: Missing block: B:4:0x0014, code:
            return;
     */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public void handleShow(IBinder windowToken) {
            if (!(this.mHandler.hasMessages(2) || this.mHandler.hasMessages(1) || this.mView == this.mNextView)) {
                handleHide();
                this.mView = this.mNextView;
                Context context = this.mView.getContext().getApplicationContext();
                String packageName = this.mView.getContext().getOpPackageName();
                if (context == null) {
                    context = this.mView.getContext();
                }
                this.mWM = (WindowManager) context.getSystemService("window");
                int gravity = Gravity.getAbsoluteGravity(this.mGravity, this.mView.getContext().getResources().getConfiguration().getLayoutDirection());
                this.mParams.gravity = gravity;
                if ((gravity & 7) == 7) {
                    this.mParams.horizontalWeight = 1.0f;
                }
                if ((gravity & 112) == 112) {
                    this.mParams.verticalWeight = 1.0f;
                }
                this.mParams.x = this.mX;
                this.mParams.y = this.mY;
                this.mParams.verticalMargin = this.mVerticalMargin;
                this.mParams.horizontalMargin = this.mHorizontalMargin;
                this.mParams.packageName = packageName;
                this.mParams.hideTimeoutMilliseconds = this.mDuration == 1 ? LONG_DURATION_TIMEOUT : SHORT_DURATION_TIMEOUT;
                this.mParams.token = windowToken;
                if (this.mView.getParent() != null) {
                    this.mWM.removeView(this.mView);
                }
                try {
                    this.mWM.addView(this.mView, this.mParams);
                    trySendAccessibilityEvent();
                } catch (BadTokenException e) {
                }
            }
        }

        private void trySendAccessibilityEvent() {
            AccessibilityManager accessibilityManager = AccessibilityManager.getInstance(this.mView.getContext());
            if (accessibilityManager.isEnabled()) {
                AccessibilityEvent event = AccessibilityEvent.obtain(64);
                event.setClassName(getClass().getName());
                event.setPackageName(this.mView.getContext().getPackageName());
                this.mView.dispatchPopulateAccessibilityEvent(event);
                accessibilityManager.sendAccessibilityEvent(event);
            }
        }

        public void handleHide() {
            if (this.mView != null) {
                if (this.mView.getParent() != null) {
                    this.mWM.removeViewImmediate(this.mView);
                }
                try {
                    Toast.getService().finishToken(this.mPackageName, this);
                } catch (RemoteException e) {
                }
                this.mView = null;
            }
        }
    }

    public Toast(Context context) {
        this(context, null);
    }

    public Toast(Context context, Looper looper) {
        this.mContext = context;
        this.mTN = new TN(context.getPackageName(), looper);
        this.mTN.mY = context.getResources().getDimensionPixelSize(R.dimen.toast_y_offset);
        this.mTN.mGravity = context.getResources().getInteger(R.integer.config_toastDefaultGravity);
    }

    public void show() {
        if (this.mNextView == null) {
            throw new RuntimeException("setView must have been called");
        }
        INotificationManager service = getService();
        String pkg = this.mContext.getOpPackageName();
        TN tn = this.mTN;
        tn.mNextView = this.mNextView;
        tn.mParams.traditonToast = 1;
        try {
            service.enqueueToast(pkg, tn, this.mDuration);
        } catch (RemoteException e) {
        }
    }

    public void cancel() {
        this.mTN.mParams.traditonToast = 0;
        this.mTN.cancel();
    }

    public void setView(View view) {
        this.mNextView = view;
    }

    public View getView() {
        return this.mNextView;
    }

    public void setDuration(int duration) {
        this.mDuration = duration;
        this.mTN.mDuration = duration;
    }

    public int getDuration() {
        return this.mDuration;
    }

    public void setMargin(float horizontalMargin, float verticalMargin) {
        this.mTN.mHorizontalMargin = horizontalMargin;
        this.mTN.mVerticalMargin = verticalMargin;
    }

    public float getHorizontalMargin() {
        return this.mTN.mHorizontalMargin;
    }

    public float getVerticalMargin() {
        return this.mTN.mVerticalMargin;
    }

    public void setGravity(int gravity, int xOffset, int yOffset) {
        this.mTN.mGravity = gravity;
        this.mTN.mX = xOffset;
        this.mTN.mY = yOffset;
    }

    public int getGravity() {
        return this.mTN.mGravity;
    }

    public int getXOffset() {
        return this.mTN.mX;
    }

    public int getYOffset() {
        return this.mTN.mY;
    }

    public LayoutParams getWindowParams() {
        return this.mTN.mParams;
    }

    public static Toast makeText(Context context, CharSequence text, int duration) {
        return makeText(context, null, text, duration);
    }

    public static Toast makeText(Context context, Looper looper, CharSequence text, int duration) {
        Toast result = new Toast(context, looper);
        View v = ((LayoutInflater) context.getSystemService("layout_inflater")).inflate((int) R.layout.transient_notification, null);
        TextView tv = (TextView) v.findViewById(R.id.message);
        tv.setText(text);
        change2VivoFont(tv);
        result.mNextView = v;
        result.mDuration = duration;
        return result;
    }

    public static Toast makeText(Context context, int resId, int duration) throws NotFoundException {
        return makeText(context, context.getResources().getText(resId), duration);
    }

    public void setText(int resId) {
        setText(this.mContext.getText(resId));
    }

    public void setText(CharSequence s) {
        if (this.mNextView == null) {
            throw new RuntimeException("This Toast was not created with Toast.makeText()");
        }
        TextView tv = (TextView) this.mNextView.findViewById(R.id.message);
        if (tv == null) {
            throw new RuntimeException("This Toast was not created with Toast.makeText()");
        }
        tv.setText(s);
        change2VivoFont(tv);
    }

    private static void change2VivoFont(TextView tx) {
        if (tx == null) {
            Log.w(TAG, "Toast text is null.");
            return;
        }
        if (!Typeface.isAndroidFont()) {
            tx.setTypeface(Typeface.SANS_SERIF);
        }
    }

    private static INotificationManager getService() {
        if (sService != null) {
            return sService;
        }
        sService = INotificationManager.Stub.asInterface(ServiceManager.getService(ThreadsColumns.NOTIFICATION));
        return sService;
    }
}
