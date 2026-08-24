package io.radius.user.config;

import io.radius.user.domain.UserAccount;
import io.radius.user.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Promotes one phone number to ADMIN on start-up.
 *
 * There is deliberately no "make me an admin" endpoint: the first administrator
 * has to come from somewhere the application cannot be talked into. Set
 * RADIUS_ADMIN_PHONE in the environment; the account is promoted the next time
 * the service starts, and takes effect on their next sign-in.
 */
@Component
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final String adminPhone;

    public AdminBootstrap(UserRepository users, @Value("${radius.admin-phone:}") String adminPhone) {
        this.users = users;
        this.adminPhone = adminPhone;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void promoteConfiguredAdmin() {
        if (adminPhone == null || adminPhone.isBlank()) {
            log.info("no radius.admin-phone set - nobody can review verifications yet");
            return;
        }
        users.findByPhone(adminPhone.trim()).ifPresentOrElse(user -> {
            if ("ADMIN".equals(user.getRole())) {
                log.info("admin already set: {}", user.getId());
                return;
            }
            user.promoteToAdmin();
            users.save(user);
            log.info("promoted {} to ADMIN - they get the role on their next sign-in", user.getId());
        }, () -> log.info("admin phone {} has not signed up yet; will promote once they do", adminPhone));
    }
}
