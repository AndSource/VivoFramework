package java.util.function;

import java.util.Objects;
import java.util.function.-$Lambda$iBcNfuYkNoKgH3GCUZob50qquB0.AnonymousClass1;

@FunctionalInterface
public interface LongPredicate {
    boolean test(long j);

    LongPredicate and(LongPredicate other) {
        Objects.requireNonNull(other);
        return new AnonymousClass1((byte) 0, this, other);
    }

    /* renamed from: lambda$-java_util_function_LongPredicate_2838 */
    /* synthetic */ boolean m98lambda$-java_util_function_LongPredicate_2838(LongPredicate other, long value) {
        return test(value) ? other.test(value) : false;
    }

    /* renamed from: lambda$-java_util_function_LongPredicate_3144 */
    /* synthetic */ boolean m99lambda$-java_util_function_LongPredicate_3144(long value) {
        return test(value) ^ 1;
    }

    LongPredicate negate() {
        return new -$Lambda$iBcNfuYkNoKgH3GCUZob50qquB0(this);
    }

    LongPredicate or(LongPredicate other) {
        Objects.requireNonNull(other);
        return new AnonymousClass1((byte) 1, this, other);
    }

    /* renamed from: lambda$-java_util_function_LongPredicate_4082 */
    /* synthetic */ boolean m100lambda$-java_util_function_LongPredicate_4082(LongPredicate other, long value) {
        return !test(value) ? other.test(value) : true;
    }
}
