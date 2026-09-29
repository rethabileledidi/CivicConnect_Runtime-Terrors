package com.civicconnect.util;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Consistent display formatting for all reports (South African Standard Time). */
public final class DisplayFormat {

    public static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private DisplayFormat() { }

    public static String dateTime(OffsetDateTime value) {
        return value == null ? "" : value.atZoneSameInstant(ZONE).format(DATE_TIME);
    }

    public static String number(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    public static String percent(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString() + "%";
    }
}
