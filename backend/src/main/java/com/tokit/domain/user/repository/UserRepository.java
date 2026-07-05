package com.tokit.domain.user.repository;

import com.tokit.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByWalletAddress(String walletAddress);
    Optional<User> findByWalletAddressIgnoreCase(String walletAddress);
}
