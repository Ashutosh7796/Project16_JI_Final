package com.spring.jwt.useraddress;

import com.spring.jwt.service.security.UserDetailsCustom;
import com.spring.jwt.useraddress.dto.SavedAddressResponse;
import com.spring.jwt.useraddress.dto.UpsertSavedAddressRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v2/user/addresses")
@RequiredArgsConstructor
public class UserSavedAddressController {

    private final UserSavedAddressService userSavedAddressService;

    @GetMapping
    public List<SavedAddressResponse> list() {
        return userSavedAddressService.list(currentUserId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavedAddressResponse create(@Valid @RequestBody UpsertSavedAddressRequest body) {
        return userSavedAddressService.create(currentUserId(), body);
    }

    @PutMapping("/{id}")
    public SavedAddressResponse update(@PathVariable Long id, @Valid @RequestBody UpsertSavedAddressRequest body) {
        return userSavedAddressService.update(currentUserId(), id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        userSavedAddressService.delete(currentUserId(), id);
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetailsCustom userDetails) {
            return userDetails.getUserId();
        }
        throw new AccessDeniedException("Authenticated user is required");
    }
}
