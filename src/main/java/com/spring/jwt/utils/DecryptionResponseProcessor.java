package com.spring.jwt.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.jwt.dto.ResponseAllUsersDto;
import com.spring.jwt.dto.UserDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.List;
import java.util.Map;

/**
 * This class intercepts responses from USER controllers only and ensures sensitive
 * data is decrypted.
 * 
 * IMPORTANT: Only applies to controllers in the specified base packages.
 * This prevents unnecessary processing of non-user related responses.
 */
@ControllerAdvice(basePackages = {
    "com.spring.jwt.controller"  // Only user-related controllers
})
@RequiredArgsConstructor
@Slf4j
public class DecryptionResponseProcessor implements ResponseBodyAdvice<Object> {

    private final EncryptionUtil encryptionUtil;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // Only process if the response contains User-related data
        Class<?> returnTypeClass = returnType.getParameterType();
        
        // Check if return type is User-related
        boolean isUserRelated = 
            UserDTO.class.isAssignableFrom(returnTypeClass) ||
            ResponseAllUsersDto.class.isAssignableFrom(returnTypeClass) ||
            returnTypeClass.getName().contains("UserDTO") ||
            returnTypeClass.getName().contains("ResponseAllUsersDto");
        
        if (isUserRelated) {
            log.debug("DecryptionResponseProcessor will process response of type: {}", returnTypeClass.getName());
        }
        
        return isUserRelated;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request, ServerHttpResponse response) {

        try {
            log.debug("Processing user response for decryption: {}", body.getClass().getName());
            return processResponse(body);
        } catch (Exception e) {
            log.error("Error processing response for decryption: {}", e.getMessage(), e);
            return body;
        }
    }

    private Object processResponse(Object body) {
        if (body == null) {
            return null;
        }

        // Skip non-user related responses
        String className = body.getClass().getName();
        if (className.contains("DocumentResponseDTO") ||
            className.contains("DocumentDetailResponseDTO") ||
            className.contains("PaginatedDocumentResponseDTO") ||
            className.contains("EmployeeFarmerSurvey") ||
            className.contains("BaseResponseDTO")) {
            return body;
        }

        // Handle ApiResponse wrapper
        if (body instanceof ApiResponse) {
            ApiResponse<?> response = (ApiResponse<?>) body;
            Object data = response.getData();
            if (data != null) {
                String dataClassName = data.getClass().getName();
                if (dataClassName.contains("DocumentResponseDTO") ||
                    dataClassName.contains("DocumentDetailResponseDTO") ||
                    dataClassName.contains("PaginatedDocumentResponseDTO") ||
                    dataClassName.contains("EmployeeFarmerSurvey")) {
                    return body;
                }
            }
        }

        if (body instanceof ResponseAllUsersDto) {
            ResponseAllUsersDto responseDto = (ResponseAllUsersDto) body;
            if (responseDto.getList() != null) {
                log.debug("Processing ResponseAllUsersDto with {} items", responseDto.getList().size());
                for (UserDTO user : responseDto.getList()) {
                    decryptUserDTO(user);
                }
            }
            return body;
        }

        if (body instanceof UserDTO) {
            decryptUserDTO((UserDTO) body);
            return body;
        }

        if (body instanceof List<?>) {
            List<?> list = (List<?>) body;
            for (Object item : list) {
                if (item instanceof UserDTO) {
                    decryptUserDTO((UserDTO) item);
                }
            }
            return body;
        }

        if (body instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) body;
            for (Object value : map.values()) {
                if (value instanceof UserDTO) {
                    decryptUserDTO((UserDTO) value);
                } else if (value instanceof List) {
                    processResponse(value);
                } else if (value instanceof Map) {
                    processResponse(value);
                }
            }
            return body;
        }

        return body;
    }

    private void decryptUserDTO(UserDTO user) {
        // Decryption logic is currently commented out
        // Uncomment when encryption is fully implemented
        
        // try {
        //     if (user.getFirstName() != null && !user.getFirstName().isEmpty()) {
        //         user.setFirstName(encryptionUtil.decrypt(user.getFirstName()));
        //     }
        //
        //     if (user.getLastName() != null && !user.getLastName().isEmpty()) {
        //         user.setLastName(encryptionUtil.decrypt(user.getLastName()));
        //     }
        //
        //     if (user.getAddress() != null && !user.getAddress().isEmpty()) {
        //         user.setAddress(encryptionUtil.decrypt(user.getAddress()));
        //     }
        // } catch (Exception e) {
        //     log.error("Error decrypting user data: {}", e.getMessage());
        // }
    }
}
