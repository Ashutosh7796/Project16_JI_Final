package com.spring.jwt.dto;

import lombok.Data;

import java.util.List;

@Data
public class ResponseAllUsersDto {

    private String message;
    private List<UserDTO> list;
    private String exception;
    private int pageSize;
    private int totalPages;
    private long totalElements;
    private int currentPage;
    private boolean first;
    private boolean last;

    public ResponseAllUsersDto(String message) {
        this.message = message;
    }
    
    public ResponseAllUsersDto(String message, List<UserDTO> list) {
        this.message = message;
        this.list = list;
    }
}