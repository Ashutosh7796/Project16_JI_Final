package com.spring.jwt.service.security;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@NoArgsConstructor
@Data
public class UserDetailsCustom implements UserDetails {

    private String username;
    @lombok.ToString.Exclude
    private String password;
    private String firstName;
    private Long userId;
    private Integer userProfileId;

    private List<GrantedAuthority> authorities;

    public UserDetailsCustom(
            String username,
            String password,
            String firstName,
            Long userId,
            Integer userProfileId,
            List<GrantedAuthority> authorities) {
        this.username = username;
        this.password = password;
        this.firstName = firstName;
        this.userId = userId;
        this.userProfileId = userProfileId;
        this.authorities = authorities;
    }

    public UserDetailsCustom(
            String username,
            String password,
            String firstName,
            Long userId,
            List<GrantedAuthority> authorities) {
        this(username, password, firstName, userId, null, authorities);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    public String getFirstName() {
        return firstName;
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
