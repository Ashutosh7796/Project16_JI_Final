package com.spring.jwt.repository;

import com.spring.jwt.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    User findByEmail(String email);

    User findByResetPasswordToken(String token);

    Optional<User> findByMobileNumber(@Param("mobileNumber") Long mobileNumber);
    
    @Query(value = "SELECT * FROM users WHERE user_id = :id", nativeQuery = true)
    Map<String, Object> findRawUserById(@Param("id") Long id);


    List<User> findAllByStatusTrue();

    boolean existsByUserIdAndAccountLockedTrue(Integer userId);

    @Query("""
        SELECT DISTINCT u FROM User u
        JOIN u.roles r
        WHERE r.name = :roleName
    """)
    Page<User> findAllByRoleName(
            @Param("roleName") String roleName,
            Pageable pageable
    );

    @Query("""
        SELECT DISTINCT u FROM User u
        JOIN u.roles r
        WHERE r.name IN ('SURVEYOR', 'LAB_TECHNICIAN')
    """)
    Page<User> findAllEmployees(Pageable pageable);

    @Query("""
        SELECT COUNT(DISTINCT u.userId) FROM User u
        JOIN u.roles r
        WHERE r.name IN ('SURVEYOR', 'LAB_TECHNICIAN', 'MANAGER')
          AND (u.status IS NULL OR u.status = true)
        """)
    long countActiveStaffUsers();
}
