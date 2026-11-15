package com.bindiya.scheduling.meeting;

import com.bindiya.scheduling.calendar.CalendarRepository;
import com.bindiya.scheduling.exception.ResourceNotFoundException;
import com.bindiya.scheduling.exception.SchedulingConflictException;
import com.bindiya.scheduling.meeting.dto.MeetingResponse;
import com.bindiya.scheduling.slot.SlotStatus;
import com.bindiya.scheduling.slot.TimeSlot;
import com.bindiya.scheduling.slot.TimeSlotRepository;
import com.bindiya.scheduling.user.User;
import com.bindiya.scheduling.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class MeetingService {

    private final CalendarRepository calendarRepository;
    private final TimeSlotRepository slotRepository;
    private final MeetingRepository meetingRepository;
    private final UserRepository userRepository;

    public MeetingService(
            CalendarRepository calendarRepository,
            TimeSlotRepository slotRepository,
            MeetingRepository meetingRepository,
            UserRepository userRepository
    ) {
        this.calendarRepository = calendarRepository;
        this.slotRepository = slotRepository;
        this.meetingRepository = meetingRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public MeetingResponse book(
            Long ownerUserId,
            Long slotId,
            String title,
            String description,
            Set<Long> participantIds
    ) {
        validateDetails(title, description, participantIds);

        // Use the same locking order as slot management.
        calendarRepository.findByUserIdForUpdate(ownerUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Calendar not found for user " + ownerUserId
                ));

        TimeSlot slot = slotRepository.findForUpdate(ownerUserId, slotId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Time slot not found for user " + ownerUserId
                ));

        if (meetingRepository.existsBySlot_Id(slotId)) {
            throw new SchedulingConflictException(
                    "Time slot is already booked"
            );
        }

        if (slot.getStatus() != SlotStatus.FREE) {
            throw new SchedulingConflictException(
                    "Time slot is unavailable"
            );
        }

        Set<Long> allParticipantIds = new HashSet<>(participantIds);
        allParticipantIds.add(ownerUserId);

        List<User> participants =
                userRepository.findAllById(allParticipantIds);

        if (participants.size() != allParticipantIds.size()) {
            Set<Long> missingIds = new HashSet<>(allParticipantIds);

            participants.forEach(user -> missingIds.remove(user.getId()));

            throw new ResourceNotFoundException(
                    "Participants not found: " + missingIds.stream().sorted().toList()
            );
        }

        Meeting meeting = new Meeting(
                slot,
                title.strip(),
                description,
                new HashSet<>(participants)
        );

        slot.changeStatus(SlotStatus.BUSY);

        Meeting savedMeeting = meetingRepository.saveAndFlush(meeting);

        return MeetingResponse.from(savedMeeting);
    }

    private void validateDetails(
            String title,
            String description,
            Set<Long> participantIds
    ) {
        if (title == null || title.isBlank() || title.length() > 200) {
            throw new IllegalArgumentException(
                    "Title is required and must not exceed 200 characters"
            );
        }

        if (description != null && description.length() > 2000) {
            throw new IllegalArgumentException(
                    "Description must not exceed 2000 characters"
            );
        }

        if (participantIds == null || participantIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one participant is required"
            );
        }

        if (participantIds.stream()
                .anyMatch(id -> id == null || id <= 0)) {

            throw new IllegalArgumentException(
                    "Participant IDs must be positive numbers"
            );
        }
    }
}