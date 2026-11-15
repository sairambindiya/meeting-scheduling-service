package com.bindiya.scheduling.slot.dto;

import com.bindiya.scheduling.slot.SlotStatus;

import java.time.Instant;
import java.util.List;

public record AvailabilityResponse(
        Long userId,
        Instant from,
        Instant to,
        List<Interval> intervals
) {

    public record Interval(
            Instant startTime,
            Instant endTime,
            SlotStatus status
    ) {
    }
}