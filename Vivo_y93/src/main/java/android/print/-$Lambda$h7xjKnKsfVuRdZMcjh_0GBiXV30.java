package android.print;

import android.print.PrintManager.PrintServiceRecommendationsChangeListener;
import android.print.PrintManager.PrintServicesChangeListener;

final /* synthetic */ class -$Lambda$h7xjKnKsfVuRdZMcjh_0GBiXV30 implements Runnable {
    private final /* synthetic */ byte $id;
    /* renamed from: -$f0 */
    private final /* synthetic */ Object f1-$f0;

    private final /* synthetic */ void $m$0() {
        ((PrintServiceRecommendationsChangeListener) this.f1-$f0).onPrintServiceRecommendationsChanged();
    }

    private final /* synthetic */ void $m$1() {
        ((PrintServicesChangeListener) this.f1-$f0).onPrintServicesChanged();
    }

    public /* synthetic */ -$Lambda$h7xjKnKsfVuRdZMcjh_0GBiXV30(byte b, Object obj) {
        this.$id = b;
        this.f1-$f0 = obj;
    }

    public final void run() {
        switch (this.$id) {
            case (byte) 0:
                $m$0();
                return;
            case (byte) 1:
                $m$1();
                return;
            default:
                throw new AssertionError();
        }
    }
}
