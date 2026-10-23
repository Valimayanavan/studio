package org.example.studio_admin;

import java.time.LocalDate;
import java.time.Period;

final class SubscriptionPlanCatalog {

    private static final int FREE_TRIAL_DAYS = 7;

    private SubscriptionPlanCatalog() {
    }

    static Integer checkoutPriceRupees(String plan) {
        return switch (plan) {
            case "1_MONTH" -> 99;
            case "3_MONTH" -> 249;
            case "1_YEAR" -> 799;
            default -> null;
        };
    }

    static Dates dates(String plan, LocalDate paymentDate) {
        Period paidPeriod = switch (plan) {
            case "1_MONTH" -> Period.ofMonths(1);
            case "3_MONTH" -> Period.ofMonths(3);
            case "1_YEAR" -> Period.ofYears(1);
            case "3_DAY_TRIAL" -> Period.ofDays(3);
            case "7_DAY_TRIAL" -> Period.ofDays(7);
            case "14_DAY_TRIAL" -> Period.ofDays(14);
            default -> null;
        };
        if (paidPeriod == null) {
            return null;
        }

        if (checkoutPriceRupees(plan) == null) {
            LocalDate endDate = paymentDate.plus(paidPeriod);
            return new Dates(paymentDate, endDate, paymentDate, endDate);
        }

        LocalDate trialEndDate = paymentDate.plusDays(FREE_TRIAL_DAYS);
        LocalDate expiryDate = trialEndDate.plus(paidPeriod);
        return new Dates(paymentDate, trialEndDate, trialEndDate, expiryDate);
    }

    record Dates(
            LocalDate trialStartDate,
            LocalDate trialEndDate,
            LocalDate startDate,
            LocalDate expiryDate
    ) {
    }
}
