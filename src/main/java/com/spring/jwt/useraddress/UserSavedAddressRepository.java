package com.spring.jwt.useraddress;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserSavedAddressRepository extends JpaRepository<UserSavedAddress, Long> {

    List<UserSavedAddress> findByUserIdOrderByDefaultAddressDescUpdatedAtDesc(Long userId);

    Optional<UserSavedAddress> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}
