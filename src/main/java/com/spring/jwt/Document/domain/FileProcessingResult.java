package com.spring.jwt.Document.domain;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Data
@Builder
public class FileProcessingResult {

    @NonNull
    private final byte[] processedData;

    @NonNull
    private final Long originalSize;

    @NonNull
    private final Long processedSize;

    @NonNull
    private final String processingType;

    private final Float compressionRatio;

    private final String processingDetails;

    /**
     * Calculate compression ratio (processed size / original size)
     *
     * @return compression ratio as a float (e.g., 0.5 means 50% of original size)
     */
    public Float getCompressionRatio()
    {
        if (compressionRatio != null)
        {
            return compressionRatio;
        }
        if (originalSize == 0) {
            return 1.0f;
        }
        return (float) processedSize / originalSize;
    }

    /**
     * Get compression percentage
     *
     * @return compression percentage (e.g., 50.0 means 50% size reduction)
     */
    public Float getCompressionPercentage()
    {
        return (1 - getCompressionRatio()) * 100;
    }

    /**
     * Check if compression was effective (reduced size by at least 10%)
     *
     * @return true if compression reduced file size by at least 10%
     */
    public boolean isCompressionEffective()
    {
        return getCompressionRatio() < 0.9f;
    }

    /**
     * Check if compression was highly effective (reduced size by at least 50%)
     *
     * @return true if compression reduced file size by at least 50%
     */
    public boolean isHighlyEffective()
    {
        return getCompressionRatio() < 0.5f;
    }

    /**
     * Get human-readable processing summary for logging and monitoring
     *
     * @return formatted summary string with processing details
     */
    public String getProcessingSummary() {
        return String.format("%s: %s → %s (%.1f%% reduction)",
                processingType,
                formatBytes(originalSize),
                formatBytes(processedSize),
                getCompressionPercentage());
    }

    /**
     * Get detailed processing report including all metrics
     *
     * @return comprehensive processing report
     */
    public String getDetailedReport()
    {
        StringBuilder report = new StringBuilder();
        report.append("Processing Report:\n");
        report.append(String.format("  Type: %s\n", processingType));
        report.append(String.format("  Original Size: %s\n", formatBytes(originalSize)));
        report.append(String.format("  Processed Size: %s\n", formatBytes(processedSize)));
        report.append(String.format("  Compression Ratio: %.3f\n", getCompressionRatio()));
        report.append(String.format("  Size Reduction: %.1f%%\n", getCompressionPercentage()));
        report.append(String.format("  Effective: %s\n", isCompressionEffective() ? "Yes" : "No"));
        if (processingDetails != null) {
            report.append(String.format("  Details: %s\n", processingDetails));
        }
        return report.toString();
    }

    /**
     * @param bytes the number of bytes
     * @return formatted string (e.g., "1.2 MB", "345 KB")
     */
    private String formatBytes(long bytes)
    {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
}