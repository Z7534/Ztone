package de.Z7534.ztone.stoneattack.util;

import java.text.DecimalFormat;

/** Kleine Zahlenformatierungs-Helfer. */
public final class FormatUtil {

    private FormatUtil() {
    }

    public static String round2(double value) {
        DecimalFormat df = new DecimalFormat("0.00");
        return df.format(value);
    }

    public static String applyMoneyFormat(double amount, String pattern) {
        DecimalFormat df = new DecimalFormat(pattern);
        return df.format(amount);
    }
}
