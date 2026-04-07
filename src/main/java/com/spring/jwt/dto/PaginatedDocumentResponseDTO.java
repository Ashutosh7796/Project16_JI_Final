package com.spring.jwt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Paginated response DTO for document listings
 * Provides comprehensive pagination information following REST API best practices
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginatedDocumentResponseDTO {
    
    /**
     * List of documents for the current page
     */
    private List<DocumentResponseDTO> documents;
    
    /**
     * Current page number (0-based)
     */
    private int currentPage;
    
    /**
     * Number of items per page
     */
    private int pageSize;
    
    /**
     * Total number of documents across all pages
     */
    private long totalElements;
    
    /**
     * Total number of pages
     */
    private int totalPages;
    
    /**
     * Whether this is the first page
     */
    private boolean first;
    
    /**
     * Whether this is the last page
     */
    private boolean last;
    
    /**
     * Whether there is a next page
     */
    private boolean hasNext;
    
    /**
     * Whether there is a previous page
     */
    private boolean hasPrevious;
    
    /**
     * Number of elements in the current page
     */
    private int numberOfElements;
    
    /**
     * Whether the current page is empty
     */
    private boolean empty;
}