package io.radius.common.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeoTest {

    @Test
    void rounds_a_home_point_to_roughly_a_hundred_metres() {
        double lat = 52.4996123;
        double lon = 13.4180456;

        double roundedLat = Geo.roundLat(lat);
        double roundedLon = Geo.roundLon(lon, lat);

        double drift = Geo.distanceMetres(lat, lon, roundedLat, roundedLon);
        assertThat(drift).isLessThan(80d);
        assertThat(roundedLat).isNotEqualTo(lat);
    }

    @Test
    void rounding_is_stable() {
        double first = Geo.roundLat(52.4996123);
        double second = Geo.roundLat(52.4996124);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void distance_between_kreuzberg_and_friedrichshain_is_a_few_kilometres() {
        double metres = Geo.distanceMetres(52.4996, 13.4180, 52.5150, 13.4540);
        assertThat(metres).isBetween(2_500d, 4_500d);
    }
}
