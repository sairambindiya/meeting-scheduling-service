package com.bindiya.scheduling.slot;

import com.bindiya.scheduling.slot.dto.AvailabilityResponse;
import com.bindiya.scheduling.slot.dto.CreateTimeSlotRequest;
import com.bindiya.scheduling.slot.dto.TimeSlotResponse;
import com.bindiya.scheduling.slot.dto.UpdateTimeSlotRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/users/{userId}")
public class TimeSlotController {

    private final TimeSlotService slotService;

    public TimeSlotController(TimeSlotService slotService) {
        this.slotService = slotService;
    }

    @PostMapping("/slots")
    public ResponseEntity<TimeSlotResponse> create(
            @PathVariable("userId") Long userId,
            @Valid @RequestBody CreateTimeSlotRequest request
    ) {
        TimeSlotResponse response = slotService.create(
                userId,
                request.startTime(),
                request.endTime()
        );

        URI location = URI.create(
                "/users/" + userId + "/slots/" + response.id()
        );

        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/slots/{slotId}")
    public TimeSlotResponse update(
            @PathVariable("userId") Long userId,
            @PathVariable("slotId") Long slotId,
            @Valid @RequestBody UpdateTimeSlotRequest request
    ) {
        return slotService.update(
                userId,
                slotId,
                request.startTime(),
                request.endTime(),
                request.status()
        );
    }

    @DeleteMapping("/slots/{slotId}")
    public ResponseEntity<Void> delete(
            @PathVariable("userId") Long userId,
            @PathVariable("slotId") Long slotId
    ) {
        slotService.delete(userId, slotId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/slots")
    public List<TimeSlotResponse> list(
            @PathVariable("userId") Long userId,
            @RequestParam("from") Instant from,
            @RequestParam("to") Instant to,
            @RequestParam(value = "status", required = false) SlotStatus status
    ) {
        return slotService.list(userId, from, to, status);
    }

    @GetMapping("/availability")
    public AvailabilityResponse availability(
            @PathVariable("userId") Long userId,
            @RequestParam("from") Instant from,
            @RequestParam("to") Instant to,
            @RequestParam(value = "status", required = false) SlotStatus status
    ) {
        return slotService.availability(userId, from, to, status);
    }
}