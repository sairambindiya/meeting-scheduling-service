package com.bindiya.scheduling.meeting.dto;

import com.bindiya.scheduling.meeting.Meeting;
import com.bindiya.scheduling.user.User;

import java.time.Instant;
import java.util.List;

public record MeetingResponse(
        Long meetingId,
        Long slotId,
        String title,
        String description,
        Instant startTime,
        Instant endTime,
        List<Long> participantIds,
        String meetingUrl
) {

    public static MeetingResponse from(Meeting meeting) {
        return new MeetingResponse(
                meeting.getId(),
                meeting.getSlot().getId(),
                meeting.getTitle(),
                meeting.getDescription(),
                meeting.getSlot().getStartTime(),
                meeting.getSlot().getEndTime(),
                meeting.getParticipants().stream()
                        .map(User::getId)
                        .sorted()
                        .toList(),
                "https://bindiya.com/meetings/" + meeting.getId()
        );
    }
}