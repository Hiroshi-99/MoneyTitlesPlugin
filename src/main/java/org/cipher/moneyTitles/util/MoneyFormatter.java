package org.cipher.moneyTitles.util;

import java.text.DecimalFormat;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for formatting money amounts using different formats and
 * abbreviations.
 * Thread-safe and optimized for high performance.
 */
public class MoneyFormatter {
    private final DecimalFormat moneyFormat;
    private final boolean formatQuadrillionEnabled;
    private final String formatQuadrillionSuffix;
    private final boolean formatTrillionEnabled;
    private final String formatTrillionSuffix;
    private final boolean formatBillionEnabled;
    private final String formatBillionSuffix;
    private final boolean formatMillionEnabled;
    private final String formatMillionSuffix;
    private final boolean formatThousandEnabled;
    private final String formatThousandSuffix;
    private final int decimalPlaces;

    // Cache for formatted values to minimize string formatting operations
    private final ConcurrentHashMap<Double, String> formatCache = new ConcurrentHashMap<>(64);

    // Constants for improved readability and performance
    private static final double QUADRILLION = 1_000_000_000_000_000D;
    private static final double TRILLION = 1_000_000_000_000D;
    private static final double BILLION = 1_000_000_000D;
    private static final double MILLION = 1_000_000D;
    private static final double THOUSAND = 1_000D;

    /**
     * Create a new MoneyFormatter with specified settings.
     *
     * @param decimalPlaces  Number of decimal places to show
     * @param formatSettings Map of format settings from config
     */
    public MoneyFormatter(int decimalPlaces,
            boolean quadrillionEnabled, String quadrillionSuffix,
            boolean trillionEnabled, String trillionSuffix,
            boolean billionEnabled, String billionSuffix,
            boolean millionEnabled, String millionSuffix,
            boolean thousandEnabled, String thousandSuffix) {
        this.decimalPlaces = decimalPlaces;
        this.moneyFormat = new DecimalFormat("#,##0" + (decimalPlaces > 0 ? "." + "0".repeat(decimalPlaces) : ""));

        this.formatQuadrillionEnabled = quadrillionEnabled;
        this.formatQuadrillionSuffix = quadrillionSuffix;
        this.formatTrillionEnabled = trillionEnabled;
        this.formatTrillionSuffix = trillionSuffix;
        this.formatBillionEnabled = billionEnabled;
        this.formatBillionSuffix = billionSuffix;
        this.formatMillionEnabled = millionEnabled;
        this.formatMillionSuffix = millionSuffix;
        this.formatThousandEnabled = thousandEnabled;
        this.formatThousandSuffix = thousandSuffix;
    }

    /**
     * Format a money amount according to the configured settings.
     * Uses a cache to improve performance for frequently formatted values.
     *
     * @param amount The amount to format
     * @return Formatted money string
     */
    public String formatMoney(double amount) {
        try {
            // Handle case where amount is NaN or infinite
            if (Double.isNaN(amount) || Double.isInfinite(amount)) {
                return "0";
            }

            // Round to the specified number of decimal places to improve cache hits
            double roundedAmount = Math.round(amount * Math.pow(10, decimalPlaces)) / Math.pow(10, decimalPlaces);

            // Check cache first
            String cached = formatCache.get(roundedAmount);
            if (cached != null) {
                return cached;
            }

            // Format the amount
            String result;
            boolean isNegative = roundedAmount < 0;
            double absAmount = Math.abs(roundedAmount);

            if (absAmount >= QUADRILLION && formatQuadrillionEnabled) {
                result = formatLargeNumber(absAmount, QUADRILLION, formatQuadrillionSuffix);
            } else if (absAmount >= TRILLION && formatTrillionEnabled) {
                result = formatLargeNumber(absAmount, TRILLION, formatTrillionSuffix);
            } else if (absAmount >= BILLION && formatBillionEnabled) {
                result = formatLargeNumber(absAmount, BILLION, formatBillionSuffix);
            } else if (absAmount >= MILLION && formatMillionEnabled) {
                result = formatLargeNumber(absAmount, MILLION, formatMillionSuffix);
            } else if (absAmount >= THOUSAND && formatThousandEnabled) {
                result = formatLargeNumber(absAmount, THOUSAND, formatThousandSuffix);
            } else {
                result = moneyFormat.format(absAmount);
            }

            // Add negative sign if needed
            if (isNegative) {
                result = "-" + result;
            }

            // Cache the result (limit cache size to prevent memory issues)
            if (formatCache.size() < 5000) {
                formatCache.put(roundedAmount, result);
            }

            return result;
        } catch (Exception e) {
            // Fallback in case of formatting error
            return String.format("%.2f", amount);
        }
    }

    /**
     * Clear the formatting cache.
     */
    public void clearCache() {
        formatCache.clear();
    }

    /**
     * Format a large number with the specified divisor and suffix.
     *
     * @param amount  The amount to format
     * @param divisor The divisor to use
     * @param suffix  The suffix to append
     * @return Formatted string
     */
    private String formatLargeNumber(double amount, double divisor, String suffix) {
        double value = amount / divisor;

        if (decimalPlaces == 0 || value == Math.floor(value)) {
            return ((int) value) + suffix;
        } else {
            return moneyFormat.format(value) + suffix;
        }
    }
}