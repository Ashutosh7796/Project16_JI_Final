package com.spring.jwt.utils;

import com.spring.jwt.service.security.UserDetailsCustom;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public final class SecurityUtil
{
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            throw new IllegalStateException("SecurityContext does not contain an authentication object. User may not be logged in.");
        }

        if (!authentication.isAuthenticated()) {
            throw new IllegalStateException("User is not authenticated. Please login again.");
        }

        Object principal = authentication.getPrincipal();

        if (principal == null || principal.equals("anonymousUser")) {
            throw new IllegalStateException("No authenticated principal found. Please login to access this resource.");
        }

        if (principal instanceof UserDetailsCustom customUser) {
            Long id = customUser.getUserId();
            if (id == null) {
                throw new IllegalStateException("Authenticated user does not have a valid user ID. Please contact support.");
            }
            return id;
        }

        if (principal instanceof String username) {
            throw new IllegalStateException(
                    "Authentication principal is a String (" + username + ") instead of UserDetailsCustom. " +
                    "This indicates a JWT authentication configuration issue. Please logout and login again."
            );
        }

        if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            throw new IllegalStateException(
                    "Principal is a Spring UserDetails (" + userDetails.getClass().getSimpleName() + 
                    ") but not UserDetailsCustom. The JWT filter should load UserDetailsCustom objects."
            );
        }

        throw new IllegalStateException(
                "Unexpected principal type: " + principal.getClass().getName() + 
                ". Expected UserDetailsCustom but got " + principal.getClass().getSimpleName() + 
                ". This indicates an authentication configuration issue."
        );


    }

}
