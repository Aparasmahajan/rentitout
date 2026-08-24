package io.radius.listing.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Persistence wiring lives here rather than on the application class so a
 * {@code @WebMvcTest} slice stays a slice: the slice's component filter skips plain
 * {@code @Configuration} classes, so it no longer tries to build an EntityManager it
 * has no database for.
 *
 * The extra package is the shared outbox table, which every publishing service
 * owns a copy of.
 */
@Configuration
@EntityScan({"io.radius.listing", "io.radius.common.outbox"})
@EnableJpaRepositories({"io.radius.listing", "io.radius.common.outbox"})
public class PersistenceConfig {
}
