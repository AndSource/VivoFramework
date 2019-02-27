package com.android.internal.policy;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.FtBuild;
import android.text.format.DateUtils;
import android.util.FtFeature;
import android.util.Log;
import android.view.DisplayInfo;
import com.android.internal.R;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import java.util.ArrayList;

public class DividerSnapAlgorithm {
    private static final int MIN_DISMISS_VELOCITY_DP_PER_SECOND = 600;
    private static final int MIN_FLING_VELOCITY_DP_PER_SECOND = 400;
    private static final int SNAP_FIXED_RATIO = 1;
    private static final int SNAP_MODE_16_9 = 0;
    private static final int SNAP_MODE_MINIMIZED = 3;
    private static final int SNAP_ONLY_1_1 = 2;
    private final SnapTarget mDismissEndTarget;
    private final SnapTarget mDismissStartTarget;
    private final int mDisplayHeight;
    private final int mDisplayWidth;
    private final int mDividerSize;
    private final SnapTarget mFirstSplitTarget;
    private final float mFixedRatio;
    private final Rect mInsets;
    private boolean mIsHorizontalDivision;
    private final SnapTarget mLastSplitTarget;
    private final SnapTarget mMiddleTarget;
    private final float mMinDismissVelocityPxPerSecond;
    private final float mMinFlingVelocityPxPerSecond;
    private final int mMinimalSizeResizableTask;
    private final int mSnapMode;
    private final ArrayList<SnapTarget> mTargets;
    private final int mTaskHeightInMinimizedMode;

    public static class SnapTarget {
        public static final int FLAG_DISMISS_END = 2;
        public static final int FLAG_DISMISS_START = 1;
        public static final int FLAG_NONE = 0;
        private final float distanceMultiplier;
        public final int flag;
        public final int position;
        public final int taskPosition;

        public SnapTarget(int position, int taskPosition, int flag) {
            this(position, taskPosition, flag, 1.0f);
        }

        public SnapTarget(int position, int taskPosition, int flag, float distanceMultiplier) {
            this.position = position;
            this.taskPosition = taskPosition;
            this.flag = flag;
            this.distanceMultiplier = distanceMultiplier;
        }
    }

    public static DividerSnapAlgorithm create(Context ctx, Rect insets) {
        boolean z = true;
        DisplayInfo displayInfo = new DisplayInfo();
        ((DisplayManager) ctx.getSystemService(DisplayManager.class)).getDisplay(0).getDisplayInfo(displayInfo);
        int dividerWindowWidth = ctx.getResources().getDimensionPixelSize(R.dimen.docked_stack_divider_thickness);
        int dividerInsets = ctx.getResources().getDimensionPixelSize(R.dimen.docked_stack_divider_insets);
        Resources resources = ctx.getResources();
        int i = displayInfo.logicalWidth;
        int i2 = displayInfo.logicalHeight;
        int i3 = dividerWindowWidth - (dividerInsets * 2);
        if (ctx.getApplicationContext().getResources().getConfiguration().orientation != 1) {
            z = false;
        }
        return new DividerSnapAlgorithm(resources, i, i2, i3, z, insets);
    }

    public DividerSnapAlgorithm(Resources res, int displayWidth, int displayHeight, int dividerSize, boolean isHorizontalDivision, Rect insets) {
        this(res, displayWidth, displayHeight, dividerSize, isHorizontalDivision, insets, false);
    }

    public DividerSnapAlgorithm(Resources res, int displayWidth, int displayHeight, int dividerSize, boolean isHorizontalDivision, Rect insets, boolean isMinimizedMode) {
        int i = 3;
        this.mTargets = new ArrayList();
        this.mInsets = new Rect();
        this.mMinFlingVelocityPxPerSecond = res.getDisplayMetrics().density * 400.0f;
        this.mMinDismissVelocityPxPerSecond = res.getDisplayMetrics().density * 600.0f;
        this.mDividerSize = dividerSize;
        this.mDisplayWidth = displayWidth;
        this.mDisplayHeight = displayHeight;
        this.mIsHorizontalDivision = isHorizontalDivision;
        this.mInsets.set(insets);
        if (isMultiWindowValid()) {
            if (!isMinimizedMode) {
                i = res.getInteger(com.vivo.internal.R.integer.vigour_config_dockedStackDividerSnapMode);
            }
            this.mSnapMode = i;
            this.mFixedRatio = res.getFraction(com.vivo.internal.R.fraction.vigour_config_dockedStackDividerSnapMode, 1, 1);
            this.mMinimalSizeResizableTask = res.getDimensionPixelSize(com.vivo.internal.R.dimen.vigour_default_minimal_size_resizable_task);
            this.mTaskHeightInMinimizedMode = res.getDimensionPixelSize(com.vivo.internal.R.dimen.vigour_task_height_of_minimized_mode);
        } else {
            if (!isMinimizedMode) {
                i = res.getInteger(R.integer.config_dockedStackDividerSnapMode);
            }
            this.mSnapMode = i;
            this.mFixedRatio = res.getFraction(R.fraction.docked_stack_divider_fixed_ratio, 1, 1);
            this.mMinimalSizeResizableTask = res.getDimensionPixelSize(R.dimen.default_minimal_size_resizable_task);
            this.mTaskHeightInMinimizedMode = res.getDimensionPixelSize(R.dimen.task_height_of_minimized_mode);
        }
        calculateTargets(isHorizontalDivision);
        this.mFirstSplitTarget = (SnapTarget) this.mTargets.get(1);
        this.mLastSplitTarget = (SnapTarget) this.mTargets.get(this.mTargets.size() - 2);
        this.mDismissStartTarget = (SnapTarget) this.mTargets.get(0);
        this.mDismissEndTarget = (SnapTarget) this.mTargets.get(this.mTargets.size() - 1);
        this.mMiddleTarget = (SnapTarget) this.mTargets.get(this.mTargets.size() / 2);
    }

    public boolean isSplitScreenFeasible() {
        int size;
        int statusBarSize = this.mInsets.top;
        int navBarSize = this.mIsHorizontalDivision ? this.mInsets.bottom : this.mInsets.right;
        if (this.mIsHorizontalDivision) {
            size = this.mDisplayHeight;
        } else {
            size = this.mDisplayWidth;
        }
        if ((((size - navBarSize) - statusBarSize) - this.mDividerSize) / 2 >= this.mMinimalSizeResizableTask) {
            return true;
        }
        return false;
    }

    public SnapTarget calculateSnapTarget(int position, float velocity) {
        return calculateSnapTarget(position, velocity, true);
    }

    public SnapTarget calculateSnapTarget(int position, float velocity, boolean hardDismiss) {
        if (position < this.mFirstSplitTarget.position && velocity < (-this.mMinDismissVelocityPxPerSecond)) {
            return this.mDismissStartTarget;
        }
        if (position > this.mLastSplitTarget.position && velocity > this.mMinDismissVelocityPxPerSecond) {
            return this.mDismissEndTarget;
        }
        if (Math.abs(velocity) < this.mMinFlingVelocityPxPerSecond) {
            if (isMultiWindowValid()) {
                return snapContinuous(position, hardDismiss);
            }
            return snap(position, hardDismiss);
        } else if (velocity < 0.0f) {
            return this.mFirstSplitTarget;
        } else {
            return this.mLastSplitTarget;
        }
    }

    public SnapTarget calculateNonDismissingSnapTarget(int position) {
        SnapTarget target;
        if (isMultiWindowValid()) {
            target = snapNonDismissTargets(position);
        } else {
            target = snap(position, false);
        }
        if (target == this.mDismissStartTarget) {
            return this.mFirstSplitTarget;
        }
        if (target == this.mDismissEndTarget) {
            return this.mLastSplitTarget;
        }
        return target;
    }

    public float calculateDismissingFraction(int position) {
        if (position < this.mFirstSplitTarget.position) {
            return 1.0f - (((float) (position - getStartInset())) / ((float) (this.mFirstSplitTarget.position - getStartInset())));
        }
        if (position > this.mLastSplitTarget.position) {
            return ((float) (position - this.mLastSplitTarget.position)) / ((float) ((this.mDismissEndTarget.position - this.mLastSplitTarget.position) - this.mDividerSize));
        }
        return 0.0f;
    }

    public SnapTarget getClosestDismissTarget(int position) {
        if (position < this.mFirstSplitTarget.position) {
            return this.mDismissStartTarget;
        }
        if (position > this.mLastSplitTarget.position) {
            return this.mDismissEndTarget;
        }
        if (position - this.mDismissStartTarget.position < this.mDismissEndTarget.position - position) {
            return this.mDismissStartTarget;
        }
        return this.mDismissEndTarget;
    }

    public SnapTarget getFirstSplitTarget() {
        return this.mFirstSplitTarget;
    }

    public SnapTarget getLastSplitTarget() {
        return this.mLastSplitTarget;
    }

    public SnapTarget getDismissStartTarget() {
        return this.mDismissStartTarget;
    }

    public SnapTarget getDismissEndTarget() {
        return this.mDismissEndTarget;
    }

    private int getStartInset() {
        if (this.mIsHorizontalDivision) {
            return this.mInsets.top;
        }
        return this.mInsets.left;
    }

    private int getEndInset() {
        if (this.mIsHorizontalDivision) {
            return this.mInsets.bottom;
        }
        return this.mInsets.right;
    }

    private SnapTarget snap(int position, boolean hardDismiss) {
        int minIndex = -1;
        float minDistance = Float.MAX_VALUE;
        int size = this.mTargets.size();
        for (int i = 0; i < size; i++) {
            SnapTarget target = (SnapTarget) this.mTargets.get(i);
            float distance = (float) Math.abs(position - target.position);
            if (hardDismiss) {
                distance /= target.distanceMultiplier;
            }
            if (distance < minDistance) {
                minIndex = i;
                minDistance = distance;
            }
        }
        return (SnapTarget) this.mTargets.get(minIndex);
    }

    private SnapTarget snapContinuous(int position, boolean hardDismiss) {
        float distanceToDismiss;
        if (position > this.mLastSplitTarget.position) {
            distanceToDismiss = (float) Math.abs(position - this.mDismissEndTarget.position);
            float distanceToLast = (float) Math.abs(position - this.mLastSplitTarget.position);
            if (hardDismiss) {
                distanceToDismiss /= this.mDismissEndTarget.distanceMultiplier;
                distanceToLast /= this.mLastSplitTarget.distanceMultiplier;
            }
            if (distanceToDismiss < distanceToLast) {
                return this.mDismissEndTarget;
            }
            return this.mLastSplitTarget;
        } else if (position < this.mFirstSplitTarget.position) {
            distanceToDismiss = (float) Math.abs(position - this.mDismissStartTarget.position);
            float distanceToFirst = (float) Math.abs(position - this.mFirstSplitTarget.position);
            if (hardDismiss) {
                distanceToDismiss /= this.mDismissStartTarget.distanceMultiplier;
                distanceToFirst /= this.mFirstSplitTarget.distanceMultiplier;
            }
            if (distanceToDismiss < distanceToFirst) {
                return this.mDismissStartTarget;
            }
            return this.mFirstSplitTarget;
        } else {
            if (position == this.mLastSplitTarget.position) {
                position--;
            } else if (position >= this.mFirstSplitTarget.position) {
                position++;
            }
            return new SnapTarget(position, position, 0);
        }
    }

    private SnapTarget snapNonDismissTargets(int position) {
        if (position >= this.mLastSplitTarget.position) {
            return this.mLastSplitTarget;
        }
        if (position <= this.mFirstSplitTarget.position) {
            return this.mFirstSplitTarget;
        }
        if (position == this.mMiddleTarget.position) {
            return this.mMiddleTarget;
        }
        return new SnapTarget(position, position, 0);
    }

    private void calculateTargets(boolean isHorizontalDivision) {
        int dividerMax;
        this.mTargets.clear();
        if (isHorizontalDivision) {
            dividerMax = this.mDisplayHeight;
        } else {
            dividerMax = this.mDisplayWidth;
        }
        int navBarSize = isHorizontalDivision ? this.mInsets.bottom : this.mInsets.right;
        this.mTargets.add(new SnapTarget(-this.mDividerSize, -this.mDividerSize, 1, 0.35f));
        switch (this.mSnapMode) {
            case 0:
                if (!isMultiWindowValid()) {
                    addRatio16_9Targets(isHorizontalDivision, dividerMax);
                    break;
                } else {
                    addFixedTargets(isHorizontalDivision, dividerMax);
                    break;
                }
            case 1:
                addFixedDivisionTargets(isHorizontalDivision, dividerMax);
                break;
            case 2:
                addMiddleTarget(isHorizontalDivision);
                break;
            case 3:
                addMinimizedTarget(isHorizontalDivision);
                break;
        }
        this.mTargets.add(new SnapTarget(dividerMax - navBarSize, dividerMax, 2, 0.35f));
    }

    private void addNonDismissingTargets(boolean isHorizontalDivision, int topPosition, int bottomPosition, int dividerMax) {
        maybeAddTarget(topPosition, topPosition - this.mInsets.top);
        addMiddleTarget(isHorizontalDivision);
        maybeAddTarget(bottomPosition, (dividerMax - this.mInsets.bottom) - (this.mDividerSize + bottomPosition));
    }

    private void addFixedDivisionTargets(boolean isHorizontalDivision, int dividerMax) {
        int end;
        int start = isHorizontalDivision ? this.mInsets.top : this.mInsets.left;
        if (isHorizontalDivision) {
            end = this.mDisplayHeight - this.mInsets.bottom;
        } else {
            end = this.mDisplayWidth - this.mInsets.right;
        }
        int size = ((int) (this.mFixedRatio * ((float) (end - start)))) - (this.mDividerSize / 2);
        addNonDismissingTargets(isHorizontalDivision, start + size, (end - size) - this.mDividerSize, dividerMax);
    }

    private void addRatio16_9Targets(boolean isHorizontalDivision, int dividerMax) {
        int end;
        int endOther;
        int start = isHorizontalDivision ? this.mInsets.top : this.mInsets.left;
        if (isHorizontalDivision) {
            end = this.mDisplayHeight - this.mInsets.bottom;
        } else {
            end = this.mDisplayWidth - this.mInsets.right;
        }
        int startOther = isHorizontalDivision ? this.mInsets.left : this.mInsets.top;
        if (isHorizontalDivision) {
            endOther = this.mDisplayWidth - this.mInsets.right;
        } else {
            endOther = this.mDisplayHeight - this.mInsets.bottom;
        }
        int sizeInt = (int) Math.floor((double) (0.5625f * ((float) (endOther - startOther))));
        addNonDismissingTargets(isHorizontalDivision, start + sizeInt, (end - sizeInt) - this.mDividerSize, dividerMax);
    }

    private void addFixedTargets(boolean isHorizontalDivision, int dividerMax) {
        int end;
        int start = isHorizontalDivision ? this.mInsets.top : this.mInsets.left;
        if (isHorizontalDivision) {
            end = this.mDisplayHeight - this.mInsets.bottom;
        } else {
            end = this.mDisplayWidth - this.mInsets.right;
        }
        int fixedSize = MetricsEvent.ACTION_DELETION_HELPER_REMOVE_CANCEL;
        switch (dividerMax) {
            case 1280:
                fixedSize = MetricsEvent.ACTION_DELETION_HELPER_REMOVE_CANCEL;
                break;
            case 1440:
                fixedSize = 525;
                break;
            case 1520:
                fixedSize = MetricsEvent.DIALOG_ZEN_ACCESS_REVOKE;
                break;
            case 1920:
                fixedSize = 700;
                break;
            case 2160:
                fixedSize = MetricsEvent.DEFAULT_HOME_PICKER;
                break;
            case 2280:
                fixedSize = MetricsEvent.ACTION_SETTINGS_TILE_CLICK;
                break;
            case 2316:
                fixedSize = MetricsEvent.DEFAULT_VOICE_INPUT_PICKER;
                break;
            case 2340:
                fixedSize = MetricsEvent.ACTION_SETTINGS_MENU_BATTERY_APPS_TOGGLE;
                break;
            case DateUtils.FORMAT_NO_NOON_MIDNIGHT /*2560*/:
                fixedSize = MetricsEvent.ENTERPRISE_PRIVACY_DEFAULT_APPS;
                break;
        }
        int topPosition = start + fixedSize;
        int bottomPosition = (end - fixedSize) - this.mDividerSize;
        Log.d("multiwinowsnap", "resize start = " + start + " " + " end = " + end);
        Log.d("multiwinowsnap", "resize topPosition = " + topPosition + " " + " bottomPosition = " + bottomPosition);
        addNonDismissingTargets(isHorizontalDivision, topPosition, bottomPosition, dividerMax);
    }

    private void maybeAddTarget(int position, int smallerSize) {
        if (smallerSize >= this.mMinimalSizeResizableTask) {
            this.mTargets.add(new SnapTarget(position, position, 0));
        }
    }

    private void addMiddleTarget(boolean isHorizontalDivision) {
        int position;
        if (FtFeature.isFeatureSupport(32)) {
            position = DockedDividerUtils.calculateAlienMiddlePosition(isHorizontalDivision, this.mInsets, this.mDisplayWidth, this.mDisplayHeight, this.mDividerSize);
        } else {
            position = DockedDividerUtils.calculateMiddlePosition(isHorizontalDivision, this.mInsets, this.mDisplayWidth, this.mDisplayHeight, this.mDividerSize);
        }
        this.mTargets.add(new SnapTarget(position, position, 0));
    }

    private void addMinimizedTarget(boolean isHorizontalDivision) {
        int position = this.mTaskHeightInMinimizedMode + this.mInsets.top;
        if (!isHorizontalDivision) {
            position += this.mInsets.left;
        }
        this.mTargets.add(new SnapTarget(position, position, 0));
    }

    public SnapTarget getMiddleTarget() {
        return this.mMiddleTarget;
    }

    public SnapTarget getNextTarget(SnapTarget snapTarget) {
        int index = this.mTargets.indexOf(snapTarget);
        if (index == -1 || index >= this.mTargets.size() - 1) {
            return snapTarget;
        }
        return (SnapTarget) this.mTargets.get(index + 1);
    }

    public SnapTarget getPreviousTarget(SnapTarget snapTarget) {
        int index = this.mTargets.indexOf(snapTarget);
        if (index == -1 || index <= 0) {
            return snapTarget;
        }
        return (SnapTarget) this.mTargets.get(index - 1);
    }

    public boolean isFirstSplitTargetAvailable() {
        return this.mFirstSplitTarget != this.mMiddleTarget;
    }

    public boolean isLastSplitTargetAvailable() {
        return this.mLastSplitTarget != this.mMiddleTarget;
    }

    public SnapTarget cycleNonDismissTarget(SnapTarget snapTarget, int increment) {
        int index = this.mTargets.indexOf(snapTarget);
        if (index == -1) {
            return snapTarget;
        }
        SnapTarget newTarget = (SnapTarget) this.mTargets.get(((this.mTargets.size() + index) + increment) % this.mTargets.size());
        if (newTarget == this.mDismissStartTarget) {
            return this.mLastSplitTarget;
        }
        if (newTarget == this.mDismissEndTarget) {
            return this.mFirstSplitTarget;
        }
        return newTarget;
    }

    public boolean isMultiWindowValid() {
        return FtBuild.IS_MULTIWINDOWVALID;
    }
}
