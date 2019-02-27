package java.time.temporal;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.chrono.Chronology;

public final class TemporalQueries {
    static final TemporalQuery<Chronology> CHRONO = -$Lambda$Tdj786c1ErPPulwwPKXine_EWQE.$INST$1;
    static final TemporalQuery<LocalDate> LOCAL_DATE = -$Lambda$Tdj786c1ErPPulwwPKXine_EWQE.$INST$5;
    static final TemporalQuery<LocalTime> LOCAL_TIME = -$Lambda$Tdj786c1ErPPulwwPKXine_EWQE.$INST$6;
    static final TemporalQuery<ZoneOffset> OFFSET = -$Lambda$Tdj786c1ErPPulwwPKXine_EWQE.$INST$3;
    static final TemporalQuery<TemporalUnit> PRECISION = -$Lambda$Tdj786c1ErPPulwwPKXine_EWQE.$INST$2;
    static final TemporalQuery<ZoneId> ZONE = -$Lambda$Tdj786c1ErPPulwwPKXine_EWQE.$INST$4;
    static final TemporalQuery<ZoneId> ZONE_ID = -$Lambda$Tdj786c1ErPPulwwPKXine_EWQE.$INST$0;

    private TemporalQueries() {
    }

    public static TemporalQuery<ZoneId> zoneId() {
        return ZONE_ID;
    }

    public static TemporalQuery<Chronology> chronology() {
        return CHRONO;
    }

    public static TemporalQuery<TemporalUnit> precision() {
        return PRECISION;
    }

    public static TemporalQuery<ZoneId> zone() {
        return ZONE;
    }

    public static TemporalQuery<ZoneOffset> offset() {
        return OFFSET;
    }

    public static TemporalQuery<LocalDate> localDate() {
        return LOCAL_DATE;
    }

    public static TemporalQuery<LocalTime> localTime() {
        return LOCAL_TIME;
    }

    /* renamed from: lambda$-java_time_temporal_TemporalQueries_16950 */
    static /* synthetic */ ZoneOffset m44lambda$-java_time_temporal_TemporalQueries_16950(TemporalAccessor temporal) {
        if (temporal.isSupported(ChronoField.OFFSET_SECONDS)) {
            return ZoneOffset.ofTotalSeconds(temporal.get(ChronoField.OFFSET_SECONDS));
        }
        return null;
    }

    /* renamed from: lambda$-java_time_temporal_TemporalQueries_17282 */
    static /* synthetic */ ZoneId m45lambda$-java_time_temporal_TemporalQueries_17282(TemporalAccessor temporal) {
        ZoneId zone = (ZoneId) temporal.query(ZONE_ID);
        return zone != null ? zone : (ZoneId) temporal.query(OFFSET);
    }

    /* renamed from: lambda$-java_time_temporal_TemporalQueries_17553 */
    static /* synthetic */ LocalDate m46lambda$-java_time_temporal_TemporalQueries_17553(TemporalAccessor temporal) {
        if (temporal.isSupported(ChronoField.EPOCH_DAY)) {
            return LocalDate.ofEpochDay(temporal.getLong(ChronoField.EPOCH_DAY));
        }
        return null;
    }

    /* renamed from: lambda$-java_time_temporal_TemporalQueries_17862 */
    static /* synthetic */ LocalTime m47lambda$-java_time_temporal_TemporalQueries_17862(TemporalAccessor temporal) {
        if (temporal.isSupported(ChronoField.NANO_OF_DAY)) {
            return LocalTime.ofNanoOfDay(temporal.getLong(ChronoField.NANO_OF_DAY));
        }
        return null;
    }
}
