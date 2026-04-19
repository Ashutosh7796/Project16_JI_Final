package com.spring.jwt.useraddress;

import com.spring.jwt.exception.ResourceNotFoundException;
import com.spring.jwt.useraddress.dto.SavedAddressResponse;
import com.spring.jwt.useraddress.dto.UpsertSavedAddressRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserSavedAddressService {

    private final UserSavedAddressRepository repository;

    @Transactional(readOnly = true)
    public List<SavedAddressResponse> list(Long userId) {
        return repository.findByUserIdOrderByDefaultAddressDescUpdatedAtDesc(userId).stream()
                .map(UserSavedAddressService::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public SavedAddressResponse create(Long userId, UpsertSavedAddressRequest req) {
        if (req.isDefaultAddress()) {
            clearDefaultForUser(userId);
        }
        UserSavedAddress row = UserSavedAddress.builder()
                .userId(userId)
                .label(trimToNull(req.getLabel()))
                .fullName(req.getFullName().trim())
                .phone(req.getPhone().trim())
                .addressLine1(req.getAddressLine1().trim())
                .addressLine2(trimToNull(req.getAddressLine2()))
                .city(req.getCity().trim())
                .state(req.getState().trim())
                .pincode(req.getPincode().trim())
                .country(countryOrDefault(req.getCountry()))
                .defaultAddress(req.isDefaultAddress())
                .build();
        return toResponse(repository.save(row));
    }

    @Transactional
    public SavedAddressResponse update(Long userId, Long id, UpsertSavedAddressRequest req) {
        UserSavedAddress row = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        if (req.isDefaultAddress()) {
            clearDefaultForUserExcept(userId, id);
        }
        row.setLabel(trimToNull(req.getLabel()));
        row.setFullName(req.getFullName().trim());
        row.setPhone(req.getPhone().trim());
        row.setAddressLine1(req.getAddressLine1().trim());
        row.setAddressLine2(trimToNull(req.getAddressLine2()));
        row.setCity(req.getCity().trim());
        row.setState(req.getState().trim());
        row.setPincode(req.getPincode().trim());
        row.setCountry(countryOrDefault(req.getCountry()));
        row.setDefaultAddress(req.isDefaultAddress());
        return toResponse(repository.save(row));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        UserSavedAddress row = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        repository.delete(row);
    }

    @Transactional(readOnly = true)
    public UserSavedAddress requireOwned(Long userId, Long addressId) {
        return repository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Saved address not found"));
    }

    private void clearDefaultForUser(Long userId) {
        List<UserSavedAddress> all = repository.findByUserIdOrderByDefaultAddressDescUpdatedAtDesc(userId);
        for (UserSavedAddress a : all) {
            if (a.isDefaultAddress()) {
                a.setDefaultAddress(false);
                repository.save(a);
            }
        }
    }

    private void clearDefaultForUserExcept(Long userId, Long keepId) {
        List<UserSavedAddress> all = repository.findByUserIdOrderByDefaultAddressDescUpdatedAtDesc(userId);
        for (UserSavedAddress a : all) {
            if (a.isDefaultAddress() && !a.getId().equals(keepId)) {
                a.setDefaultAddress(false);
                repository.save(a);
            }
        }
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String countryOrDefault(String c) {
        String t = trimToNull(c);
        return t != null ? t : "India";
    }

    private static SavedAddressResponse toResponse(UserSavedAddress a) {
        return SavedAddressResponse.builder()
                .id(a.getId())
                .label(a.getLabel())
                .fullName(a.getFullName())
                .phone(a.getPhone())
                .addressLine1(a.getAddressLine1())
                .addressLine2(a.getAddressLine2())
                .city(a.getCity())
                .state(a.getState())
                .pincode(a.getPincode())
                .country(a.getCountry())
                .defaultAddress(a.isDefaultAddress())
                .build();
    }
}
