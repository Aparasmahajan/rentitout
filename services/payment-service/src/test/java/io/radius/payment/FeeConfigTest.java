package io.radius.payment;

import io.radius.payment.domain.FeeConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fee is the one number in the product that must never be off by a cent.
 * These cases are the ones an accountant would ask about.
 */
class FeeConfigTest {

    private FeeConfig config(int percentBps, Long capMinor, long minFeeMinor) throws Exception {
        FeeConfig config = newInstance();
        set(config, "percentBps", percentBps);
        set(config, "capMinor", capMinor);
        set(config, "minFeeMinor", minFeeMinor);
        return config;
    }

    @Test
    void the_default_configuration_charges_nothing() throws Exception {
        assertThat(config(0, null, 0).feeFor(100_00)).isZero();
    }

    @Test
    void a_percentage_is_basis_points_of_the_amount() throws Exception {
        // 2.5% of 100.00 is 2.50
        assertThat(config(250, null, 0).feeFor(100_00)).isEqualTo(250);
    }

    @Test
    void the_cap_wins_over_the_percentage() throws Exception {
        assertThat(config(250, 500L, 0).feeFor(1_000_00)).isEqualTo(500);
    }

    @Test
    void the_minimum_wins_on_a_small_amount() throws Exception {
        assertThat(config(250, null, 100).feeFor(1_00)).isEqualTo(100);
    }

    @Test
    void rounding_never_invents_a_cent() throws Exception {
        // 2.5% of 0.01 is 0.00025 — integer division floors it, we never round up.
        assertThat(config(250, null, 0).feeFor(1)).isZero();
    }

    private static FeeConfig newInstance() throws Exception {
        var constructor = FeeConfig.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static void set(FeeConfig config, String field, Object value) throws Exception {
        Field f = FeeConfig.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(config, value);
    }
}
