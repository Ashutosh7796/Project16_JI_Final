package com.spring.jwt.ProductPhoto;

/**
 * Internal-only projection carrying raw image bytes.
 * Used exclusively by the download endpoint — never serialised to JSON.
 */
public record ProductPhotoRawDTO(byte[] imageData, String contentType) {}
