package org.example.studio_admin;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SubscriptionPlanCatalogTest {

    @Test
    void checkoutUsesTheExistingPaidPlanPrices() {
        assertEquals(99, SubscriptionPlanCatalog.checkoutPriceRupees("1_MONTH"));
        assertEquals(249, SubscriptionPlanCatalog.checkoutPriceRupees("3_MONTH"));
        assertEquals(799, SubscriptionPlanCatalog.checkoutPriceRupees("1_YEAR"));
        assertNull(SubscriptionPlanCatalog.checkoutPriceRupees("7_DAY_TRIAL"));
    }

    @Test
    void everyPaidPlanStartsItsPaidTermAfterTheSameSevenDayTrial() {
        LocalDate paymentDate = LocalDate.of(2026, 1, 1);

        assertPlanDates("1_MONTH", paymentDate, LocalDate.of(2026, 1, 8), LocalDate.of(2026, 2, 8));
        assertPlanDates("3_MONTH", paymentDate, LocalDate.of(2026, 1, 8), LocalDate.of(2026, 4, 8));
        assertPlanDates("1_YEAR", paymentDate, LocalDate.of(2026, 1, 8), LocalDate.of(2027, 1, 8));
    }

    @Test
    void legacyTrialDatesRemainSupportedForAlreadyCreatedOrders() {
        LocalDate paymentDate = LocalDate.of(2026, 1, 1);
        SubscriptionPlanCatalog.Dates dates =
                SubscriptionPlanCatalog.dates("7_DAY_TRIAL", paymentDate);

        assertEquals(paymentDate, dates.trialStartDate());
        assertEquals(LocalDate.of(2026, 1, 8), dates.trialEndDate());
        assertEquals(paymentDate, dates.startDate());
        assertEquals(LocalDate.of(2026, 1, 8), dates.expiryDate());
    }

    private void assertPlanDates(
            String plan,
            LocalDate paymentDate,
            LocalDate expectedPaidStart,
            LocalDate expectedExpiry
    ) {
        SubscriptionPlanCatalog.Dates dates = SubscriptionPlanCatalog.dates(plan, paymentDate);
        assertEquals(paymentDate, dates.trialStartDate());
        assertEquals(expectedPaidStart, dates.trialEndDate());
        assertEquals(expectedPaidStart, dates.startDate());
        assertEquals(expectedExpiry, dates.expiryDate());
    }
}
