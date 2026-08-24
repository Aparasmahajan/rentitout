package io.radius.user.repo;

import io.radius.user.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByPhone(String phone);

    boolean existsByPhone(String phone);
}
