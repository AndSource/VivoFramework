package com.android.internal.policy;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionLegacyHelper;
import android.net.Uri;
import android.os.Bundle;
import android.os.FtBuild;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.BrowserContract;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;
import android.provider.SettingsStringUtil;
import android.text.TextUtils;
import android.transition.Scene;
import android.transition.Transition;
import android.transition.TransitionInflater;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.AndroidRuntimeException;
import android.util.EventLog;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.IRotationWatcher.Stub;
import android.view.IWindowManager;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.InputQueue;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.KeyEvent.DispatcherState;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.SurfaceHolder.Callback2;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewManager;
import android.view.ViewParent;
import android.view.ViewRootImpl;
import android.view.ViewRootImpl.ActivityConfigCallback;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.android.internal.R;
import com.android.internal.policy.NavigationBarWindow.NavigationBarColorCallback;
import com.android.internal.view.menu.ContextMenuBuilder;
import com.android.internal.view.menu.IconMenuPresenter;
import com.android.internal.view.menu.ListMenuPresenter;
import com.android.internal.view.menu.MenuBuilder;
import com.android.internal.view.menu.MenuBuilder.Callback;
import com.android.internal.view.menu.MenuDialogHelper;
import com.android.internal.view.menu.MenuHelper;
import com.android.internal.view.menu.MenuPresenter;
import com.android.internal.view.menu.MenuView;
import com.android.internal.view.menu.VivoIconMenuPresenter;
import com.android.internal.view.menu.VivoListMenuPresenter;
import com.android.internal.widget.DecorContentParent;
import com.android.internal.widget.SwipeDismissLayout;
import com.android.internal.widget.SwipeDismissLayout.OnDismissedListener;
import com.android.internal.widget.SwipeDismissLayout.OnSwipeProgressChangedListener;
import com.vivo.services.security.client.VivoPermissionManager;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import vivo.util.VivoThemeUtil;

public class PhoneWindow extends Window implements Callback, NavigationBarWindow.Callback {
    private static final String ACTION_BAR_TAG = "android:ActionBar";
    private static final String CUSTOM_META_VAULE = "custom";
    private static final int CUSTOM_TITLE_COMPATIBLE_FEATURES = 13505;
    private static final boolean DEBUG = false;
    private static final int DEFAULT_BACKGROUND_FADE_DURATION_MS = 300;
    static final int DEFAULT_DARK_COVER_COLOR = -16777216;
    static final int DEFAULT_LIGHT_COVER_COLOR = -592138;
    private static final String DEFAULT_META_VAULE = "default";
    static final int FLAG_RESOURCE_SET_ICON = 1;
    static final int FLAG_RESOURCE_SET_ICON_FALLBACK = 4;
    static final int FLAG_RESOURCE_SET_LOGO = 2;
    private static final String FOCUSED_ID_TAG = "android:focusedViewId";
    private static final String FOLLOW_SYSTEM_META_VAULE = "followSystem";
    private static final String FORCED_FOLLOW_META_VAULE = "forcedFollowSystem";
    private static final String FRINGE_COVER_METAKEY = "com.vivo.window.fringeCoverColor";
    private static final String HOME_INDICATOR_COVER_METAKEY = "android.vendor.home_indicator_cover_color";
    private static final String HOME_INDICATOR_STATE_METAKEY = "android.vendor.home_indicator";
    private static final String KEEP_FULLSCREEN_METAKEY = "android.vendor.full_screen";
    private static final int KEEP_FULLSCREEN_TYPE_KEEPFULL = 1;
    private static final int KEEP_FULLSCREEN_TYPE_NOTFULL = 0;
    private static final String NAVBAR_COLOR_METAKEY = "com.vivo.systemui.navColorFollowSystem";
    private static final String PANELS_TAG = "android:Panels";
    private static final String STATUSBAR_COLOR_METAKEY = "com.vivo.systemui.statusbarColorFollowSystem";
    private static String TAG = "PhoneWindow";
    private static final Transition USE_DEFAULT_TRANSITION = new TransitionSet();
    private static final String VIEWS_TAG = "android:views";
    static final String[] mFilterPKG = new String[]{"com.ubercab"};
    private static boolean mKeepFullUserConfigApp = false;
    private static boolean mKeepFullUserConfigCmp = false;
    static final RotationWatcher sRotationWatcher = new RotationWatcher();
    private final boolean DEBUG_SYSTEMUI;
    private ActionMenuPresenterCallback mActionMenuPresenterCallback;
    private ActivityConfigCallback mActivityConfigCallback;
    private Boolean mAllowEnterTransitionOverlap;
    private Boolean mAllowReturnTransitionOverlap;
    private boolean mAlwaysReadCloseOnTouchAttr;
    private AudioManager mAudioManager;
    private Drawable mBackgroundDrawable;
    private long mBackgroundFadeDurationMillis;
    int mBackgroundFallbackResource;
    int mBackgroundResource;
    private ProgressBar mCircularProgressBar;
    private boolean mClipToOutline;
    private boolean mClosingActionMenu;
    private String mClsName;
    private ComponentName mComponentName;
    private boolean mConfigInited;
    ViewGroup mContentParent;
    private boolean mContentParentExplicitlySet;
    private Scene mContentScene;
    ContextMenuBuilder mContextMenu;
    final PhoneWindowMenuCallback mContextMenuCallback;
    MenuHelper mContextMenuHelper;
    private DecorView mDecor;
    private int mDecorCaptionShade;
    DecorContentParent mDecorContentParent;
    private DrawableFeatureState[] mDrawables;
    private float mElevation;
    boolean mEnableFringeCover;
    boolean mEnableHomeIndicatorCover;
    private Transition mEnterTransition;
    private Transition mExitTransition;
    TypedValue mFixedHeightMajor;
    TypedValue mFixedHeightMinor;
    TypedValue mFixedWidthMajor;
    TypedValue mFixedWidthMinor;
    private boolean mForceDecorInstall;
    boolean mForcedCoverColor;
    boolean mForcedHomeIndicatorCoverColor;
    private boolean mForcedNavigationBarColor;
    private boolean mForcedNavigationBarColorInTheme;
    private boolean mForcedStatusBarColor;
    private boolean mForcedStatusBarColorInTheme;
    private int mFrameResource;
    private float mFreeformAlpha;
    int mFringeCoverColor;
    private String mFringeCoverColorUserConfig;
    private final String mGMSAppPrefix;
    int mHomeIndicatorCoverColor;
    private String mHomeIndicatorCoverColorUserConfig;
    boolean mHomeIndicatorOn;
    int mHomeIndicatorState;
    private String mHomeIndicatorStateListConfig;
    private String mHomeIndicatorStateUserConfig;
    private ProgressBar mHorizontalProgressBar;
    int mIconRes;
    int mInternalFlag;
    private int mInvalidatePanelMenuFeatures;
    private boolean mInvalidatePanelMenuPosted;
    private final Runnable mInvalidatePanelMenuRunnable;
    boolean mIsFloating;
    private boolean mIsStartingWindow;
    private boolean mIsTranslucent;
    private boolean mIsVivoApp;
    private String mKeepFullListConfig;
    int mKeepFullScreen;
    private KeyguardManager mKeyguardManager;
    private LayoutInflater mLayoutInflater;
    private ImageView mLeftIconView;
    private boolean mLoadElevation;
    private String mLogTag;
    int mLogoRes;
    private MediaController mMediaController;
    final TypedValue mMinWidthMajor;
    final TypedValue mMinWidthMinor;
    private String mNavColorConfig;
    int mNavigationBarColor;
    private NavigationBarColorCallback mNavigationBarColorCallback;
    int mNavigationBarDividerColor;
    private final ArrayList<String> mOtherGMSApp;
    int mPanelChordingKey;
    private PanelMenuPresenterCallback mPanelMenuPresenterCallback;
    private PanelFeatureState[] mPanels;
    PanelFeatureState mPreparedPanel;
    private Transition mReenterTransition;
    int mResourcesSetFlags;
    private Transition mReturnTransition;
    private ImageView mRightIconView;
    int mRotation;
    private String mSBColorConfig;
    private Transition mSharedElementEnterTransition;
    private Transition mSharedElementExitTransition;
    private Transition mSharedElementReenterTransition;
    private Transition mSharedElementReturnTransition;
    private Boolean mSharedElementsUseOverlay;
    int mStatusBarColor;
    private boolean mSupportsPictureInPicture;
    private boolean mSystemApp;
    InputQueue.Callback mTakeInputQueueCallback;
    Callback2 mTakeSurfaceCallback;
    private int mTextColor;
    private int mTheme;
    CharSequence mTitle;
    private int mTitleColor;
    private TextView mTitleView;
    private TransitionManager mTransitionManager;
    private int mUiOptions;
    private boolean mUseDecorContext;
    boolean mVivoStyle;
    private int mVolumeControlStreamType;

    private final class ActionMenuPresenterCallback implements MenuPresenter.Callback {
        /* synthetic */ ActionMenuPresenterCallback(PhoneWindow this$0, ActionMenuPresenterCallback -this1) {
            this();
        }

        private ActionMenuPresenterCallback() {
        }

        public boolean onOpenSubMenu(MenuBuilder subMenu) {
            Window.Callback cb = PhoneWindow.this.getCallback();
            if (cb == null) {
                return false;
            }
            cb.onMenuOpened(8, subMenu);
            return true;
        }

        public void onCloseMenu(MenuBuilder menu, boolean allMenusAreClosing) {
            PhoneWindow.this.checkCloseActionMenu(menu);
        }
    }

    private static final class DrawableFeatureState {
        int alpha = 255;
        Drawable child;
        Drawable cur;
        int curAlpha = 255;
        Drawable def;
        final int featureId;
        Drawable local;
        int resid;
        Uri uri;

        DrawableFeatureState(int _featureId) {
            this.featureId = _featureId;
        }
    }

    static final class PanelFeatureState {
        int background;
        View createdPanelView;
        DecorView decorView;
        int featureId;
        Bundle frozenActionViewState;
        Bundle frozenMenuState;
        int fullBackground;
        int gravity;
        IconMenuPresenter iconMenuPresenter;
        boolean isCompact;
        boolean isHandled;
        boolean isInExpandedMode;
        boolean isOpen;
        boolean isPrepared;
        ListMenuPresenter listMenuPresenter;
        int listPresenterTheme;
        boolean mVivoStyle;
        MenuBuilder menu;
        public boolean qwertyMode;
        boolean refreshDecorView = false;
        boolean refreshMenuContent;
        View shownPanelView;
        boolean wasLastExpanded;
        boolean wasLastOpen;
        int windowAnimations;
        int x;
        int y;

        private static class SavedState implements Parcelable {
            public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
                public SavedState createFromParcel(Parcel in) {
                    return SavedState.readFromParcel(in);
                }

                public SavedState[] newArray(int size) {
                    return new SavedState[size];
                }
            };
            int featureId;
            boolean isInExpandedMode;
            boolean isOpen;
            Bundle menuState;

            /* synthetic */ SavedState(SavedState -this0) {
                this();
            }

            private SavedState() {
            }

            public int describeContents() {
                return 0;
            }

            public void writeToParcel(Parcel dest, int flags) {
                int i = 1;
                dest.writeInt(this.featureId);
                dest.writeInt(this.isOpen ? 1 : 0);
                if (!this.isInExpandedMode) {
                    i = 0;
                }
                dest.writeInt(i);
                if (this.isOpen) {
                    dest.writeBundle(this.menuState);
                }
            }

            private static SavedState readFromParcel(Parcel source) {
                boolean z = true;
                SavedState savedState = new SavedState();
                savedState.featureId = source.readInt();
                savedState.isOpen = source.readInt() == 1;
                if (source.readInt() != 1) {
                    z = false;
                }
                savedState.isInExpandedMode = z;
                if (savedState.isOpen) {
                    savedState.menuState = source.readBundle();
                }
                return savedState;
            }
        }

        PanelFeatureState(int featureId) {
            this.featureId = featureId;
        }

        public boolean isInListMode() {
            return !this.isInExpandedMode ? this.isCompact : true;
        }

        public boolean hasPanelItems() {
            boolean z = true;
            if (this.shownPanelView == null) {
                return false;
            }
            if (this.createdPanelView != null) {
                return true;
            }
            if (this.isCompact || this.isInExpandedMode) {
                return this.listMenuPresenter.getAdapter().getCount() > 0;
            }
            if (((ViewGroup) this.shownPanelView).getChildCount() <= 0) {
                z = false;
            }
            return z;
        }

        public void clearMenuPresenters() {
            if (this.menu != null) {
                this.menu.removeMenuPresenter(this.iconMenuPresenter);
                this.menu.removeMenuPresenter(this.listMenuPresenter);
            }
            this.iconMenuPresenter = null;
            this.listMenuPresenter = null;
        }

        void setStyle(Context context) {
            TypedArray a = context.obtainStyledAttributes(R.styleable.Theme);
            this.background = a.getResourceId(46, 0);
            this.fullBackground = a.getResourceId(47, 0);
            this.windowAnimations = a.getResourceId(93, 0);
            this.isCompact = a.getBoolean(306, false);
            this.listPresenterTheme = a.getResourceId(307, R.style.Theme_ExpandedMenu);
            a.recycle();
        }

        void setMenu(MenuBuilder menu) {
            if (menu != this.menu) {
                if (this.menu != null) {
                    this.menu.removeMenuPresenter(this.iconMenuPresenter);
                    this.menu.removeMenuPresenter(this.listMenuPresenter);
                }
                this.menu = menu;
                if (menu != null) {
                    if (this.iconMenuPresenter != null) {
                        menu.addMenuPresenter(this.iconMenuPresenter);
                    }
                    if (this.listMenuPresenter != null) {
                        menu.addMenuPresenter(this.listMenuPresenter);
                    }
                }
            }
        }

        MenuView getListMenuView(Context context, MenuPresenter.Callback cb) {
            if (this.menu == null) {
                return null;
            }
            if (!this.isCompact) {
                getIconMenuView(context, cb);
            }
            if (this.listMenuPresenter == null) {
                this.listMenuPresenter = new ListMenuPresenter((int) R.layout.list_menu_item_layout, this.listPresenterTheme);
                this.listMenuPresenter.setCallback(cb);
                this.listMenuPresenter.setId(R.id.list_menu_presenter);
                this.menu.addMenuPresenter(this.listMenuPresenter);
            }
            if (this.iconMenuPresenter != null) {
                this.listMenuPresenter.setItemIndexOffset(this.iconMenuPresenter.getNumActualItemsShown());
            }
            return this.listMenuPresenter.getMenuView(this.decorView);
        }

        MenuView getIconMenuView(Context context, MenuPresenter.Callback cb) {
            if (this.menu == null) {
                return null;
            }
            if (this.iconMenuPresenter == null) {
                if (!this.mVivoStyle) {
                    this.iconMenuPresenter = new IconMenuPresenter(context);
                } else if (FtBuild.getRomVersion() < 3.0f) {
                    this.iconMenuPresenter = new VivoListMenuPresenter(context);
                } else {
                    this.iconMenuPresenter = new VivoIconMenuPresenter(context);
                }
                this.iconMenuPresenter.setCallback(cb);
                this.iconMenuPresenter.setId(R.id.icon_menu_presenter);
                this.menu.addMenuPresenter(this.iconMenuPresenter);
            }
            return this.iconMenuPresenter.getMenuView(this.decorView);
        }

        Parcelable onSaveInstanceState() {
            SavedState savedState = new SavedState();
            savedState.featureId = this.featureId;
            savedState.isOpen = this.isOpen;
            savedState.isInExpandedMode = this.isInExpandedMode;
            if (this.menu != null) {
                savedState.menuState = new Bundle();
                this.menu.savePresenterStates(savedState.menuState);
            }
            return savedState;
        }

        void onRestoreInstanceState(Parcelable state) {
            SavedState savedState = (SavedState) state;
            this.featureId = savedState.featureId;
            this.wasLastOpen = savedState.isOpen;
            this.wasLastExpanded = savedState.isInExpandedMode;
            this.frozenMenuState = savedState.menuState;
            this.createdPanelView = null;
            this.shownPanelView = null;
            this.decorView = null;
        }

        void applyFrozenState() {
            if (this.menu != null && this.frozenMenuState != null) {
                this.menu.restorePresenterStates(this.frozenMenuState);
                this.frozenMenuState = null;
            }
        }
    }

    private class PanelMenuPresenterCallback implements MenuPresenter.Callback {
        /* synthetic */ PanelMenuPresenterCallback(PhoneWindow this$0, PanelMenuPresenterCallback -this1) {
            this();
        }

        private PanelMenuPresenterCallback() {
        }

        public void onCloseMenu(MenuBuilder menu, boolean allMenusAreClosing) {
            MenuBuilder parentMenu = menu.getRootMenu();
            boolean isSubMenu = parentMenu != menu;
            PhoneWindow phoneWindow = PhoneWindow.this;
            if (isSubMenu) {
                menu = parentMenu;
            }
            PanelFeatureState panel = phoneWindow.findMenuPanel(menu);
            if (panel == null) {
                return;
            }
            if (isSubMenu) {
                PhoneWindow.this.callOnPanelClosed(panel.featureId, panel, parentMenu);
                PhoneWindow.this.closePanel(panel, true);
                return;
            }
            PhoneWindow.this.closePanel(panel, allMenusAreClosing);
        }

        public boolean onOpenSubMenu(MenuBuilder subMenu) {
            if (subMenu == null && PhoneWindow.this.hasFeature(8)) {
                Window.Callback cb = PhoneWindow.this.getCallback();
                if (!(cb == null || (PhoneWindow.this.isDestroyed() ^ 1) == 0)) {
                    cb.onMenuOpened(8, subMenu);
                }
            }
            return true;
        }
    }

    public static final class PhoneWindowMenuCallback implements Callback, MenuPresenter.Callback {
        private static final int FEATURE_ID = 6;
        private boolean mShowDialogForSubmenu;
        private MenuDialogHelper mSubMenuHelper;
        private final PhoneWindow mWindow;

        public PhoneWindowMenuCallback(PhoneWindow window) {
            this.mWindow = window;
        }

        public void onCloseMenu(MenuBuilder menu, boolean allMenusAreClosing) {
            if (menu.getRootMenu() != menu) {
                onCloseSubMenu(menu);
            }
            if (allMenusAreClosing) {
                Window.Callback callback = this.mWindow.getCallback();
                if (!(callback == null || (this.mWindow.isDestroyed() ^ 1) == 0)) {
                    callback.onPanelClosed(6, menu);
                }
                if (menu == this.mWindow.mContextMenu) {
                    this.mWindow.dismissContextMenu();
                }
                if (this.mSubMenuHelper != null) {
                    this.mSubMenuHelper.dismiss();
                    this.mSubMenuHelper = null;
                }
            }
        }

        private void onCloseSubMenu(MenuBuilder menu) {
            Window.Callback callback = this.mWindow.getCallback();
            if (callback != null && (this.mWindow.isDestroyed() ^ 1) != 0) {
                callback.onPanelClosed(6, menu.getRootMenu());
            }
        }

        public boolean onMenuItemSelected(MenuBuilder menu, MenuItem item) {
            Window.Callback callback = this.mWindow.getCallback();
            if (callback == null || (this.mWindow.isDestroyed() ^ 1) == 0) {
                return false;
            }
            return callback.onMenuItemSelected(6, item);
        }

        public void onMenuModeChange(MenuBuilder menu) {
        }

        public boolean onOpenSubMenu(MenuBuilder subMenu) {
            if (subMenu == null) {
                return false;
            }
            subMenu.setCallback(this);
            if (!this.mShowDialogForSubmenu) {
                return false;
            }
            this.mSubMenuHelper = new MenuDialogHelper(subMenu);
            this.mSubMenuHelper.show(null);
            return true;
        }

        public void setShowDialogForSubmenu(boolean enabled) {
            this.mShowDialogForSubmenu = enabled;
        }
    }

    static class RotationWatcher extends Stub {
        private Handler mHandler;
        private boolean mIsWatching;
        private final Runnable mRotationChanged = new Runnable() {
            public void run() {
                RotationWatcher.this.dispatchRotationChanged();
            }
        };
        private final ArrayList<WeakReference<PhoneWindow>> mWindows = new ArrayList();

        RotationWatcher() {
        }

        public void onRotationChanged(int rotation) throws RemoteException {
            this.mHandler.post(this.mRotationChanged);
        }

        public void addWindow(PhoneWindow phoneWindow) {
            synchronized (this.mWindows) {
                if (!this.mIsWatching) {
                    try {
                        WindowManagerHolder.sWindowManager.watchRotation(this, phoneWindow.getContext().getDisplay().getDisplayId());
                        this.mHandler = new Handler();
                        this.mIsWatching = true;
                    } catch (RemoteException ex) {
                        Log.e(PhoneWindow.TAG, "Couldn't start watching for device rotation", ex);
                    }
                }
                this.mWindows.add(new WeakReference(phoneWindow));
            }
            return;
        }

        public void removeWindow(PhoneWindow phoneWindow) {
            synchronized (this.mWindows) {
                int i = 0;
                while (i < this.mWindows.size()) {
                    PhoneWindow win = (PhoneWindow) ((WeakReference) this.mWindows.get(i)).get();
                    if (win == null || win == phoneWindow) {
                        this.mWindows.remove(i);
                    } else {
                        i++;
                    }
                }
            }
        }

        void dispatchRotationChanged() {
            synchronized (this.mWindows) {
                int i = 0;
                while (i < this.mWindows.size()) {
                    PhoneWindow win = (PhoneWindow) ((WeakReference) this.mWindows.get(i)).get();
                    if (win != null) {
                        win.onOptionsPanelRotationChanged();
                        i++;
                    } else {
                        this.mWindows.remove(i);
                    }
                }
            }
        }
    }

    static class WindowManagerHolder {
        static final IWindowManager sWindowManager = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));

        WindowManagerHolder() {
        }
    }

    public PhoneWindow(Context context) {
        super(context);
        this.DEBUG_SYSTEMUI = SystemProperties.getBoolean("persist.systemuilog.debug", false);
        this.mContextMenuCallback = new PhoneWindowMenuCallback(this);
        this.mMinWidthMajor = new TypedValue();
        this.mMinWidthMinor = new TypedValue();
        this.mForceDecorInstall = false;
        this.mContentParentExplicitlySet = false;
        this.mRotation = 0;
        this.mBackgroundResource = 0;
        this.mBackgroundFallbackResource = 0;
        this.mLoadElevation = true;
        this.mFrameResource = 0;
        this.mTextColor = 0;
        this.mStatusBarColor = 0;
        this.mNavigationBarColor = 0;
        this.mNavigationBarDividerColor = 0;
        this.mForcedStatusBarColor = false;
        this.mForcedNavigationBarColor = false;
        this.mForcedStatusBarColorInTheme = false;
        this.mForcedNavigationBarColorInTheme = false;
        this.mKeepFullScreen = 0;
        this.mInternalFlag = 0;
        this.mEnableHomeIndicatorCover = true;
        this.mEnableFringeCover = true;
        this.mForcedCoverColor = false;
        this.mForcedHomeIndicatorCoverColor = false;
        this.mHomeIndicatorCoverColor = DEFAULT_LIGHT_COVER_COLOR;
        this.mFringeCoverColor = -16777216;
        this.mHomeIndicatorState = -1;
        this.mHomeIndicatorStateUserConfig = "";
        this.mKeepFullListConfig = "";
        this.mHomeIndicatorCoverColorUserConfig = "";
        this.mFringeCoverColorUserConfig = "";
        this.mHomeIndicatorStateListConfig = "";
        this.mComponentName = null;
        this.mHomeIndicatorOn = false;
        this.mTitle = null;
        this.mTitleColor = 0;
        this.mAlwaysReadCloseOnTouchAttr = false;
        this.mVolumeControlStreamType = Integer.MIN_VALUE;
        this.mUiOptions = 0;
        this.mInvalidatePanelMenuRunnable = new Runnable() {
            public void run() {
                for (int i = 0; i <= 13; i++) {
                    if ((PhoneWindow.this.mInvalidatePanelMenuFeatures & (1 << i)) != 0) {
                        PhoneWindow.this.doInvalidatePanelMenu(i);
                    }
                }
                PhoneWindow.this.mInvalidatePanelMenuPosted = false;
                PhoneWindow.this.mInvalidatePanelMenuFeatures = 0;
            }
        };
        this.mEnterTransition = null;
        this.mReturnTransition = USE_DEFAULT_TRANSITION;
        this.mExitTransition = null;
        this.mReenterTransition = USE_DEFAULT_TRANSITION;
        this.mSharedElementEnterTransition = null;
        this.mSharedElementReturnTransition = USE_DEFAULT_TRANSITION;
        this.mSharedElementExitTransition = null;
        this.mSharedElementReenterTransition = USE_DEFAULT_TRANSITION;
        this.mBackgroundFadeDurationMillis = -1;
        this.mTheme = -1;
        this.mDecorCaptionShade = 0;
        this.mUseDecorContext = false;
        this.mClsName = null;
        this.mFreeformAlpha = 0.8f;
        this.mLogTag = TAG;
        this.mNavColorConfig = "default";
        this.mSBColorConfig = "default";
        this.mSystemApp = false;
        this.mIsVivoApp = false;
        this.mConfigInited = false;
        this.mOtherGMSApp = new ArrayList<String>() {
            {
                add("com.android.vending");
                add("com.android.chrome");
            }
        };
        this.mGMSAppPrefix = "com.google.android";
        initVivoWindowPolicy();
        this.mLayoutInflater = LayoutInflater.from(context);
    }

    public PhoneWindow(Context context, Window preservedWindow, ActivityConfigCallback activityConfigCallback, ComponentName componentName) {
        boolean z;
        this(context);
        if (componentName != null) {
            this.mComponentName = componentName.clone();
        }
        initVivoWindowPolicy();
        this.mUseDecorContext = true;
        if (preservedWindow != null) {
            this.mDecor = (DecorView) preservedWindow.getDecorView();
            this.mElevation = preservedWindow.getElevation();
            this.mLoadElevation = false;
            this.mForceDecorInstall = true;
            getAttributes().token = preservedWindow.getAttributes().token;
        }
        if (Global.getInt(context.getContentResolver(), Global.DEVELOPMENT_FORCE_RESIZABLE_ACTIVITIES, 0) != 0) {
            z = true;
        } else {
            z = context.getPackageManager().hasSystemFeature("android.software.picture_in_picture");
        }
        this.mSupportsPictureInPicture = z;
        this.mActivityConfigCallback = activityConfigCallback;
    }

    public PhoneWindow(Context context, Window preservedWindow, ActivityConfigCallback activityConfigCallback) {
        boolean z;
        this(context);
        this.mUseDecorContext = true;
        if (preservedWindow != null) {
            this.mDecor = (DecorView) preservedWindow.getDecorView();
            this.mElevation = preservedWindow.getElevation();
            this.mLoadElevation = false;
            this.mForceDecorInstall = true;
            getAttributes().token = preservedWindow.getAttributes().token;
        }
        if (Global.getInt(context.getContentResolver(), Global.DEVELOPMENT_FORCE_RESIZABLE_ACTIVITIES, 0) != 0) {
            z = true;
        } else {
            z = context.getPackageManager().hasSystemFeature("android.software.picture_in_picture");
        }
        this.mSupportsPictureInPicture = z;
        this.mActivityConfigCallback = activityConfigCallback;
    }

    public final void setContainer(Window container) {
        super.setContainer(container);
    }

    public boolean requestFeature(int featureId) {
        if (this.mContentParentExplicitlySet) {
            throw new AndroidRuntimeException("requestFeature() must be called before adding content");
        }
        int features = getFeatures();
        int newFeatures = features | (1 << featureId);
        if ((newFeatures & 128) != 0 && (newFeatures & -13506) != 0) {
            throw new AndroidRuntimeException("You cannot combine custom titles with other title features");
        } else if ((features & 2) != 0 && featureId == 8) {
            return false;
        } else {
            if ((features & 256) != 0 && featureId == 1) {
                removeFeature(8);
            }
            if ((features & 256) != 0 && featureId == 11) {
                throw new AndroidRuntimeException("You cannot combine swipe dismissal and the action bar.");
            } else if ((features & 2048) != 0 && featureId == 8) {
                throw new AndroidRuntimeException("You cannot combine swipe dismissal and the action bar.");
            } else if (featureId != 5 || !getContext().getPackageManager().hasSystemFeature("android.hardware.type.watch")) {
                return super.requestFeature(featureId);
            } else {
                throw new AndroidRuntimeException("You cannot use indeterminate progress on a watch.");
            }
        }
    }

    public void setUiOptions(int uiOptions) {
        this.mUiOptions = uiOptions;
    }

    public void setUiOptions(int uiOptions, int mask) {
        this.mUiOptions = (this.mUiOptions & (~mask)) | (uiOptions & mask);
    }

    public TransitionManager getTransitionManager() {
        return this.mTransitionManager;
    }

    public void setTransitionManager(TransitionManager tm) {
        this.mTransitionManager = tm;
    }

    public Scene getContentScene() {
        return this.mContentScene;
    }

    public void setContentView(int layoutResID) {
        if (this.mContentParent == null) {
            installDecor();
        } else if (!hasFeature(12)) {
            this.mContentParent.removeAllViews();
        }
        if (hasFeature(12)) {
            transitionTo(Scene.getSceneForLayout(this.mContentParent, layoutResID, getContext()));
        } else {
            this.mLayoutInflater.inflate(layoutResID, this.mContentParent);
        }
        this.mContentParent.requestApplyInsets();
        Window.Callback cb = getCallback();
        if (!(cb == null || (isDestroyed() ^ 1) == 0)) {
            cb.onContentChanged();
        }
        this.mContentParentExplicitlySet = true;
    }

    public void setContentView(View view) {
        setContentView(view, new LayoutParams(-1, -1));
    }

    public void setContentView(View view, LayoutParams params) {
        if (this.mContentParent == null) {
            installDecor();
        } else if (!hasFeature(12)) {
            this.mContentParent.removeAllViews();
        }
        if (hasFeature(12)) {
            view.setLayoutParams(params);
            transitionTo(new Scene(this.mContentParent, view));
        } else {
            this.mContentParent.addView(view, params);
        }
        this.mContentParent.requestApplyInsets();
        Window.Callback cb = getCallback();
        if (!(cb == null || (isDestroyed() ^ 1) == 0)) {
            cb.onContentChanged();
        }
        this.mContentParentExplicitlySet = true;
    }

    public void addContentView(View view, LayoutParams params) {
        if (this.mContentParent == null) {
            installDecor();
        }
        if (hasFeature(12)) {
            Log.v(TAG, "addContentView does not support content transitions");
        }
        this.mContentParent.addView(view, params);
        this.mContentParent.requestApplyInsets();
        Window.Callback cb = getCallback();
        if (cb != null && (isDestroyed() ^ 1) != 0) {
            cb.onContentChanged();
        }
    }

    public void clearContentView() {
        if (this.mDecor != null) {
            this.mDecor.clearContentView();
        }
    }

    private void transitionTo(Scene scene) {
        if (this.mContentScene == null) {
            scene.enter();
        } else {
            this.mTransitionManager.transitionTo(scene);
        }
        this.mContentScene = scene;
    }

    public View getCurrentFocus() {
        return this.mDecor != null ? this.mDecor.findFocus() : null;
    }

    public void takeSurface(Callback2 callback) {
        this.mTakeSurfaceCallback = callback;
    }

    public void takeInputQueue(InputQueue.Callback callback) {
        this.mTakeInputQueueCallback = callback;
    }

    public boolean isFloating() {
        return this.mIsFloating;
    }

    public boolean isTranslucent() {
        return this.mIsTranslucent;
    }

    boolean isShowingWallpaper() {
        return (getAttributes().flags & 1048576) != 0;
    }

    public LayoutInflater getLayoutInflater() {
        return this.mLayoutInflater;
    }

    public void setTitle(CharSequence title) {
        setTitle(title, true);
    }

    public void setTitle(CharSequence title, boolean updateAccessibilityTitle) {
        if (this.mTitleView != null) {
            this.mTitleView.setText(title);
        } else if (this.mDecorContentParent != null) {
            this.mDecorContentParent.setWindowTitle(title);
        }
        this.mTitle = title;
        if (updateAccessibilityTitle) {
            WindowManager.LayoutParams params = getAttributes();
            if (!TextUtils.equals(title, params.accessibilityTitle)) {
                params.accessibilityTitle = TextUtils.stringOrSpannedString(title);
                if (this.mDecor != null) {
                    ViewRootImpl vr = this.mDecor.getViewRootImpl();
                    if (vr != null) {
                        vr.onWindowTitleChanged();
                    }
                }
                dispatchWindowAttributesChanged(getAttributes());
            }
        }
    }

    @Deprecated
    public void setTitleColor(int textColor) {
        if (this.mTitleView != null) {
            this.mTitleView.setTextColor(textColor);
        }
        this.mTitleColor = textColor;
    }

    public final boolean preparePanel(PanelFeatureState st, KeyEvent event) {
        if (isDestroyed()) {
            return false;
        }
        if (st.isPrepared) {
            return true;
        }
        if (!(this.mPreparedPanel == null || this.mPreparedPanel == st)) {
            closePanel(this.mPreparedPanel, false);
        }
        Window.Callback cb = getCallback();
        if (cb != null) {
            st.createdPanelView = cb.onCreatePanelView(st.featureId);
        }
        boolean isActionBarMenu = st.featureId == 0 || st.featureId == 8;
        if (isActionBarMenu && this.mDecorContentParent != null) {
            this.mDecorContentParent.setMenuPrepared();
        }
        if (st.createdPanelView == null) {
            if (st.menu == null || st.refreshMenuContent) {
                if (st.menu == null && (!initializePanelMenu(st) || st.menu == null)) {
                    return false;
                }
                if (isActionBarMenu && this.mDecorContentParent != null) {
                    if (this.mActionMenuPresenterCallback == null) {
                        this.mActionMenuPresenterCallback = new ActionMenuPresenterCallback(this, null);
                    }
                    this.mDecorContentParent.setMenu(st.menu, this.mActionMenuPresenterCallback);
                }
                st.menu.stopDispatchingItemsChanged();
                if (cb == null || (cb.onCreatePanelMenu(st.featureId, st.menu) ^ 1) != 0) {
                    st.setMenu(null);
                    if (isActionBarMenu && this.mDecorContentParent != null) {
                        this.mDecorContentParent.setMenu(null, this.mActionMenuPresenterCallback);
                    }
                    return false;
                }
                st.refreshMenuContent = false;
            }
            st.menu.stopDispatchingItemsChanged();
            if (st.frozenActionViewState != null) {
                st.menu.restoreActionViewStates(st.frozenActionViewState);
                st.frozenActionViewState = null;
            }
            if (cb.onPreparePanel(st.featureId, st.createdPanelView, st.menu)) {
                boolean z;
                if (KeyCharacterMap.load(event != null ? event.getDeviceId() : -1).getKeyboardType() != 1) {
                    z = true;
                } else {
                    z = false;
                }
                st.qwertyMode = z;
                st.menu.setQwertyMode(st.qwertyMode);
                st.menu.startDispatchingItemsChanged();
            } else {
                if (isActionBarMenu && this.mDecorContentParent != null) {
                    this.mDecorContentParent.setMenu(null, this.mActionMenuPresenterCallback);
                }
                st.menu.startDispatchingItemsChanged();
                return false;
            }
        }
        st.isPrepared = true;
        st.isHandled = false;
        this.mPreparedPanel = st;
        return true;
    }

    public void onConfigurationChanged(Configuration newConfig) {
        Slog.d(TAG, "DEBUG_ALIENSCREEN:onConfigurationChanged newConfig=" + newConfig);
        if (this.mDecorContentParent == null) {
            PanelFeatureState st = getPanelState(0, false);
            if (!(st == null || st.menu == null)) {
                if (st.isOpen) {
                    Bundle state = new Bundle();
                    if (st.iconMenuPresenter != null) {
                        st.iconMenuPresenter.saveHierarchyState(state);
                    }
                    if (st.listMenuPresenter != null) {
                        st.listMenuPresenter.saveHierarchyState(state);
                    }
                    clearMenuViews(st);
                    reopenMenu(false);
                    if (st.iconMenuPresenter != null) {
                        st.iconMenuPresenter.restoreHierarchyState(state);
                    }
                    if (st.listMenuPresenter != null) {
                        st.listMenuPresenter.restoreHierarchyState(state);
                    }
                } else {
                    clearMenuViews(st);
                }
            }
        }
        getRotation();
    }

    private void getRotation() {
        try {
            WindowManagerGlobal.getInstance();
            int rotation = WindowManagerGlobal.getWindowManagerService().getDefaultDisplayRotation();
            if (this.mRotation != rotation) {
                this.mRotation = rotation;
                if (this.mDecor != null) {
                    if (this.DEBUG_SYSTEMUI) {
                        Slog.d(TAG, "DEBUG_SYSTEMUI:setStatusBarColor:" + Integer.toHexString(this.mStatusBarColor) + " updateView");
                    }
                    this.mDecor.updateColorViews(null, false);
                }
            }
            Slog.d(TAG, "DEBUG_ALIENSCREEN:getRotation mRotation=" + this.mRotation);
        } catch (Exception e) {
        }
    }

    public void onMultiWindowModeChanged() {
        if (this.mDecor != null) {
            this.mDecor.onConfigurationChanged(getContext().getResources().getConfiguration());
        }
    }

    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        if (this.mDecor != null) {
            this.mDecor.updatePictureInPictureOutlineProvider(isInPictureInPictureMode);
        }
    }

    public void reportActivityRelaunched() {
        if (this.mDecor != null && this.mDecor.getViewRootImpl() != null) {
            this.mDecor.getViewRootImpl().reportActivityRelaunched();
        }
    }

    private static void clearMenuViews(PanelFeatureState st) {
        st.createdPanelView = null;
        st.refreshDecorView = true;
        st.clearMenuPresenters();
    }

    public final void openPanel(int featureId, KeyEvent event) {
        if (featureId != 0 || this.mDecorContentParent == null || !this.mDecorContentParent.canShowOverflowMenu() || (ViewConfiguration.get(getContext()).hasPermanentMenuKey() ^ 1) == 0) {
            openPanel(getPanelState(featureId, true), event);
        } else {
            this.mDecorContentParent.showOverflowMenu();
        }
    }

    private void openPanel(PanelFeatureState st, KeyEvent event) {
        if (!st.isOpen && !isDestroyed()) {
            if (st.featureId == 0) {
                Context context = getContext();
                boolean isXLarge = (context.getResources().getConfiguration().screenLayout & 15) == 4;
                boolean isHoneycombApp = context.getApplicationInfo().targetSdkVersion >= 11;
                if (isXLarge && isHoneycombApp) {
                    return;
                }
            }
            Window.Callback cb = getCallback();
            if (cb == null || (cb.onMenuOpened(st.featureId, st.menu) ^ 1) == 0) {
                WindowManager wm = getWindowManager();
                if (wm == null || !preparePanel(st, event)) {
                    return;
                }
                if (st.menu == null || st.menu.getNonActionItems().size() > 0) {
                    int width = -2;
                    LayoutParams lp;
                    if (st.decorView == null || st.refreshDecorView) {
                        if (st.decorView == null) {
                            if (!initializePanelDecor(st) || st.decorView == null) {
                                return;
                            }
                        } else if (st.refreshDecorView && st.decorView.getChildCount() > 0) {
                            st.decorView.removeAllViews();
                        }
                        if (initializePanelContent(st) && (st.hasPanelItems() ^ 1) == 0) {
                            int backgroundResId;
                            lp = st.shownPanelView.getLayoutParams();
                            if (lp == null) {
                                LayoutParams layoutParams = new LayoutParams(-2, -2);
                            }
                            if (lp.width == -1) {
                                backgroundResId = st.fullBackground;
                                width = -1;
                            } else {
                                backgroundResId = st.background;
                            }
                            st.decorView.setWindowBackground(getContext().getDrawable(backgroundResId));
                            ViewParent shownPanelParent = st.shownPanelView.getParent();
                            if (shownPanelParent != null && (shownPanelParent instanceof ViewGroup)) {
                                ((ViewGroup) shownPanelParent).removeView(st.shownPanelView);
                            }
                            st.decorView.addView(st.shownPanelView, lp);
                            if (!st.shownPanelView.hasFocus()) {
                                st.shownPanelView.requestFocus();
                            }
                        } else {
                            return;
                        }
                    } else if (!st.isInListMode()) {
                        width = -1;
                    } else if (st.createdPanelView != null) {
                        lp = st.createdPanelView.getLayoutParams();
                        if (lp != null && lp.width == -1) {
                            width = -1;
                        }
                    }
                    st.isHandled = false;
                    WindowManager.LayoutParams lp2 = new WindowManager.LayoutParams(width, -2, st.x, st.y, 1003, 8519680, st.decorView.mDefaultOpacity);
                    if (st.isCompact) {
                        lp2.gravity = getOptionsPanelGravity();
                        sRotationWatcher.addWindow(this);
                    } else {
                        lp2.gravity = st.gravity;
                    }
                    lp2.windowAnimations = st.windowAnimations;
                    wm.addView(st.decorView, lp2);
                    st.isOpen = true;
                    return;
                }
                st.isPrepared = false;
                return;
            }
            closePanel(st, true);
        }
    }

    public final void closePanel(int featureId) {
        if (featureId == 0 && this.mDecorContentParent != null && this.mDecorContentParent.canShowOverflowMenu() && (ViewConfiguration.get(getContext()).hasPermanentMenuKey() ^ 1) != 0) {
            this.mDecorContentParent.hideOverflowMenu();
        } else if (featureId == 6) {
            closeContextMenu();
        } else {
            closePanel(getPanelState(featureId, true), true);
        }
    }

    public final void closePanel(PanelFeatureState st, boolean doCallback) {
        if (doCallback && st.featureId == 0 && this.mDecorContentParent != null && this.mDecorContentParent.isOverflowMenuShowing()) {
            checkCloseActionMenu(st.menu);
            return;
        }
        ViewManager wm = getWindowManager();
        if (wm != null && st.isOpen) {
            if (st.decorView != null) {
                wm.removeView(st.decorView);
                if (st.isCompact) {
                    sRotationWatcher.removeWindow(this);
                }
            }
            if (doCallback) {
                callOnPanelClosed(st.featureId, st, null);
            }
        }
        st.isPrepared = false;
        st.isHandled = false;
        st.isOpen = false;
        st.shownPanelView = null;
        if (st.isInExpandedMode) {
            st.refreshDecorView = true;
            st.isInExpandedMode = false;
        }
        if (this.mPreparedPanel == st) {
            this.mPreparedPanel = null;
            this.mPanelChordingKey = 0;
        }
    }

    void checkCloseActionMenu(Menu menu) {
        if (!this.mClosingActionMenu) {
            this.mClosingActionMenu = true;
            this.mDecorContentParent.dismissPopups();
            Window.Callback cb = getCallback();
            if (!(cb == null || (isDestroyed() ^ 1) == 0)) {
                cb.onPanelClosed(8, menu);
            }
            this.mClosingActionMenu = false;
        }
    }

    public final void togglePanel(int featureId, KeyEvent event) {
        PanelFeatureState st = getPanelState(featureId, true);
        if (st.isOpen) {
            closePanel(st, true);
        } else {
            openPanel(st, event);
        }
    }

    public void invalidatePanelMenu(int featureId) {
        this.mInvalidatePanelMenuFeatures |= 1 << featureId;
        if (!this.mInvalidatePanelMenuPosted && this.mDecor != null) {
            this.mDecor.postOnAnimation(this.mInvalidatePanelMenuRunnable);
            this.mInvalidatePanelMenuPosted = true;
        }
    }

    void doPendingInvalidatePanelMenu() {
        if (this.mInvalidatePanelMenuPosted) {
            this.mDecor.removeCallbacks(this.mInvalidatePanelMenuRunnable);
            this.mInvalidatePanelMenuRunnable.run();
        }
    }

    void doInvalidatePanelMenu(int featureId) {
        PanelFeatureState st = getPanelState(featureId, false);
        if (st != null) {
            if (st.menu != null) {
                Bundle savedActionViewStates = new Bundle();
                st.menu.saveActionViewStates(savedActionViewStates);
                if (savedActionViewStates.size() > 0) {
                    st.frozenActionViewState = savedActionViewStates;
                }
                st.menu.stopDispatchingItemsChanged();
                st.menu.clear();
            }
            st.refreshMenuContent = true;
            st.refreshDecorView = true;
            if ((featureId == 8 || featureId == 0) && this.mDecorContentParent != null) {
                st = getPanelState(0, false);
                if (st != null) {
                    st.isPrepared = false;
                    preparePanel(st, null);
                }
            }
        }
    }

    public final boolean onKeyDownPanel(int featureId, KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (event.getRepeatCount() == 0) {
            this.mPanelChordingKey = keyCode;
            PanelFeatureState st = getPanelState(featureId, false);
            if (!(st == null || (st.isOpen ^ 1) == 0)) {
                return preparePanel(st, event);
            }
        }
        return false;
    }

    public final void onKeyUpPanel(int featureId, KeyEvent event) {
        if (this.mPanelChordingKey != 0) {
            this.mPanelChordingKey = 0;
            PanelFeatureState st = getPanelState(featureId, false);
            if (!event.isCanceled() && ((this.mDecor == null || this.mDecor.mPrimaryActionMode == null) && st != null)) {
                boolean playSoundEffect = false;
                if (featureId != 0 || this.mDecorContentParent == null || !this.mDecorContentParent.canShowOverflowMenu() || (ViewConfiguration.get(getContext()).hasPermanentMenuKey() ^ 1) == 0) {
                    if (st.isOpen || st.isHandled) {
                        playSoundEffect = st.isOpen;
                        closePanel(st, true);
                    } else if (st.isPrepared) {
                        boolean show = true;
                        if (st.refreshMenuContent) {
                            st.isPrepared = false;
                            show = preparePanel(st, event);
                        }
                        if (show) {
                            EventLog.writeEvent(50001, 0);
                            openPanel(st, event);
                            playSoundEffect = true;
                        }
                    }
                } else if (this.mDecorContentParent.isOverflowMenuShowing()) {
                    playSoundEffect = this.mDecorContentParent.hideOverflowMenu();
                } else if (!isDestroyed() && preparePanel(st, event)) {
                    playSoundEffect = this.mDecorContentParent.showOverflowMenu();
                }
                if (playSoundEffect) {
                    AudioManager audioManager = (AudioManager) getContext().getSystemService("audio");
                    if (audioManager != null) {
                        audioManager.playSoundEffect(0);
                    } else {
                        Log.w(TAG, "Couldn't get audio manager");
                    }
                }
            }
        }
    }

    public final void closeAllPanels() {
        if (getWindowManager() != null) {
            PanelFeatureState[] panels = this.mPanels;
            int N = panels != null ? panels.length : 0;
            for (int i = 0; i < N; i++) {
                PanelFeatureState panel = panels[i];
                if (panel != null) {
                    closePanel(panel, true);
                }
            }
            closeContextMenu();
        }
    }

    private synchronized void closeContextMenu() {
        if (this.mContextMenu != null) {
            this.mContextMenu.close();
            dismissContextMenu();
        }
    }

    private synchronized void dismissContextMenu() {
        this.mContextMenu = null;
        if (this.mContextMenuHelper != null) {
            this.mContextMenuHelper.dismiss();
            this.mContextMenuHelper = null;
        }
    }

    public boolean performPanelShortcut(int featureId, int keyCode, KeyEvent event, int flags) {
        return performPanelShortcut(getPanelState(featureId, false), keyCode, event, flags);
    }

    boolean performPanelShortcut(PanelFeatureState st, int keyCode, KeyEvent event, int flags) {
        if (event.isSystem() || st == null) {
            return false;
        }
        boolean handled = false;
        if ((st.isPrepared || preparePanel(st, event)) && st.menu != null) {
            handled = st.menu.performShortcut(keyCode, event, flags);
        }
        if (handled) {
            st.isHandled = true;
            if ((flags & 1) == 0 && this.mDecorContentParent == null) {
                closePanel(st, true);
            }
        }
        return handled;
    }

    public boolean performPanelIdentifierAction(int featureId, int id, int flags) {
        PanelFeatureState st = getPanelState(featureId, true);
        if (!preparePanel(st, new KeyEvent(0, 82)) || st.menu == null) {
            return false;
        }
        boolean res = st.menu.performIdentifierAction(id, flags);
        if (this.mDecorContentParent == null) {
            closePanel(st, true);
        }
        return res;
    }

    public PanelFeatureState findMenuPanel(Menu menu) {
        PanelFeatureState[] panels = this.mPanels;
        int N = panels != null ? panels.length : 0;
        for (int i = 0; i < N; i++) {
            PanelFeatureState panel = panels[i];
            if (panel != null && panel.menu == menu) {
                return panel;
            }
        }
        return null;
    }

    public boolean onMenuItemSelected(MenuBuilder menu, MenuItem item) {
        Window.Callback cb = getCallback();
        if (!(cb == null || (isDestroyed() ^ 1) == 0)) {
            PanelFeatureState panel = findMenuPanel(menu.getRootMenu());
            if (panel != null) {
                return cb.onMenuItemSelected(panel.featureId, item);
            }
        }
        return false;
    }

    public void onMenuModeChange(MenuBuilder menu) {
        reopenMenu(true);
    }

    private void reopenMenu(boolean toggleMenuMode) {
        PanelFeatureState st;
        if (this.mDecorContentParent == null || !this.mDecorContentParent.canShowOverflowMenu() || (ViewConfiguration.get(getContext()).hasPermanentMenuKey() && !this.mDecorContentParent.isOverflowMenuShowPending())) {
            st = getPanelState(0, false);
            if (st != null) {
                boolean newExpandedMode = toggleMenuMode ? st.isInExpandedMode ^ 1 : st.isInExpandedMode;
                st.refreshDecorView = true;
                closePanel(st, false);
                st.isInExpandedMode = newExpandedMode;
                openPanel(st, null);
                return;
            }
            return;
        }
        Window.Callback cb = getCallback();
        if (this.mDecorContentParent.isOverflowMenuShowing() && (toggleMenuMode ^ 1) == 0) {
            this.mDecorContentParent.hideOverflowMenu();
            st = getPanelState(0, false);
            if (!(st == null || cb == null || (isDestroyed() ^ 1) == 0)) {
                cb.onPanelClosed(8, st.menu);
            }
        } else if (!(cb == null || (isDestroyed() ^ 1) == 0)) {
            if (this.mInvalidatePanelMenuPosted && (this.mInvalidatePanelMenuFeatures & 1) != 0) {
                this.mDecor.removeCallbacks(this.mInvalidatePanelMenuRunnable);
                this.mInvalidatePanelMenuRunnable.run();
            }
            st = getPanelState(0, false);
            if (!(st == null || st.menu == null || (st.refreshMenuContent ^ 1) == 0 || !cb.onPreparePanel(0, st.createdPanelView, st.menu))) {
                cb.onMenuOpened(8, st.menu);
                this.mDecorContentParent.showOverflowMenu();
            }
        }
    }

    protected boolean initializePanelMenu(PanelFeatureState st) {
        Context context = getContext();
        if ((st.featureId == 0 || st.featureId == 8) && this.mDecorContentParent != null) {
            TypedValue outValue = new TypedValue();
            Theme baseTheme = context.getTheme();
            baseTheme.resolveAttribute(R.attr.actionBarTheme, outValue, true);
            Theme widgetTheme = null;
            if (outValue.resourceId != 0) {
                widgetTheme = context.getResources().newTheme();
                widgetTheme.setTo(baseTheme);
                widgetTheme.applyStyle(outValue.resourceId, true);
                widgetTheme.resolveAttribute(R.attr.actionBarWidgetTheme, outValue, true);
            } else {
                baseTheme.resolveAttribute(R.attr.actionBarWidgetTheme, outValue, true);
            }
            if (outValue.resourceId != 0) {
                if (widgetTheme == null) {
                    widgetTheme = context.getResources().newTheme();
                    widgetTheme.setTo(baseTheme);
                }
                widgetTheme.applyStyle(outValue.resourceId, true);
            }
            if (widgetTheme != null) {
                Context context2 = new ContextThemeWrapper(context, 0);
                context2.getTheme().setTo(widgetTheme);
                context = context2;
            }
        }
        MenuBuilder menu = new MenuBuilder(context);
        menu.setCallback(this);
        st.setMenu(menu);
        return true;
    }

    protected boolean initializePanelDecor(PanelFeatureState st) {
        st.decorView = generateDecor(st.featureId);
        st.gravity = 81;
        st.setStyle(getContext());
        TypedArray a = getContext().obtainStyledAttributes(null, R.styleable.Window, 0, st.listPresenterTheme);
        float elevation = a.getDimension(38, 0.0f);
        if (elevation != 0.0f) {
            st.decorView.setElevation(elevation);
        }
        a.recycle();
        return true;
    }

    private int getOptionsPanelGravity() {
        try {
            return WindowManagerHolder.sWindowManager.getPreferredOptionsPanelGravity();
        } catch (RemoteException ex) {
            Log.e(TAG, "Couldn't getOptionsPanelGravity; using default", ex);
            return 81;
        }
    }

    void onOptionsPanelRotationChanged() {
        PanelFeatureState st = getPanelState(0, false);
        if (st != null) {
            LayoutParams lp = st.decorView != null ? (WindowManager.LayoutParams) st.decorView.getLayoutParams() : null;
            if (lp != null) {
                lp.gravity = getOptionsPanelGravity();
                ViewManager wm = getWindowManager();
                if (wm != null) {
                    wm.updateViewLayout(st.decorView, lp);
                }
            }
        }
    }

    protected boolean initializePanelContent(PanelFeatureState st) {
        if (st.createdPanelView != null) {
            st.shownPanelView = st.createdPanelView;
            return true;
        } else if (st.menu == null) {
            return false;
        } else {
            MenuView menuView;
            if (this.mPanelMenuPresenterCallback == null) {
                this.mPanelMenuPresenterCallback = new PanelMenuPresenterCallback(this, null);
            }
            if (st.isInListMode()) {
                menuView = st.getListMenuView(getContext(), this.mPanelMenuPresenterCallback);
            } else {
                menuView = st.getIconMenuView(getContext(), this.mPanelMenuPresenterCallback);
            }
            st.shownPanelView = (View) menuView;
            if (st.shownPanelView == null) {
                return false;
            }
            int defaultAnimations = menuView.getWindowAnimations();
            if (defaultAnimations != 0) {
                st.windowAnimations = defaultAnimations;
            }
            return true;
        }
    }

    public boolean performContextMenuIdentifierAction(int id, int flags) {
        return this.mContextMenu != null ? this.mContextMenu.performIdentifierAction(id, flags) : false;
    }

    public final void setElevation(float elevation) {
        this.mElevation = elevation;
        WindowManager.LayoutParams attrs = getAttributes();
        if (this.mDecor != null) {
            this.mDecor.setElevation(elevation);
            attrs.setSurfaceInsets(this.mDecor, true, false);
        }
        dispatchWindowAttributesChanged(attrs);
    }

    public float getElevation() {
        return this.mElevation;
    }

    public final void setClipToOutline(boolean clipToOutline) {
        this.mClipToOutline = clipToOutline;
        if (this.mDecor != null) {
            this.mDecor.setClipToOutline(clipToOutline);
        }
    }

    public final void setBackgroundDrawable(Drawable drawable) {
        int i = 0;
        if (drawable != this.mBackgroundDrawable || this.mBackgroundResource != 0) {
            this.mBackgroundResource = 0;
            this.mBackgroundDrawable = drawable;
            if (this.mDecor != null) {
                this.mDecor.setWindowBackground(drawable);
            }
            if (this.mBackgroundFallbackResource != 0) {
                DecorView decorView = this.mDecor;
                if (drawable == null) {
                    i = this.mBackgroundFallbackResource;
                }
                decorView.setBackgroundFallback(i);
            }
        }
    }

    public final void setFeatureDrawableResource(int featureId, int resId) {
        if (resId != 0) {
            DrawableFeatureState st = getDrawableState(featureId, true);
            if (st.resid != resId) {
                st.resid = resId;
                st.uri = null;
                st.local = getContext().getDrawable(resId);
                updateDrawable(featureId, st, false);
                return;
            }
            return;
        }
        setFeatureDrawable(featureId, null);
    }

    public final void setFeatureDrawableUri(int featureId, Uri uri) {
        if (uri != null) {
            DrawableFeatureState st = getDrawableState(featureId, true);
            if (st.uri == null || (st.uri.equals(uri) ^ 1) != 0) {
                st.resid = 0;
                st.uri = uri;
                st.local = loadImageURI(uri);
                updateDrawable(featureId, st, false);
                return;
            }
            return;
        }
        setFeatureDrawable(featureId, null);
    }

    public final void setFeatureDrawable(int featureId, Drawable drawable) {
        DrawableFeatureState st = getDrawableState(featureId, true);
        st.resid = 0;
        st.uri = null;
        if (st.local != drawable) {
            st.local = drawable;
            updateDrawable(featureId, st, false);
        }
    }

    public void setFeatureDrawableAlpha(int featureId, int alpha) {
        DrawableFeatureState st = getDrawableState(featureId, true);
        if (st.alpha != alpha) {
            st.alpha = alpha;
            updateDrawable(featureId, st, false);
        }
    }

    protected final void setFeatureDefaultDrawable(int featureId, Drawable drawable) {
        DrawableFeatureState st = getDrawableState(featureId, true);
        if (st.def != drawable) {
            st.def = drawable;
            updateDrawable(featureId, st, false);
        }
    }

    public final void setFeatureInt(int featureId, int value) {
        updateInt(featureId, value, false);
    }

    protected final void updateDrawable(int featureId, boolean fromActive) {
        DrawableFeatureState st = getDrawableState(featureId, false);
        if (st != null) {
            updateDrawable(featureId, st, fromActive);
        }
    }

    protected void onDrawableChanged(int featureId, Drawable drawable, int alpha) {
        ImageView view;
        if (featureId == 3) {
            view = getLeftIconView();
        } else if (featureId == 4) {
            view = getRightIconView();
        } else {
            return;
        }
        if (drawable != null) {
            drawable.setAlpha(alpha);
            view.setImageDrawable(drawable);
            view.setVisibility(0);
        } else {
            view.setVisibility(8);
        }
    }

    protected void onIntChanged(int featureId, int value) {
        if (featureId == 2 || featureId == 5) {
            updateProgressBars(value);
        } else if (featureId == 7) {
            ViewGroup titleContainer = (FrameLayout) findViewById(R.id.title_container);
            if (titleContainer != null) {
                this.mLayoutInflater.inflate(value, titleContainer);
            }
        }
    }

    private void updateProgressBars(int value) {
        ProgressBar circularProgressBar = getCircularProgressBar(true);
        ProgressBar horizontalProgressBar = getHorizontalProgressBar(true);
        int features = getLocalFeatures();
        if (value == -1) {
            if ((features & 4) != 0) {
                if (horizontalProgressBar != null) {
                    int visibility = (horizontalProgressBar.isIndeterminate() || horizontalProgressBar.getProgress() < 10000) ? 0 : 4;
                    horizontalProgressBar.setVisibility(visibility);
                } else {
                    Log.e(TAG, "Horizontal progress bar not located in current window decor");
                }
            }
            if ((features & 32) == 0) {
                return;
            }
            if (circularProgressBar != null) {
                circularProgressBar.setVisibility(0);
            } else {
                Log.e(TAG, "Circular progress bar not located in current window decor");
            }
        } else if (value == -2) {
            if ((features & 4) != 0) {
                if (horizontalProgressBar != null) {
                    horizontalProgressBar.setVisibility(8);
                } else {
                    Log.e(TAG, "Horizontal progress bar not located in current window decor");
                }
            }
            if ((features & 32) == 0) {
                return;
            }
            if (circularProgressBar != null) {
                circularProgressBar.setVisibility(8);
            } else {
                Log.e(TAG, "Circular progress bar not located in current window decor");
            }
        } else if (value == -3) {
            if (horizontalProgressBar != null) {
                horizontalProgressBar.setIndeterminate(true);
            } else {
                Log.e(TAG, "Horizontal progress bar not located in current window decor");
            }
        } else if (value == -4) {
            if (horizontalProgressBar != null) {
                horizontalProgressBar.setIndeterminate(false);
            } else {
                Log.e(TAG, "Horizontal progress bar not located in current window decor");
            }
        } else if (value >= 0 && value <= 10000) {
            if (horizontalProgressBar != null) {
                horizontalProgressBar.setProgress(value + 0);
            } else {
                Log.e(TAG, "Horizontal progress bar not located in current window decor");
            }
            if (value < 10000) {
                showProgressBars(horizontalProgressBar, circularProgressBar);
            } else {
                hideProgressBars(horizontalProgressBar, circularProgressBar);
            }
        } else if (Window.PROGRESS_SECONDARY_START <= value && value <= 30000) {
            if (horizontalProgressBar != null) {
                horizontalProgressBar.setSecondaryProgress(value - 20000);
            } else {
                Log.e(TAG, "Horizontal progress bar not located in current window decor");
            }
            showProgressBars(horizontalProgressBar, circularProgressBar);
        }
    }

    private void showProgressBars(ProgressBar horizontalProgressBar, ProgressBar spinnyProgressBar) {
        int features = getLocalFeatures();
        if (!((features & 32) == 0 || spinnyProgressBar == null || spinnyProgressBar.getVisibility() != 4)) {
            spinnyProgressBar.setVisibility(0);
        }
        if ((features & 4) != 0 && horizontalProgressBar != null && horizontalProgressBar.getProgress() < 10000) {
            horizontalProgressBar.setVisibility(0);
        }
    }

    private void hideProgressBars(ProgressBar horizontalProgressBar, ProgressBar spinnyProgressBar) {
        int features = getLocalFeatures();
        Animation anim = AnimationUtils.loadAnimation(getContext(), R.anim.fade_out);
        anim.setDuration(1000);
        if (!((features & 32) == 0 || spinnyProgressBar == null || spinnyProgressBar.getVisibility() != 0)) {
            spinnyProgressBar.startAnimation(anim);
            spinnyProgressBar.setVisibility(4);
        }
        if ((features & 4) != 0 && horizontalProgressBar != null && horizontalProgressBar.getVisibility() == 0) {
            horizontalProgressBar.startAnimation(anim);
            horizontalProgressBar.setVisibility(4);
        }
    }

    public void setIcon(int resId) {
        this.mIconRes = resId;
        this.mResourcesSetFlags |= 1;
        this.mResourcesSetFlags &= -5;
        if (this.mDecorContentParent != null) {
            this.mDecorContentParent.setIcon(resId);
        }
    }

    public void setDefaultIcon(int resId) {
        if ((this.mResourcesSetFlags & 1) == 0) {
            this.mIconRes = resId;
            if (!(this.mDecorContentParent == null || (this.mDecorContentParent.hasIcon() && (this.mResourcesSetFlags & 4) == 0))) {
                if (resId != 0) {
                    this.mDecorContentParent.setIcon(resId);
                    this.mResourcesSetFlags &= -5;
                } else {
                    this.mDecorContentParent.setIcon(getContext().getPackageManager().getDefaultActivityIcon());
                    this.mResourcesSetFlags |= 4;
                }
            }
        }
    }

    public void setLogo(int resId) {
        this.mLogoRes = resId;
        this.mResourcesSetFlags |= 2;
        if (this.mDecorContentParent != null) {
            this.mDecorContentParent.setLogo(resId);
        }
    }

    public void setDefaultLogo(int resId) {
        if ((this.mResourcesSetFlags & 2) == 0) {
            this.mLogoRes = resId;
            if (!(this.mDecorContentParent == null || (this.mDecorContentParent.hasLogo() ^ 1) == 0)) {
                this.mDecorContentParent.setLogo(resId);
            }
        }
    }

    public void setLocalFocus(boolean hasFocus, boolean inTouchMode) {
        getViewRootImpl().windowFocusChanged(hasFocus, inTouchMode);
    }

    public void injectInputEvent(InputEvent event) {
        getViewRootImpl().dispatchInputEvent(event);
    }

    private ViewRootImpl getViewRootImpl() {
        if (this.mDecor != null) {
            ViewRootImpl viewRootImpl = this.mDecor.getViewRootImpl();
            if (viewRootImpl != null) {
                return viewRootImpl;
            }
        }
        throw new IllegalStateException("view not added");
    }

    public void takeKeyEvents(boolean get) {
        this.mDecor.setFocusable(get);
    }

    public boolean superDispatchKeyEvent(KeyEvent event) {
        return this.mDecor.superDispatchKeyEvent(event);
    }

    public boolean superDispatchKeyShortcutEvent(KeyEvent event) {
        return this.mDecor.superDispatchKeyShortcutEvent(event);
    }

    public boolean superDispatchTouchEvent(MotionEvent event) {
        return this.mDecor.superDispatchTouchEvent(event);
    }

    public boolean superDispatchTrackballEvent(MotionEvent event) {
        return this.mDecor.superDispatchTrackballEvent(event);
    }

    public boolean superDispatchGenericMotionEvent(MotionEvent event) {
        return this.mDecor.superDispatchGenericMotionEvent(event);
    }

    protected boolean onKeyDown(int featureId, int keyCode, KeyEvent event) {
        DispatcherState dispatcher = this.mDecor != null ? this.mDecor.getKeyDispatcherState() : null;
        if (InputEventReceiver.DEBUG_VIVO_INPUT) {
            Log.d(TAG, "onKeyDown event = " + event);
        }
        switch (keyCode) {
            case 4:
                if (event.getRepeatCount() <= 0 && featureId >= 0) {
                    if (dispatcher != null) {
                        dispatcher.startTracking(event, this);
                    }
                    return true;
                }
            case 24:
            case 25:
            case 164:
                if (InputEventReceiver.DEBUG_VIVO_INPUT) {
                    Log.d(TAG, "onKeyDown MediaController = " + this.mMediaController);
                }
                if (this.mMediaController != null) {
                    int direction = 0;
                    switch (keyCode) {
                        case 24:
                            direction = 1;
                            break;
                        case 25:
                            direction = -1;
                            break;
                        case 164:
                            direction = 101;
                            break;
                    }
                    this.mMediaController.adjustVolume(direction, 1);
                } else {
                    MediaSessionLegacyHelper.getHelper(getContext()).sendVolumeKeyEvent(event, this.mVolumeControlStreamType, false);
                }
                return true;
            case 79:
            case 85:
            case 86:
            case 87:
            case 88:
            case 89:
            case 90:
            case 91:
            case 126:
            case 127:
            case 130:
                return this.mMediaController != null && this.mMediaController.dispatchMediaButtonEvent(event);
            case 82:
                if (featureId < 0) {
                    featureId = 0;
                }
                onKeyDownPanel(featureId, event);
                return true;
        }
        return false;
    }

    private KeyguardManager getKeyguardManager() {
        if (this.mKeyguardManager == null) {
            this.mKeyguardManager = (KeyguardManager) getContext().getSystemService("keyguard");
        }
        return this.mKeyguardManager;
    }

    AudioManager getAudioManager() {
        if (this.mAudioManager == null) {
            this.mAudioManager = (AudioManager) getContext().getSystemService("audio");
        }
        return this.mAudioManager;
    }

    protected boolean onKeyUp(int featureId, int keyCode, KeyEvent event) {
        DispatcherState dispatcher = this.mDecor != null ? this.mDecor.getKeyDispatcherState() : null;
        if (dispatcher != null) {
            dispatcher.handleUpEvent(event);
        }
        if (InputEventReceiver.DEBUG_VIVO_INPUT) {
            Log.d(TAG, "onKeyUp event = " + event);
        }
        switch (keyCode) {
            case 4:
                if (featureId >= 0 && event.isTracking() && (event.isCanceled() ^ 1) != 0) {
                    if (featureId == 0) {
                        PanelFeatureState st = getPanelState(featureId, false);
                        if (st != null && st.isInExpandedMode) {
                            reopenMenu(true);
                            return true;
                        }
                    }
                    closePanel(featureId);
                    return true;
                }
            case 24:
            case 25:
                if (this.mMediaController != null) {
                    this.mMediaController.adjustVolume(0, 4116);
                } else {
                    MediaSessionLegacyHelper.getHelper(getContext()).sendVolumeKeyEvent(event, this.mVolumeControlStreamType, false);
                }
                return true;
            case 79:
            case 85:
            case 86:
            case 87:
            case 88:
            case 89:
            case 90:
            case 91:
            case 126:
            case 127:
            case 130:
                return this.mMediaController != null && this.mMediaController.dispatchMediaButtonEvent(event);
            case 82:
                if (featureId < 0) {
                    featureId = 0;
                }
                onKeyUpPanel(featureId, event);
                return true;
            case 84:
                if (!isNotInstantAppAndKeyguardRestricted()) {
                    if (event.isTracking() && (event.isCanceled() ^ 1) != 0) {
                        launchDefaultSearch(event);
                    }
                    return true;
                }
                break;
            case 164:
                MediaSessionLegacyHelper.getHelper(getContext()).sendVolumeKeyEvent(event, Integer.MIN_VALUE, false);
                return true;
            case 171:
                if (this.mSupportsPictureInPicture && (event.isCanceled() ^ 1) != 0) {
                    getWindowControllerCallback().enterPictureInPictureModeIfPossible();
                }
                return true;
        }
        return false;
    }

    private boolean isNotInstantAppAndKeyguardRestricted() {
        if (getContext().getPackageManager().isInstantApp()) {
            return false;
        }
        return getKeyguardManager().inKeyguardRestrictedInputMode();
    }

    protected void onActive() {
    }

    public final View getDecorView() {
        if (this.mDecor == null || this.mForceDecorInstall) {
            installDecor();
        }
        return this.mDecor;
    }

    public final View peekDecorView() {
        return this.mDecor;
    }

    void onViewRootImplSet(ViewRootImpl viewRoot) {
        viewRoot.setActivityConfigCallback(this.mActivityConfigCallback);
    }

    public Bundle saveHierarchyState() {
        Bundle outState = new Bundle();
        if (this.mContentParent == null) {
            return outState;
        }
        SparseArray<Parcelable> states = new SparseArray();
        this.mContentParent.saveHierarchyState(states);
        outState.putSparseParcelableArray(VIEWS_TAG, states);
        View focusedView = this.mContentParent.findFocus();
        if (!(focusedView == null || focusedView.getId() == -1)) {
            outState.putInt(FOCUSED_ID_TAG, focusedView.getId());
        }
        SparseArray<Parcelable> panelStates = new SparseArray();
        savePanelState(panelStates);
        if (panelStates.size() > 0) {
            outState.putSparseParcelableArray(PANELS_TAG, panelStates);
        }
        if (this.mDecorContentParent != null) {
            SparseArray<Parcelable> actionBarStates = new SparseArray();
            this.mDecorContentParent.saveToolbarHierarchyState(actionBarStates);
            outState.putSparseParcelableArray(ACTION_BAR_TAG, actionBarStates);
        }
        return outState;
    }

    public void restoreHierarchyState(Bundle savedInstanceState) {
        if (this.mContentParent != null) {
            SparseArray<Parcelable> savedStates = savedInstanceState.getSparseParcelableArray(VIEWS_TAG);
            if (savedStates != null) {
                this.mContentParent.restoreHierarchyState(savedStates);
            }
            int focusedViewId = savedInstanceState.getInt(FOCUSED_ID_TAG, -1);
            if (focusedViewId != -1) {
                View needsFocus = this.mContentParent.findViewById(focusedViewId);
                if (needsFocus != null) {
                    needsFocus.requestFocus();
                } else {
                    Log.w(TAG, "Previously focused view reported id " + focusedViewId + " during save, but can't be found during restore.");
                }
            }
            SparseArray<Parcelable> panelStates = savedInstanceState.getSparseParcelableArray(PANELS_TAG);
            if (panelStates != null) {
                restorePanelState(panelStates);
            }
            if (this.mDecorContentParent != null) {
                SparseArray<Parcelable> actionBarStates = savedInstanceState.getSparseParcelableArray(ACTION_BAR_TAG);
                if (actionBarStates != null) {
                    doPendingInvalidatePanelMenu();
                    this.mDecorContentParent.restoreToolbarHierarchyState(actionBarStates);
                } else {
                    Log.w(TAG, "Missing saved instance states for action bar views! State will not be restored.");
                }
            }
        }
    }

    private void savePanelState(SparseArray<Parcelable> icicles) {
        PanelFeatureState[] panels = this.mPanels;
        if (panels != null) {
            for (int curFeatureId = panels.length - 1; curFeatureId >= 0; curFeatureId--) {
                if (panels[curFeatureId] != null) {
                    icicles.put(curFeatureId, panels[curFeatureId].onSaveInstanceState());
                }
            }
        }
    }

    private void restorePanelState(SparseArray<Parcelable> icicles) {
        for (int i = icicles.size() - 1; i >= 0; i--) {
            int curFeatureId = icicles.keyAt(i);
            PanelFeatureState st = getPanelState(curFeatureId, false);
            if (st != null) {
                st.onRestoreInstanceState((Parcelable) icicles.get(curFeatureId));
                invalidatePanelMenu(curFeatureId);
            }
        }
    }

    void openPanelsAfterRestore() {
        PanelFeatureState[] panels = this.mPanels;
        if (panels != null) {
            for (int i = panels.length - 1; i >= 0; i--) {
                PanelFeatureState st = panels[i];
                if (st != null) {
                    st.applyFrozenState();
                    if (!st.isOpen && st.wasLastOpen) {
                        st.isInExpandedMode = st.wasLastExpanded;
                        openPanel(st, null);
                    }
                }
            }
        }
    }

    protected DecorView generateDecor(int featureId) {
        Context context;
        if (this.mUseDecorContext) {
            Context applicationContext = getContext().getApplicationContext();
            if (applicationContext == null) {
                context = getContext();
            } else {
                context = new DecorContext(applicationContext, getContext().getResources());
                if (this.mTheme != -1) {
                    context.setTheme(this.mTheme);
                }
            }
        } else {
            context = getContext();
        }
        return new DecorView(context, featureId, this, getAttributes());
    }

    protected ViewGroup generateLayout(DecorView decor) {
        int layoutResource;
        TypedArray a = getWindowStyle();
        this.mIsFloating = a.getBoolean(4, false);
        int flagsToUpdate = 65792 & (~getForcedWindowFlags());
        if (this.mIsFloating) {
            setLayout(-2, -2);
            setFlags(0, flagsToUpdate);
        } else {
            setFlags(65792, flagsToUpdate);
        }
        if (a.getBoolean(3, false)) {
            requestFeature(1);
        } else if (a.getBoolean(15, false)) {
            requestFeature(8);
        }
        if (a.getBoolean(17, false)) {
            requestFeature(9);
        }
        if (a.getBoolean(16, false)) {
            requestFeature(10);
        }
        if (a.getBoolean(25, false)) {
            requestFeature(11);
        }
        if (a.getBoolean(9, false)) {
            setFlags(1024, (~getForcedWindowFlags()) & 1024);
        }
        if (a.getBoolean(23, false)) {
            setFlags(67108864, (~getForcedWindowFlags()) & 67108864);
        }
        if (a.getBoolean(24, false)) {
            setFlags(134217728, (~getForcedWindowFlags()) & 134217728);
        }
        if (a.getBoolean(22, false)) {
            setFlags(33554432, (~getForcedWindowFlags()) & 33554432);
        }
        if (a.getBoolean(14, false)) {
            setFlags(1048576, (~getForcedWindowFlags()) & 1048576);
        }
        if (a.getBoolean(18, getContext().getApplicationInfo().targetSdkVersion >= 11)) {
            setFlags(8388608, (~getForcedWindowFlags()) & 8388608);
        }
        a.getValue(19, this.mMinWidthMajor);
        a.getValue(20, this.mMinWidthMinor);
        if (a.hasValue(54)) {
            if (this.mFixedWidthMajor == null) {
                this.mFixedWidthMajor = new TypedValue();
            }
            a.getValue(54, this.mFixedWidthMajor);
        }
        if (a.hasValue(55)) {
            if (this.mFixedWidthMinor == null) {
                this.mFixedWidthMinor = new TypedValue();
            }
            a.getValue(55, this.mFixedWidthMinor);
        }
        if (a.hasValue(52)) {
            if (this.mFixedHeightMajor == null) {
                this.mFixedHeightMajor = new TypedValue();
            }
            a.getValue(52, this.mFixedHeightMajor);
        }
        if (a.hasValue(53)) {
            if (this.mFixedHeightMinor == null) {
                this.mFixedHeightMinor = new TypedValue();
            }
            a.getValue(53, this.mFixedHeightMinor);
        }
        if (a.getBoolean(26, false)) {
            requestFeature(12);
        }
        if (a.getBoolean(45, false)) {
            requestFeature(13);
        }
        this.mIsTranslucent = a.getBoolean(5, false);
        Context context = getContext();
        int targetSdk = context.getApplicationInfo().targetSdkVersion;
        boolean targetPreHoneycomb = targetSdk < 11;
        boolean targetPreIcs = targetSdk < 14;
        boolean targetPreL = targetSdk < 21;
        boolean targetHcNeedsOptions = context.getResources().getBoolean(R.bool.target_honeycomb_needs_options_menu);
        boolean noActionBar = hasFeature(8) ? hasFeature(1) : true;
        if (targetPreHoneycomb || (targetPreIcs && targetHcNeedsOptions && noActionBar)) {
            setNeedsMenuKey(1);
        } else {
            setNeedsMenuKey(2);
        }
        if (VivoThemeUtil.isVigourTheme(getContext())) {
            this.mVivoStyle = true;
            this.mDecor.mVivoStyle = true;
        }
        TypedArray typeArray = getContext().obtainStyledAttributes(com.vivo.internal.R.styleable.VivoTheme);
        WindowManager.LayoutParams params = getAttributes();
        int IconColor = typeArray.getInt(12, -1);
        String pkg = getContext().getPackageName();
        int defcolor = getContext().getResources().getColor(com.vivo.internal.R.color.vivo_window_statusbar_bg_color);
        boolean setWindowDraw = a.getBoolean(34, false);
        if (this.DEBUG_SYSTEMUI) {
            Slog.d(TAG, "DEBUG_SYSTEMUI:windowDrawsAttr=" + setWindowDraw + " targetPreL=" + targetPreL);
            Slog.d(TAG, "DEBUG_SYSTEMUI:mStatusBarColor was set " + Integer.toHexString(this.mStatusBarColor));
            Slog.d(TAG, "DEBUG_SYSTEMUI:VivoTheme " + this.mVivoStyle);
        }
        if (isInVivoStyleList() || (this.mVivoStyle ^ 1) == 0) {
            if (this.DEBUG_SYSTEMUI) {
                Slog.d(TAG, "DEBUG_SYSTEMUI:vivo statusbar style ");
            }
            if (!this.mIsFloating && ActivityManager.isHighEndGfx()) {
                if (this.DEBUG_SYSTEMUI) {
                    Slog.d(TAG, "DEBUG_SYSTEMUI:windowDrawsFlag set");
                }
                setFlags(Integer.MIN_VALUE, (~getForcedWindowFlags()) & Integer.MIN_VALUE);
                if (!a.getBoolean(34, false) || (targetPreL ^ 1) == 0) {
                    this.mStatusBarColor = defcolor;
                } else if (!this.mForcedStatusBarColor) {
                    this.mStatusBarColor = a.getColor(35, DEFAULT_LIGHT_COVER_COLOR);
                    if (this.mStatusBarColor != DEFAULT_LIGHT_COVER_COLOR) {
                        if (this.DEBUG_SYSTEMUI) {
                            Slog.d(TAG, "DEBUG_SYSTEMUI:mForcedStatusBarColorInTheme=" + this.mForcedStatusBarColorInTheme + " mStatusBarColor=" + this.mStatusBarColor);
                        }
                        this.mForcedStatusBarColorInTheme = true;
                    }
                }
            }
        } else {
            if (this.DEBUG_SYSTEMUI) {
                Slog.d(TAG, "DEBUG_SYSTEMUI:origin statusbar style ");
            }
            if (!this.mIsFloating && ActivityManager.isHighEndGfx()) {
                if (!targetPreL && a.getBoolean(34, false)) {
                    setFlags(Integer.MIN_VALUE, (~getForcedWindowFlags()) & Integer.MIN_VALUE);
                }
                if (this.mDecor.mForceWindowDrawsStatusBarBackground) {
                    params.privateFlags |= 131072;
                }
            }
            if (!this.mForcedStatusBarColor) {
                this.mStatusBarColor = a.getColor(35, DEFAULT_LIGHT_COVER_COLOR);
                if (this.mStatusBarColor != DEFAULT_LIGHT_COVER_COLOR) {
                    if (this.DEBUG_SYSTEMUI) {
                        Slog.d(TAG, "DEBUG_SYSTEMUI:mForcedStatusBarColorInTheme=" + this.mForcedStatusBarColorInTheme + " mStatusBarColor=" + this.mStatusBarColor);
                    }
                    this.mForcedStatusBarColorInTheme = true;
                }
            }
            IconColor = 1;
        }
        if (this.DEBUG_SYSTEMUI) {
            Slog.d(TAG, "DEBUG_SYSTEMUI:mForcedNavigationBarColor" + this.mForcedNavigationBarColor + " mForcedStatusBarColor" + this.mForcedStatusBarColor);
        }
        if (!this.mForcedNavigationBarColor) {
            int color = a.getColor(36, DEFAULT_LIGHT_COVER_COLOR);
            this.mNavigationBarDividerColor = a.getColor(50, 0);
            if (this.DEBUG_SYSTEMUI) {
                Slog.d(TAG, "DEBUG_SYSTEMUI:mNavigationBarColor  get from style=" + color);
            }
            if (color != DEFAULT_LIGHT_COVER_COLOR) {
                this.mNavigationBarColor = color;
                this.mForcedNavigationBarColorInTheme = true;
                if (this.DEBUG_SYSTEMUI) {
                    Slog.d(TAG, "DEBUG_SYSTEMUI:mForcedNavigationBarColorInTheme=" + this.mForcedNavigationBarColorInTheme + " mNavigationBarColor=" + this.mNavigationBarColor);
                }
            } else {
                this.mNavigationBarColor = NavigationBarWindow.getSystemSettingColor();
            }
        }
        applySystemUIColorSetting(false);
        params.statusbarIconColor = IconColor;
        if (this.DEBUG_SYSTEMUI) {
            Slog.d(TAG, "DEBUG_SYSTEMUI:IconColor=" + IconColor);
        }
        params.statusbarColor = this.mStatusBarColor;
        if (this.DEBUG_SYSTEMUI) {
            Slog.d(TAG, "DEBUG_SYSTEMUI:StatusBarColor final set " + Integer.toHexString(this.mStatusBarColor));
        }
        if (this.DEBUG_SYSTEMUI) {
            Slog.d(TAG, "DEBUG_SYSTEMUI:navbarColor final set " + Integer.toHexString(this.mNavigationBarColor));
        }
        applyAlienScreenSetting();
        getRotation();
        params.homeIndicatorState = this.mHomeIndicatorState;
        params.topCoverColor = this.mFringeCoverColor;
        params.bottomCoverColor = this.mHomeIndicatorCoverColor;
        params.topCoverEnable = this.mEnableFringeCover ? 1 : 0;
        params.bottomCoverEnable = this.mEnableHomeIndicatorCover ? 1 : 0;
        params.keepFullScreen = this.mKeepFullScreen;
        params.internalFlag = this.mInternalFlag;
        if (params.packageName != null && "com.vivo.childrenmode".equals(params.packageName) && this.mIsStartingWindow) {
            this.mHomeIndicatorState = 0;
            params.homeIndicatorState = 0;
            if (this.DEBUG_SYSTEMUI) {
                Slog.d(TAG, "DEBUG_ALIENSCREEN: force set mHomeIndicatorState: " + this.mHomeIndicatorState);
            }
        }
        if (this.DEBUG_SYSTEMUI) {
            Slog.d(TAG, "DEBUG_ALIENSCREEN:mHomeIndicatorState:" + this.mHomeIndicatorState + " mFringeCoverColor=" + this.mFringeCoverColor + " mHomeIndicatorCoverColor=" + this.mHomeIndicatorCoverColor + " mEnableFringeCover=" + this.mEnableFringeCover + " mEnableHomeIndicatorCover=" + this.mEnableHomeIndicatorCover);
        }
        if (a.getBoolean(46, false)) {
            decor.setSystemUiVisibility(decor.getSystemUiVisibility() | 8192);
        }
        if (a.getBoolean(49, false)) {
            decor.setSystemUiVisibility(decor.getSystemUiVisibility() | 16);
        }
        if ((this.mAlwaysReadCloseOnTouchAttr || getContext().getApplicationInfo().targetSdkVersion >= 11) && a.getBoolean(21, false)) {
            setCloseOnTouchOutsideIfNotSet(true);
        }
        if (!hasSoftInputMode()) {
            params.softInputMode = a.getInt(13, params.softInputMode);
        }
        if (a.getBoolean(11, this.mIsFloating)) {
            if ((getForcedWindowFlags() & 2) == 0) {
                params.flags |= 2;
            }
            if (!haveDimAmount()) {
                params.dimAmount = a.getFloat(0, 0.5f);
            }
        }
        if (params.windowAnimations == 0) {
            params.windowAnimations = a.getResourceId(8, 0);
        }
        if (getContainer() == null) {
            if (this.mBackgroundDrawable == null) {
                if (this.mBackgroundResource == 0) {
                    this.mBackgroundResource = a.getResourceId(1, 0);
                }
                if (this.mFrameResource == 0) {
                    this.mFrameResource = a.getResourceId(2, 0);
                }
                this.mBackgroundFallbackResource = a.getResourceId(47, 0);
            }
            if (this.mLoadElevation) {
                this.mElevation = a.getDimension(38, 0.0f);
            }
            this.mClipToOutline = a.getBoolean(39, false);
            this.mTextColor = a.getColor(7, 0);
        }
        int features = getLocalFeatures();
        TypedValue res;
        if ((features & 2048) != 0) {
            layoutResource = R.layout.screen_swipe_dismiss;
            setCloseOnSwipeEnabled(true);
        } else if ((features & 24) != 0) {
            if (this.mIsFloating) {
                res = new TypedValue();
                getContext().getTheme().resolveAttribute(R.attr.dialogTitleIconsDecorLayout, res, true);
                layoutResource = res.resourceId;
            } else {
                layoutResource = R.layout.screen_title_icons;
            }
            removeFeature(8);
        } else if ((features & 36) != 0 && (features & 256) == 0) {
            layoutResource = R.layout.screen_progress;
        } else if ((features & 128) != 0) {
            if (this.mIsFloating) {
                res = new TypedValue();
                getContext().getTheme().resolveAttribute(R.attr.dialogCustomTitleDecorLayout, res, true);
                layoutResource = res.resourceId;
            } else {
                layoutResource = R.layout.screen_custom_title;
            }
            removeFeature(8);
        } else if ((features & 2) == 0) {
            if (this.mIsFloating) {
                res = new TypedValue();
                getContext().getTheme().resolveAttribute(R.attr.dialogTitleDecorLayout, res, true);
                layoutResource = res.resourceId;
            } else if ((features & 256) != 0) {
                layoutResource = a.getResourceId(51, R.layout.screen_action_bar);
            } else if (this.mVivoStyle) {
                layoutResource = com.vivo.internal.R.layout.vigour_screen_title;
            } else {
                layoutResource = R.layout.screen_title;
            }
        } else if ((features & 1024) != 0) {
            layoutResource = R.layout.screen_simple_overlay_action_mode;
        } else {
            layoutResource = R.layout.screen_simple;
        }
        this.mDecor.startChanging();
        this.mDecor.onResourcesLoaded(this.mLayoutInflater, layoutResource);
        ViewGroup contentParent = (ViewGroup) findViewById(16908290);
        if (contentParent == null) {
            throw new RuntimeException("Window couldn't find content container view");
        }
        if ((features & 32) != 0) {
            ProgressBar progress = getCircularProgressBar(false);
            if (progress != null) {
                progress.setIndeterminate(true);
            }
        }
        if ((features & 2048) != 0) {
            registerSwipeCallbacks(contentParent);
        }
        if (getContainer() == null) {
            Drawable background;
            Drawable frame;
            if (this.mBackgroundResource != 0) {
                background = getContext().getDrawable(this.mBackgroundResource);
            } else {
                background = this.mBackgroundDrawable;
            }
            this.mDecor.setWindowBackground(background);
            if (this.mFrameResource != 0) {
                frame = getContext().getDrawable(this.mFrameResource);
            } else {
                frame = null;
            }
            this.mDecor.setWindowFrame(frame);
            this.mDecor.setElevation(this.mElevation);
            this.mDecor.setClipToOutline(this.mClipToOutline);
            if (this.mTitle != null) {
                setTitle(this.mTitle);
            }
            if (this.mTitleColor == 0) {
                this.mTitleColor = this.mTextColor;
            }
            setTitleColor(this.mTitleColor);
        }
        this.mDecor.finishChanging();
        return contentParent;
    }

    public void alwaysReadCloseOnTouchAttr() {
        this.mAlwaysReadCloseOnTouchAttr = true;
    }

    private void installDecor() {
        this.mForceDecorInstall = false;
        if (this.mDecor == null) {
            this.mDecor = generateDecor(-1);
            this.mDecor.setDescendantFocusability(262144);
            this.mDecor.setIsRootNamespace(true);
            if (!(this.mInvalidatePanelMenuPosted || this.mInvalidatePanelMenuFeatures == 0)) {
                this.mDecor.postOnAnimation(this.mInvalidatePanelMenuRunnable);
            }
        } else {
            this.mDecor.setWindow(this);
        }
        if (this.mContentParent == null) {
            this.mContentParent = generateLayout(this.mDecor);
            this.mDecor.makeOptionalFitsSystemWindows();
            DecorContentParent decorContentParent = (DecorContentParent) this.mDecor.findViewById(R.id.decor_content_parent);
            if (decorContentParent != null) {
                this.mDecorContentParent = decorContentParent;
                this.mDecorContentParent.setWindowCallback(getCallback());
                if (this.mDecorContentParent.getTitle() == null) {
                    this.mDecorContentParent.setWindowTitle(this.mTitle);
                }
                int localFeatures = getLocalFeatures();
                for (int i = 0; i < 13; i++) {
                    if (((1 << i) & localFeatures) != 0) {
                        this.mDecorContentParent.initFeature(i);
                    }
                }
                this.mDecorContentParent.setUiOptions(this.mUiOptions);
                if ((this.mResourcesSetFlags & 1) != 0 || (this.mIconRes != 0 && (this.mDecorContentParent.hasIcon() ^ 1) != 0)) {
                    this.mDecorContentParent.setIcon(this.mIconRes);
                } else if ((this.mResourcesSetFlags & 1) == 0 && this.mIconRes == 0 && (this.mDecorContentParent.hasIcon() ^ 1) != 0) {
                    this.mDecorContentParent.setIcon(getContext().getPackageManager().getDefaultActivityIcon());
                    this.mResourcesSetFlags |= 4;
                }
                if (!((this.mResourcesSetFlags & 2) == 0 && (this.mLogoRes == 0 || (this.mDecorContentParent.hasLogo() ^ 1) == 0))) {
                    this.mDecorContentParent.setLogo(this.mLogoRes);
                }
                PanelFeatureState st = getPanelState(0, false);
                if (!isDestroyed() && ((st == null || st.menu == null) && (this.mIsStartingWindow ^ 1) != 0)) {
                    invalidatePanelMenu(8);
                }
            } else {
                this.mTitleView = (TextView) findViewById(R.id.title);
                if (this.mTitleView != null) {
                    if ((getLocalFeatures() & 2) != 0) {
                        View titleContainer = findViewById(R.id.title_container);
                        if (titleContainer != null) {
                            titleContainer.setVisibility(8);
                        } else {
                            this.mTitleView.setVisibility(8);
                        }
                        this.mContentParent.setForeground(null);
                    } else {
                        this.mTitleView.setText(this.mTitle);
                    }
                }
            }
            if (this.mDecor.getBackground() == null && this.mBackgroundFallbackResource != 0) {
                this.mDecor.setBackgroundFallback(this.mBackgroundFallbackResource);
            }
            if (hasFeature(13)) {
                if (this.mTransitionManager == null) {
                    int transitionRes = getWindowStyle().getResourceId(27, 0);
                    if (transitionRes != 0) {
                        this.mTransitionManager = TransitionInflater.from(getContext()).inflateTransitionManager(transitionRes, this.mContentParent);
                    } else {
                        this.mTransitionManager = new TransitionManager();
                    }
                }
                this.mEnterTransition = getTransition(this.mEnterTransition, null, 28);
                this.mReturnTransition = getTransition(this.mReturnTransition, USE_DEFAULT_TRANSITION, 40);
                this.mExitTransition = getTransition(this.mExitTransition, null, 29);
                this.mReenterTransition = getTransition(this.mReenterTransition, USE_DEFAULT_TRANSITION, 41);
                this.mSharedElementEnterTransition = getTransition(this.mSharedElementEnterTransition, null, 30);
                this.mSharedElementReturnTransition = getTransition(this.mSharedElementReturnTransition, USE_DEFAULT_TRANSITION, 42);
                this.mSharedElementExitTransition = getTransition(this.mSharedElementExitTransition, null, 31);
                this.mSharedElementReenterTransition = getTransition(this.mSharedElementReenterTransition, USE_DEFAULT_TRANSITION, 43);
                if (this.mAllowEnterTransitionOverlap == null) {
                    this.mAllowEnterTransitionOverlap = Boolean.valueOf(getWindowStyle().getBoolean(33, true));
                }
                if (this.mAllowReturnTransitionOverlap == null) {
                    this.mAllowReturnTransitionOverlap = Boolean.valueOf(getWindowStyle().getBoolean(32, true));
                }
                if (this.mBackgroundFadeDurationMillis < 0) {
                    this.mBackgroundFadeDurationMillis = (long) getWindowStyle().getInteger(37, 300);
                }
                if (this.mSharedElementsUseOverlay == null) {
                    this.mSharedElementsUseOverlay = Boolean.valueOf(getWindowStyle().getBoolean(44, true));
                }
            }
        }
    }

    private Transition getTransition(Transition currentValue, Transition defaultValue, int id) {
        if (currentValue != defaultValue) {
            return currentValue;
        }
        int transitionId = getWindowStyle().getResourceId(id, -1);
        Transition transition = defaultValue;
        if (!(transitionId == -1 || transitionId == R.transition.no_transition)) {
            transition = TransitionInflater.from(getContext()).inflateTransition(transitionId);
            if ((transition instanceof TransitionSet) && ((TransitionSet) transition).getTransitionCount() == 0) {
                transition = null;
            }
        }
        return transition;
    }

    private Drawable loadImageURI(Uri uri) {
        try {
            return Drawable.createFromStream(getContext().getContentResolver().openInputStream(uri), null);
        } catch (Exception e) {
            Log.w(TAG, "Unable to open content: " + uri);
            return null;
        }
    }

    private DrawableFeatureState getDrawableState(int featureId, boolean required) {
        if ((getFeatures() & (1 << featureId)) != 0) {
            DrawableFeatureState[] ar = this.mDrawables;
            if (ar == null || ar.length <= featureId) {
                DrawableFeatureState[] nar = new DrawableFeatureState[(featureId + 1)];
                if (ar != null) {
                    System.arraycopy(ar, 0, nar, 0, ar.length);
                }
                ar = nar;
                this.mDrawables = nar;
            }
            DrawableFeatureState st = ar[featureId];
            if (st == null) {
                st = new DrawableFeatureState(featureId);
                ar[featureId] = st;
            }
            return st;
        } else if (!required) {
            return null;
        } else {
            throw new RuntimeException("The feature has not been requested");
        }
    }

    PanelFeatureState getPanelState(int featureId, boolean required) {
        return getPanelState(featureId, required, null);
    }

    private PanelFeatureState getPanelState(int featureId, boolean required, PanelFeatureState convertPanelState) {
        if ((getFeatures() & (1 << featureId)) != 0) {
            PanelFeatureState[] ar = this.mPanels;
            if (ar == null || ar.length <= featureId) {
                PanelFeatureState[] nar = new PanelFeatureState[(featureId + 1)];
                if (ar != null) {
                    System.arraycopy(ar, 0, nar, 0, ar.length);
                }
                ar = nar;
                this.mPanels = nar;
            }
            PanelFeatureState st = ar[featureId];
            if (st == null) {
                if (convertPanelState != null) {
                    st = convertPanelState;
                } else {
                    st = new PanelFeatureState(featureId);
                }
                ar[featureId] = st;
            }
            st.mVivoStyle = this.mVivoStyle;
            return st;
        } else if (!required) {
            return null;
        } else {
            throw new RuntimeException("The feature has not been requested");
        }
    }

    public final void setChildDrawable(int featureId, Drawable drawable) {
        DrawableFeatureState st = getDrawableState(featureId, true);
        st.child = drawable;
        updateDrawable(featureId, st, false);
    }

    public final void setChildInt(int featureId, int value) {
        updateInt(featureId, value, false);
    }

    public boolean isShortcutKey(int keyCode, KeyEvent event) {
        PanelFeatureState st = getPanelState(0, false);
        if (st == null || st.menu == null) {
            return false;
        }
        return st.menu.isShortcutKey(keyCode, event);
    }

    private void updateDrawable(int featureId, DrawableFeatureState st, boolean fromResume) {
        if (this.mContentParent != null) {
            int featureMask = 1 << featureId;
            if ((getFeatures() & featureMask) != 0 || (fromResume ^ 1) == 0) {
                Drawable drawable = null;
                if (st != null) {
                    drawable = st.child;
                    if (drawable == null) {
                        drawable = st.local;
                    }
                    if (drawable == null) {
                        drawable = st.def;
                    }
                }
                if ((getLocalFeatures() & featureMask) == 0) {
                    if (getContainer() != null && (isActive() || fromResume)) {
                        getContainer().setChildDrawable(featureId, drawable);
                    }
                } else if (!(st == null || (st.cur == drawable && st.curAlpha == st.alpha))) {
                    st.cur = drawable;
                    st.curAlpha = st.alpha;
                    onDrawableChanged(featureId, drawable, st.alpha);
                }
            }
        }
    }

    private void updateInt(int featureId, int value, boolean fromResume) {
        if (this.mContentParent != null) {
            int featureMask = 1 << featureId;
            if ((getFeatures() & featureMask) != 0 || (fromResume ^ 1) == 0) {
                if ((getLocalFeatures() & featureMask) != 0) {
                    onIntChanged(featureId, value);
                } else if (getContainer() != null) {
                    getContainer().setChildInt(featureId, value);
                }
            }
        }
    }

    private ImageView getLeftIconView() {
        if (this.mLeftIconView != null) {
            return this.mLeftIconView;
        }
        if (this.mContentParent == null) {
            installDecor();
        }
        ImageView imageView = (ImageView) findViewById(R.id.left_icon);
        this.mLeftIconView = imageView;
        return imageView;
    }

    protected void dispatchWindowAttributesChanged(WindowManager.LayoutParams attrs) {
        super.dispatchWindowAttributesChanged(attrs);
        if (this.mDecor != null) {
            this.mDecor.updateColorViews(null, true);
        }
    }

    private ProgressBar getCircularProgressBar(boolean shouldInstallDecor) {
        if (this.mCircularProgressBar != null) {
            return this.mCircularProgressBar;
        }
        if (this.mContentParent == null && shouldInstallDecor) {
            installDecor();
        }
        this.mCircularProgressBar = (ProgressBar) findViewById(R.id.progress_circular);
        if (this.mCircularProgressBar != null) {
            this.mCircularProgressBar.setVisibility(4);
        }
        return this.mCircularProgressBar;
    }

    private ProgressBar getHorizontalProgressBar(boolean shouldInstallDecor) {
        if (this.mHorizontalProgressBar != null) {
            return this.mHorizontalProgressBar;
        }
        if (this.mContentParent == null && shouldInstallDecor) {
            installDecor();
        }
        this.mHorizontalProgressBar = (ProgressBar) findViewById(R.id.progress_horizontal);
        if (this.mHorizontalProgressBar != null) {
            this.mHorizontalProgressBar.setVisibility(4);
        }
        return this.mHorizontalProgressBar;
    }

    private ImageView getRightIconView() {
        if (this.mRightIconView != null) {
            return this.mRightIconView;
        }
        if (this.mContentParent == null) {
            installDecor();
        }
        ImageView imageView = (ImageView) findViewById(R.id.right_icon);
        this.mRightIconView = imageView;
        return imageView;
    }

    private void registerSwipeCallbacks(ViewGroup contentParent) {
        if (contentParent instanceof SwipeDismissLayout) {
            SwipeDismissLayout swipeDismiss = (SwipeDismissLayout) contentParent;
            swipeDismiss.setOnDismissedListener(new OnDismissedListener() {
                public void onDismissed(SwipeDismissLayout layout) {
                    PhoneWindow.this.dispatchOnWindowSwipeDismissed();
                    PhoneWindow.this.dispatchOnWindowDismissed(false, true);
                }
            });
            swipeDismiss.setOnSwipeProgressChangedListener(new OnSwipeProgressChangedListener() {
                public void onSwipeProgressChanged(SwipeDismissLayout layout, float alpha, float translate) {
                    int flags;
                    WindowManager.LayoutParams newParams = PhoneWindow.this.getAttributes();
                    newParams.x = (int) translate;
                    newParams.alpha = alpha;
                    PhoneWindow.this.setAttributes(newParams);
                    if (newParams.x == 0) {
                        flags = 1024;
                    } else {
                        flags = 512;
                    }
                    PhoneWindow.this.setFlags(flags, View.SYSTEM_UI_LAYOUT_FLAGS);
                }

                public void onSwipeCancelled(SwipeDismissLayout layout) {
                    WindowManager.LayoutParams newParams = PhoneWindow.this.getAttributes();
                    if (newParams.x != 0 || newParams.alpha != 1.0f) {
                        newParams.x = 0;
                        newParams.alpha = 1.0f;
                        PhoneWindow.this.setAttributes(newParams);
                        PhoneWindow.this.setFlags(1024, View.SYSTEM_UI_LAYOUT_FLAGS);
                    }
                }
            });
            return;
        }
        Log.w(TAG, "contentParent is not a SwipeDismissLayout: " + contentParent);
    }

    public void setCloseOnSwipeEnabled(boolean closeOnSwipeEnabled) {
        if (hasFeature(11) && (this.mContentParent instanceof SwipeDismissLayout)) {
            ((SwipeDismissLayout) this.mContentParent).setDismissable(closeOnSwipeEnabled);
        }
        super.setCloseOnSwipeEnabled(closeOnSwipeEnabled);
    }

    private void callOnPanelClosed(int featureId, PanelFeatureState panel, Menu menu) {
        Window.Callback cb = getCallback();
        if (cb != null) {
            if (menu == null) {
                if (panel == null && featureId >= 0 && featureId < this.mPanels.length) {
                    panel = this.mPanels[featureId];
                }
                if (panel != null) {
                    menu = panel.menu;
                }
            }
            if ((panel == null || (panel.isOpen ^ 1) == 0) && !isDestroyed()) {
                cb.onPanelClosed(featureId, menu);
            }
        }
    }

    private boolean isTvUserSetupComplete() {
        int i = 0;
        boolean isTvSetupComplete = Secure.getInt(getContext().getContentResolver(), Secure.USER_SETUP_COMPLETE, 0) != 0;
        if (Secure.getInt(getContext().getContentResolver(), Secure.TV_USER_SETUP_COMPLETE, 0) != 0) {
            i = 1;
        }
        return isTvSetupComplete & i;
    }

    private boolean launchDefaultSearch(KeyEvent event) {
        if (getContext().getPackageManager().hasSystemFeature("android.software.leanback") && (isTvUserSetupComplete() ^ 1) != 0) {
            return false;
        }
        boolean result;
        Window.Callback cb = getCallback();
        if (cb == null || isDestroyed()) {
            result = false;
        } else {
            sendCloseSystemWindows("search");
            int deviceId = event.getDeviceId();
            SearchEvent searchEvent = null;
            if (deviceId != 0) {
                searchEvent = new SearchEvent(InputDevice.getDevice(deviceId));
            }
            try {
                result = cb.onSearchRequested(searchEvent);
            } catch (AbstractMethodError e) {
                Log.e(TAG, "WindowCallback " + cb.getClass().getName() + " does not implement" + " method onSearchRequested(SearchEvent); fa", e);
                result = cb.onSearchRequested();
            }
        }
        if (result || (getContext().getResources().getConfiguration().uiMode & 15) != 4) {
            return result;
        }
        Bundle args = new Bundle();
        args.putInt("android.intent.extra.ASSIST_INPUT_DEVICE_ID", event.getDeviceId());
        return ((SearchManager) getContext().getSystemService("search")).launchLegacyAssist(null, UserHandle.myUserId(), args);
    }

    public void setVolumeControlStream(int streamType) {
        this.mVolumeControlStreamType = streamType;
    }

    public int getVolumeControlStream() {
        return this.mVolumeControlStreamType;
    }

    public void setMediaController(MediaController controller) {
        this.mMediaController = controller;
    }

    public MediaController getMediaController() {
        return this.mMediaController;
    }

    public void setEnterTransition(Transition enterTransition) {
        this.mEnterTransition = enterTransition;
    }

    public void setReturnTransition(Transition transition) {
        this.mReturnTransition = transition;
    }

    public void setExitTransition(Transition exitTransition) {
        this.mExitTransition = exitTransition;
    }

    public void setReenterTransition(Transition transition) {
        this.mReenterTransition = transition;
    }

    public void setSharedElementEnterTransition(Transition sharedElementEnterTransition) {
        this.mSharedElementEnterTransition = sharedElementEnterTransition;
    }

    public void setSharedElementReturnTransition(Transition transition) {
        this.mSharedElementReturnTransition = transition;
    }

    public void setSharedElementExitTransition(Transition sharedElementExitTransition) {
        this.mSharedElementExitTransition = sharedElementExitTransition;
    }

    public void setSharedElementReenterTransition(Transition transition) {
        this.mSharedElementReenterTransition = transition;
    }

    public Transition getEnterTransition() {
        return this.mEnterTransition;
    }

    public Transition getReturnTransition() {
        if (this.mReturnTransition == USE_DEFAULT_TRANSITION) {
            return getEnterTransition();
        }
        return this.mReturnTransition;
    }

    public Transition getExitTransition() {
        return this.mExitTransition;
    }

    public Transition getReenterTransition() {
        if (this.mReenterTransition == USE_DEFAULT_TRANSITION) {
            return getExitTransition();
        }
        return this.mReenterTransition;
    }

    public Transition getSharedElementEnterTransition() {
        return this.mSharedElementEnterTransition;
    }

    public Transition getSharedElementReturnTransition() {
        return this.mSharedElementReturnTransition == USE_DEFAULT_TRANSITION ? getSharedElementEnterTransition() : this.mSharedElementReturnTransition;
    }

    public Transition getSharedElementExitTransition() {
        return this.mSharedElementExitTransition;
    }

    public Transition getSharedElementReenterTransition() {
        return this.mSharedElementReenterTransition == USE_DEFAULT_TRANSITION ? getSharedElementExitTransition() : this.mSharedElementReenterTransition;
    }

    public void setAllowEnterTransitionOverlap(boolean allow) {
        this.mAllowEnterTransitionOverlap = Boolean.valueOf(allow);
    }

    public boolean getAllowEnterTransitionOverlap() {
        return this.mAllowEnterTransitionOverlap == null ? true : this.mAllowEnterTransitionOverlap.booleanValue();
    }

    public void setAllowReturnTransitionOverlap(boolean allowExitTransitionOverlap) {
        this.mAllowReturnTransitionOverlap = Boolean.valueOf(allowExitTransitionOverlap);
    }

    public boolean getAllowReturnTransitionOverlap() {
        return this.mAllowReturnTransitionOverlap == null ? true : this.mAllowReturnTransitionOverlap.booleanValue();
    }

    public long getTransitionBackgroundFadeDuration() {
        if (this.mBackgroundFadeDurationMillis < 0) {
            return 300;
        }
        return this.mBackgroundFadeDurationMillis;
    }

    public void setTransitionBackgroundFadeDuration(long fadeDurationMillis) {
        if (fadeDurationMillis < 0) {
            throw new IllegalArgumentException("negative durations are not allowed");
        }
        this.mBackgroundFadeDurationMillis = fadeDurationMillis;
    }

    public void setSharedElementsUseOverlay(boolean sharedElementsUseOverlay) {
        this.mSharedElementsUseOverlay = Boolean.valueOf(sharedElementsUseOverlay);
    }

    public boolean getSharedElementsUseOverlay() {
        return this.mSharedElementsUseOverlay == null ? true : this.mSharedElementsUseOverlay.booleanValue();
    }

    int getLocalFeaturesPrivate() {
        return super.getLocalFeatures();
    }

    protected void setDefaultWindowFormat(int format) {
        super.setDefaultWindowFormat(format);
    }

    void sendCloseSystemWindows() {
        sendCloseSystemWindows(getContext(), null);
    }

    void sendCloseSystemWindows(String reason) {
        sendCloseSystemWindows(getContext(), reason);
    }

    public static void sendCloseSystemWindows(Context context, String reason) {
        if (ActivityManager.isSystemReady()) {
            try {
                ActivityManager.getService().closeSystemDialogs(reason);
            } catch (RemoteException e) {
            }
        }
    }

    public int getStatusBarColor() {
        return this.mStatusBarColor;
    }

    public void setStatusBarColor(int color) {
        this.mStatusBarColor = color;
        this.mForcedStatusBarColor = true;
        if (this.DEBUG_SYSTEMUI) {
            Slog.d(TAG, "DEBUG_SYSTEMUI:setStatusBarColor:" + Integer.toHexString(this.mStatusBarColor));
        }
        WindowManager.LayoutParams paramsT = getAttributes();
        paramsT.statusbarColor = this.mStatusBarColor;
        if (shouldDispatchAttributes()) {
            dispatchWindowAttributesChanged(paramsT);
        }
        if (this.mDecor != null) {
            if (this.DEBUG_SYSTEMUI) {
                Slog.d(TAG, "DEBUG_SYSTEMUI:setStatusBarColor:" + Integer.toHexString(this.mStatusBarColor) + " updateView");
            }
            this.mDecor.updateColorViews(null, false);
        }
    }

    private boolean shouldDispatchAttributes() {
        String pkgName = getContext().getPackageName();
        for (String pkg : mFilterPKG) {
            if (pkg.equals(pkgName)) {
                return false;
            }
        }
        return true;
    }

    public int getNavigationBarColor() {
        return this.mNavigationBarColor;
    }

    public void setNavigationBarColor(int color) {
        if (this.DEBUG_SYSTEMUI) {
            Slog.d(this.mLogTag, "DEBUG_SYSTEMUI:setNavigationBarColor:" + Integer.toHexString(color), new RuntimeException().fillInStackTrace());
        }
        setNavigationBarColorInner(color);
        this.mForcedNavigationBarColor = true;
        if (this.mNavigationBarColorCallback != null) {
            this.mNavigationBarColorCallback.setNavigationBarColor(this.mNavigationBarColor);
        }
    }

    public void setIsStartingWindow(boolean isStartingWindow) {
        this.mIsStartingWindow = isStartingWindow;
    }

    private boolean isInVivoStyleList() {
        String pkg = getContext().getPackageName();
        if (pkg == null || (!pkg.equals("com.bbk.theme") && !pkg.equals("com.android.bbkmusic") && !pkg.equals("com.iqoo.secure") && !pkg.equals(BrowserContract.AUTHORITY) && !pkg.equals("com.bbk.appstore") && !pkg.equals("com.chaozh.iReader") && !pkg.equals("com.android.email") && !pkg.equals("com.vivo.email") && !pkg.equals("com.vivo.space") && !pkg.equals("com.iqoo.powersaving") && !pkg.equals("com.vivo.findphone") && !pkg.equals("com.vivo.securedaemonservice") && !pkg.equals("com.vivo.PCTools") && !pkg.equals("com.bbk.PCTools") && !pkg.equals("com.bbk.cloud") && !pkg.equals("com.vivo.abe") && !pkg.equals("com.bbk.account") && !pkg.equals("com.vivo.abeui") && !pkg.equals("com.vivo.appfilter") && !pkg.equals("com.vivo.game") && !pkg.equals("com.baidu.input_bbk.service") && !pkg.equals("com.sohu.inputmethod.sogou.vivo"))) {
            return false;
        }
        return true;
    }

    public void setTheme(int resid) {
        this.mTheme = resid;
        if (this.mDecor != null) {
            Context context = this.mDecor.getContext();
            if (context instanceof DecorContext) {
                context.setTheme(resid);
            }
        }
    }

    public void setResizingCaptionDrawable(Drawable drawable) {
        this.mDecor.setUserCaptionBackgroundDrawable(drawable);
    }

    public void setDecorCaptionShade(int decorCaptionShade) {
        this.mDecorCaptionShade = decorCaptionShade;
        if (this.mDecor != null) {
            this.mDecor.updateDecorCaptionShade();
        }
    }

    int getDecorCaptionShade() {
        return this.mDecorCaptionShade;
    }

    public void setAttributes(WindowManager.LayoutParams params) {
        super.setAttributes(params);
        if (this.mDecor != null) {
            this.mDecor.updateLogTag(params);
        }
    }

    private boolean forceNavigationBarColor() {
        return !this.mForcedNavigationBarColor ? this.mForcedNavigationBarColorInTheme : true;
    }

    public void setNavigationBarColorCallback(NavigationBarColorCallback callback) {
        this.mNavigationBarColorCallback = callback;
        if (forceNavigationBarColor()) {
            this.mNavigationBarColorCallback.setNavigationBarColor(this.mNavigationBarColor);
        }
    }

    public void updateNavigationBarColor(int color) {
        if (!forceNavigationBarColor()) {
            setNavigationBarColorInner(color);
        }
    }

    private void setNavigationBarColorInner(int color) {
        if (this.mNavigationBarColor != color) {
            this.mNavigationBarColor = color;
            if (this.mDecor != null) {
                if (this.DEBUG_SYSTEMUI) {
                    Log.d(this.mLogTag, "setNavigationBarColorInner:" + Integer.toHexString(this.mNavigationBarColor), new RuntimeException().fillInStackTrace());
                }
                this.mDecor.updateColorViews(null, false);
                this.mDecor.updateNavigationGuardColor();
            }
        }
    }

    private static String getTitleSuffix(WindowManager.LayoutParams params) {
        if (params == null) {
            return "";
        }
        String[] split = params.getTitle().toString().split("\\.");
        if (split.length > 0) {
            return split[split.length - 1];
        }
        return params.getTitle().toString();
    }

    void updateLogTag(WindowManager.LayoutParams params) {
        this.mLogTag = TAG + "[" + getTitleSuffix(params) + (params != null ? SettingsStringUtil.DELIMITER + params.type : "") + "]";
    }

    public void applySystemUIColorSetting() {
        applySystemUIColorSetting(true);
    }

    private void applySystemUIColorSetting(boolean refresh) {
        boolean z = true;
        if (FtBuild.getRomVersion() >= 3.6f) {
            WindowManagerGlobal.getInstance();
            IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
            try {
                if (!this.mConfigInited) {
                    ApplicationInfo appInfo = getContext().getPackageManager().getApplicationInfo(getContext().getPackageName(), 128);
                    if (appInfo != null) {
                        if ((appInfo.flags & 1) == 0) {
                            z = false;
                        }
                        this.mSystemApp = z;
                        this.mIsVivoApp = VivoPermissionManager.isVivoApp(getContext(), getContext().getPackageName());
                        if (appInfo.metaData != null) {
                            this.mNavColorConfig = appInfo.metaData.getString(NAVBAR_COLOR_METAKEY, "default");
                            this.mSBColorConfig = appInfo.metaData.getString(STATUSBAR_COLOR_METAKEY, "default");
                        }
                    }
                    this.mConfigInited = true;
                }
                if ("default".equals(this.mNavColorConfig)) {
                    this.mNavColorConfig = CUSTOM_META_VAULE;
                }
                if ("default".equals(this.mSBColorConfig)) {
                    this.mSBColorConfig = CUSTOM_META_VAULE;
                }
                if ((FOLLOW_SYSTEM_META_VAULE.equals(this.mSBColorConfig) && (this.mForcedStatusBarColor ^ 1) != 0) || (!(!CUSTOM_META_VAULE.equals(this.mSBColorConfig) || (this.mForcedStatusBarColor ^ 1) == 0 || (this.mForcedStatusBarColorInTheme ^ 1) == 0) || FORCED_FOLLOW_META_VAULE.equals(this.mSBColorConfig))) {
                    String sbColorString = "";
                    sbColorString = wm.fetchSystemSetting("sb_color");
                    if (!(sbColorString == null || sbColorString == "")) {
                        int sbColor = Color.parseColor(sbColorString);
                        this.mStatusBarColor = sbColor;
                        if (refresh) {
                            setStatusBarColor(sbColor);
                            this.mForcedStatusBarColor = false;
                        }
                    }
                }
                if (this.DEBUG_SYSTEMUI) {
                    Slog.d(TAG, "DEBUG_SYSTEMUI:applySystemUIColorSetting  window:" + this + " mSBColorConfig:" + this.mSBColorConfig + " mNavColorConfig:" + this.mNavColorConfig + " isSystemApp:" + this.mSystemApp + " isVivoApp:" + this.mIsVivoApp + " NavigationBarColor:" + this.mNavigationBarColor + " mStatusBarColor:" + this.mStatusBarColor);
                }
            } catch (Exception e) {
                if (this.DEBUG_SYSTEMUI) {
                    Slog.d(TAG, "DEBUG_SYSTEMUI:e=" + e);
                }
            }
        }
    }

    private void initVivoWindowPolicy() {
        if (this.DEBUG_SYSTEMUI) {
            Slog.d(TAG, "DEBUG_METADATA:initVivoWindowPolicy begin mHomeIndicatorStateUserConfig=" + this.mHomeIndicatorStateUserConfig);
        }
        try {
            ApplicationInfo appInfo = getContext().getPackageManager().getApplicationInfo(getContext().getPackageName(), 128);
            WindowManagerGlobal.getInstance();
            IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
            if (appInfo != null) {
                this.mSystemApp = (appInfo.flags & 1) != 0;
                this.mIsVivoApp = VivoPermissionManager.isVivoApp(getContext(), getContext().getPackageName());
                if (appInfo.metaData != null) {
                    if (this.DEBUG_SYSTEMUI) {
                        Slog.d(TAG, "DEBUG_METADATA:getContext().getPackageName()=" + getContext().getPackageName() + " appInfo.metaData = " + appInfo.metaData.toString());
                    }
                    this.mNavColorConfig = appInfo.metaData.getString(NAVBAR_COLOR_METAKEY, "default");
                    this.mSBColorConfig = appInfo.metaData.getString(STATUSBAR_COLOR_METAKEY, "default");
                    if (FtBuild.getRomVersion() >= 4.0f) {
                        mKeepFullUserConfigApp = appInfo.metaData.getBoolean(KEEP_FULLSCREEN_METAKEY, false);
                        this.mHomeIndicatorStateUserConfig = appInfo.metaData.getString(HOME_INDICATOR_STATE_METAKEY, "");
                        if (this.DEBUG_SYSTEMUI) {
                            Slog.d(TAG, "DEBUG_METADATA:initVivoWindowPolicy from app mHomeIndicatorStateUserConfig=" + this.mHomeIndicatorStateUserConfig);
                        }
                    }
                }
            }
            if (this.mComponentName != null && FtBuild.getRomVersion() >= 4.0f) {
                ActivityInfo activityinfo = getContext().getPackageManager().getActivityInfo(this.mComponentName, 128);
                if (!(activityinfo == null || activityinfo.metaData == null)) {
                    if (this.DEBUG_SYSTEMUI) {
                        Slog.d(TAG, "DEBUG_METADATA:mComponentName=" + this.mComponentName + " activityinfo.metaData = " + activityinfo.metaData.toString());
                    }
                    mKeepFullUserConfigCmp = activityinfo.metaData.getBoolean(KEEP_FULLSCREEN_METAKEY, false);
                    this.mHomeIndicatorStateUserConfig = activityinfo.metaData.getString(HOME_INDICATOR_STATE_METAKEY, "");
                    if (this.DEBUG_SYSTEMUI) {
                        Slog.d(TAG, "DEBUG_METADATA:initVivoWindowPolicy from activityInfo mHomeIndicatorStateUserConfig=" + this.mHomeIndicatorStateUserConfig);
                    }
                }
            }
            String title = this.mComponentName != null ? this.mComponentName.flattenToString() : "null";
            String alienScreenFetchString = "WindowPolicy-ALIENSCREEN," + getContext().getPackageName() + "," + title;
            if (this.DEBUG_SYSTEMUI) {
                Slog.d(TAG, "DEBUG_WINDOWPOLICY:ready to fetch fetchString=" + alienScreenFetchString);
            }
            String alienScreenPolicy = wm.fetchSystemSetting(alienScreenFetchString);
            String internalFlagFetchString = "WindowPolicy-INTERNALFLAG," + getContext().getPackageName() + "," + title;
            if (this.DEBUG_SYSTEMUI) {
                Slog.d(TAG, "DEBUG_WINDOWPOLICY:ready to fetch fetchString=" + internalFlagFetchString);
            }
            String internalFlag = wm.fetchSystemSetting(internalFlagFetchString);
            String homeIndicatorFetchString = "WindowPolicy-HOMEINDICATOR," + getContext().getPackageName() + "," + title;
            if (this.DEBUG_SYSTEMUI) {
                Slog.d(TAG, "DEBUG_WINDOWPOLICY:ready to fetch fetchString=" + homeIndicatorFetchString);
            }
            this.mHomeIndicatorStateListConfig = wm.fetchSystemSetting(homeIndicatorFetchString);
            if (!(internalFlag == null || (internalFlag.equals("") ^ 1) == 0)) {
                int internalFlagInt = Integer.decode(internalFlag).intValue();
                this.mInternalFlag = internalFlagInt;
                if (this.DEBUG_SYSTEMUI) {
                    Slog.d(TAG, "DEBUG_WINDOWPOLICY:got internalFlag=" + internalFlag + " decode to :" + internalFlagInt + " , then mInternalFlag=" + this.mInternalFlag);
                }
            }
            String homeIndicatorOn = wm.fetchSystemSetting("homeIndicator_on");
            if (!(!this.mSystemApp || this.mHomeIndicatorStateUserConfig == null || (this.mHomeIndicatorStateUserConfig.equals("") ^ 1) == 0)) {
                int i = this.mHomeIndicatorStateUserConfig.equals("light") ? 1 : this.mHomeIndicatorStateUserConfig.equals("dark") ? 0 : this.mHomeIndicatorStateUserConfig.equals("dark_dim") ? 2 : this.mHomeIndicatorStateUserConfig.equals("light_dim") ? 3 : this.mHomeIndicatorStateUserConfig.equals("hide") ? 4 : this.mHomeIndicatorStateUserConfig.equals("disable") ? 5 : -1;
                this.mHomeIndicatorState = i;
            }
            this.mHomeIndicatorOn = "true".equals(homeIndicatorOn);
            this.mKeepFullListConfig = alienScreenPolicy;
        } catch (Exception e) {
            Log.e(TAG, "DEBUG_WINDOWPOLICY", e);
        }
        if (this.DEBUG_SYSTEMUI) {
            Slog.d(TAG, "DEBUG_ALIENSCREEN: initVivoWindowPolicy mKeepFullUserConfigApp=" + mKeepFullUserConfigApp + " mFringeCoverColorUserConfig=" + this.mFringeCoverColorUserConfig + " mHomeIndicatorCoverColorUserConfig=" + this.mHomeIndicatorCoverColorUserConfig + " mKeepFullUserConfigCmp=" + mKeepFullUserConfigCmp + " mHomeIndicatorStateUserConfig=" + this.mHomeIndicatorStateUserConfig + " mHomeIndicatorStateListConfig=" + this.mHomeIndicatorStateListConfig + " mHomeIndicatorOn=" + this.mHomeIndicatorOn + " mKeepFullListConfig=" + this.mKeepFullListConfig);
        }
    }

    private void applyAlienScreenSetting() {
        this.mEnableHomeIndicatorCover = true;
        this.mEnableFringeCover = true;
        if ((this.mSystemApp || this.mIsVivoApp) && (isGMSApp() ^ 1) != 0) {
            this.mKeepFullScreen = 1;
            if (this.DEBUG_SYSTEMUI) {
                Slog.d(TAG, "DEBUG_ALIENSCREEN:applyAlienScreenSetting systemApp should always keepFull");
            }
            return;
        }
        int i;
        if (mKeepFullUserConfigApp || mKeepFullUserConfigCmp) {
            i = 1;
        } else {
            i = 0;
        }
        this.mKeepFullScreen = i;
        String res = "";
        if (!(this.mKeepFullListConfig == null || ("".equals(this.mKeepFullListConfig) ^ 1) == 0)) {
            if ("NOTKEEPFULL".equals(this.mKeepFullListConfig)) {
                this.mEnableFringeCover = true;
                this.mKeepFullScreen = 0;
                res = res + "List set NOTKEEPFULL";
            } else if ("KEEPFULL".equals(this.mKeepFullListConfig)) {
                this.mEnableFringeCover = false;
                this.mKeepFullScreen = 1;
                res = res + "List set KEEPFULL";
            }
        }
        res = res + ", final return mKeepFullScreen=" + this.mKeepFullScreen + " ,mEnableFringeCover = " + this.mEnableFringeCover;
        if (this.DEBUG_SYSTEMUI) {
            Slog.d(TAG, "DEBUG_ALIENSCREEN:applyAlienScreenSetting res =  " + res);
        }
    }

    public void setHomeIndicatorState(int state) {
        if (this.DEBUG_SYSTEMUI) {
            Slog.d(TAG, "DEBUG_ALIENSCREEN:setHomeIndicatorState " + state);
        }
        this.mHomeIndicatorState = state;
        WindowManager.LayoutParams params = getAttributes();
        params.homeIndicatorState = this.mHomeIndicatorState;
        dispatchWindowAttributesChanged(params);
        if (this.mDecor != null) {
            this.mDecor.updateColorViews(null, false);
        }
    }

    public boolean isHomeIndicatorOn() {
        try {
            WindowManagerGlobal.getInstance();
            String sHomeIndicatorOn = WindowManagerGlobal.getWindowManagerService().fetchSystemSetting("homeIndicator_on");
            if (this.DEBUG_SYSTEMUI) {
                Slog.d(TAG, "DEBUG_ALIENSCREEN:isHomeIndicatorOn fetchSystemSetting =" + sHomeIndicatorOn);
            }
            this.mHomeIndicatorOn = "true".equals(sHomeIndicatorOn);
        } catch (Exception e) {
        }
        return this.mHomeIndicatorOn;
    }

    public void setAlienScreenCoverColor(int topColor, int bottomColor) {
        if (this.DEBUG_SYSTEMUI) {
            Slog.d(TAG, "DEBUG_ALIENSCREEN:setAlienScreenCoverColor upColor=" + topColor + " bottomColor=" + bottomColor);
        }
    }

    private boolean isGMSApp() {
        try {
            String packageName = getContext().getPackageName();
            if (packageName != null && (this.mOtherGMSApp.contains(packageName) || packageName.contains("com.google.android"))) {
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    public void updateActivityName(String name) {
        this.mClsName = name;
    }

    public String getActivityName() {
        return this.mClsName;
    }
}
