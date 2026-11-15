package com.bindiya.scheduling.slot.dto;

import com.bindiya.scheduling.slot.SlotStatus;
import com.bindiya.scheduling.slot.TimeSlot;

import java.time.Instant;

public record TimeSlotResponse(
        Long id,
        Long userId,
        Instant startTime,
        Instant endTime,
        SlotStatus status
) {

    public static TimeSlotResponse from(TimeSlot slot) {
        return new TimeSlotResponse(
                slot.getId(),
                slot.getCalendar().getUser().getId(),
                slot.getStartTime(),
                slot.getEndTime(),
                slot.getStatus()
        );
    }
}