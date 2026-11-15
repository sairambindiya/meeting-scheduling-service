package com.bindiya.scheduling.slot.dto;

import com.bindiya.scheduling.slot.SlotStatus;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record UpdateTimeSlotRequest(
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        @NotNull SlotStatus status
) {
}