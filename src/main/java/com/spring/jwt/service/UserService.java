package com.spring.jwt.service;

import com.spring.jwt.dto.ResetPassword;
import com.spring.jwt.dto.UserDTO;
import com.spring.jwt.dto.UserUpdateRequest;
import com.spring.jwt.exception.UserNotFoundExceptions;
import com.spring.jwt.utils.BaseResponseDTO;
import com.spring.jwt.utils.ResponseDto;
import org.springframework.data.domain.Page;

public interface UserService {
    BaseResponseDTO registerAccount(UserDTO userDTO);

    ResponseDto forgotPass(String email, String resetPasswordLink, String domain) throws UserNotFoundExceptions;

    ResponseDto handleForgotPassword(String email, String domain);

    void updateResetPassword(String token, String email);

    boolean validateResetToken(String token);

    boolean isSameAsOldPassword(String token, String newPassword);

    ResponseDto updatePassword(String token, String newPassword);

    ResponseDto processPasswordUpdate(ResetPassword resetRequest);

    Page<UserDTO> getAllUsers(int pageNo, int pageSize);

    UserDTO getUserById(Long id);

    UserDTO updateUser(Long id, UserUpdateRequest request);
}
