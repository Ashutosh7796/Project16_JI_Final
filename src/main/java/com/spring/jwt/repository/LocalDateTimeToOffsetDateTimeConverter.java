package com.spring.jwt.repository;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Converter(autoApply = true)
public class LocalDateTimeToOffsetDateTimeConverter implements AttributeConverter<LocalDateTime, OffsetDateTime> {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Kolkata");

    @Override
    public OffsetDateTime convertToDatabaseColumn(LocalDateTime localDateTime) {
        return localDateTime == null ? null : localDateTime.atZone(ZONE_ID).toOffsetDateTime();
    }

    @Override
    public LocalDateTime convertToEntityAttribute(OffsetDateTime offsetDateTime) {
        return offsetDateTime == null ? null : offsetDateTime.atZoneSameInstant(ZONE_ID).toLocalDateTime();
    }
}
