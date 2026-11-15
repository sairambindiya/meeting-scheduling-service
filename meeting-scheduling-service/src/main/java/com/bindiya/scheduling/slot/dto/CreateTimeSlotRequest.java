package com.bindiya.scheduling.slot.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateTimeSlotRequest(
        @NotNull Instant startTime,
        @NotNull Instant endTime
) {
}