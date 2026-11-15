package com.bindiya.scheduling.meeting;

import com.bindiya.scheduling.meeting.dto.CreateMeetingRequest;
import com.bindiya.scheduling.meeting.dto.MeetingResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users/{userId}/slots/{slotId}/meeting")
public class MeetingController {

    private final MeetingService meetingService;

    public MeetingController(MeetingService meetingService) {
        this.meetingService = meetingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MeetingResponse book(
            @PathVariable("userId") Long userId,
            @PathVariable("slotId") Long slotId,
            @Valid @RequestBody CreateMeetingRequest request
    ) {
        return meetingService.book(
                userId,
                slotId,
                request.title(),
                request.description(),
                request.participantIds()
        );
    }
}