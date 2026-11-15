package com.bindiya.scheduling.slot;

import com.bindiya.scheduling.calendar.Calendar;
import com.bindiya.scheduling.slot.dto.PageResponse;
import com.bindiya.scheduling.calendar.CalendarRepository;
import com.bindiya.scheduling.exception.ResourceNotFoundException;
import com.bindiya.scheduling.exception.SchedulingConflictException;
import com.bindiya.scheduling.meeting.MeetingRepository;
import com.bindiya.scheduling.slot.dto.AvailabilityResponse;
import com.bindiya.scheduling.slot.dto.AvailabilityResponse.Interval;
import com.bindiya.scheduling.slot.dto.TimeSlotResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class TimeSlotService {

    private final CalendarRepository calendarRepository;
    private final TimeSlotRepository slotRepository;
    private final MeetingRepository meetingRepository;

    private static final int MAX_PAGE_SIZE = 100;
    private static final Duration MAX_QUERY_RANGE = Duration.ofDays(31);

    public TimeSlotService(
            CalendarRepository calendarRepository,
            TimeSlotRepository slotRepository,
            MeetingRepository meetingRepository
    ) {
        this.calendarRepository = calendarRepository;
        this.slotRepository = slotRepository;
        this.meetingRepository = meetingRepository;
    }

    @Transactional
    public TimeSlotResponse create(
            Long userId,
            Instant startTime,
            Instant endTime
    ) {
        validateRange(startTime, endTime);

        Calendar calendar = lockCalendar(userId);

        ensureNoOverlap(calendar.getId(), startTime, endTime, null);

        TimeSlot slot = new TimeSlot(calendar, startTime, endTime);

        return TimeSlotResponse.from(slotRepository.save(slot));
    }

    @Transactional
    public TimeSlotResponse update(
            Long userId,
            Long slotId,
            Instant startTime,
            Instant endTime,
            SlotStatus status
    ) {
        validateRange(startTime, endTime);

        if (status == null) {
            throw new IllegalArgumentException("Slot status is required");
        }

        Calendar calendar = lockCalendar(userId);
        TimeSlot slot = lockSlot(userId, slotId);

        ensureNotBooked(slotId);
        ensureNoOverlap(calendar.getId(), startTime, endTime, slotId);

        slot.reschedule(startTime, endTime);
        slot.changeStatus(status);

        return TimeSlotResponse.from(slot);
    }

    @Transactional
    public void delete(Long userId, Long slotId) {
        lockCalendar(userId);
        TimeSlot slot = lockSlot(userId, slotId);

        ensureNotBooked(slotId);

        slotRepository.delete(slot);
    }

    public PageResponse<TimeSlotResponse> list(
            Long userId,
            Instant from,
            Instant to,
            SlotStatus status,
            int page,
            int size
    ) {
        validateQueryRange(from, to);

        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page must be zero or greater"
            );
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Size must be between 1 and " + MAX_PAGE_SIZE
            );
        }

        Calendar calendar = findCalendar(userId);

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.asc("startTime"),
                        Sort.Order.asc("id")
                )
        );

        return PageResponse.from(
                slotRepository.findPageOverlapping(
                        calendar.getId(),
                        from,
                        to,
                        status,
                        pageable
                ).map(TimeSlotResponse::from)
        );
    }

    public AvailabilityResponse availability(
            Long userId,
            Instant from,
            Instant to,
            SlotStatus status
    ) {
        validateQueryRange(from, to);

        Calendar calendar = findCalendar(userId);
        List<Interval> merged = new ArrayList<>();

        for (TimeSlot slot :
                slotRepository.findOverlapping(calendar.getId(), from, to)) {


            Instant start = slot.getStartTime().isBefore(from)
                    ? from : slot.getStartTime();

            Instant end = slot.getEndTime().isAfter(to)
                    ? to : slot.getEndTime();

            Interval current = new Interval(start, end, slot.getStatus());

            if (!merged.isEmpty()) {
                int lastIndex = merged.size() - 1;
                Interval previous = merged.get(lastIndex);

                if (previous.status() == current.status()
                        && !current.startTime().isAfter(previous.endTime())) {

                    Instant mergedEnd =
                            previous.endTime().isAfter(current.endTime())
                                    ? previous.endTime()
                                    : current.endTime();

                    merged.set(lastIndex, new Interval(
                            previous.startTime(),
                            mergedEnd,
                            previous.status()
                    ));

                    continue;
                }
            }

            merged.add(current);
        }

        List<Interval> intervals = merged.stream()
                .filter(interval -> status == null || interval.status() == status)
                .toList();

        return new AvailabilityResponse(userId, from, to, intervals);
    }

    private Calendar findCalendar(Long userId) {
        return calendarRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Calendar not found for user " + userId
                ));
    }

    private Calendar lockCalendar(Long userId) {
        return calendarRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Calendar not found for user " + userId
                ));
    }

    private TimeSlot lockSlot(Long userId, Long slotId) {
        return slotRepository.findForUpdate(userId, slotId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Time slot not found for user " + userId
                ));
    }

    private void ensureNotBooked(Long slotId) {
        if (meetingRepository.existsBySlot_Id(slotId)) {
            throw new SchedulingConflictException(
                    "A booked time slot cannot be modified or deleted"
            );
        }
    }

    private void ensureNoOverlap(
            Long calendarId,
            Instant startTime,
            Instant endTime,
            Long excludedSlotId
    ) {
        boolean overlaps = slotRepository
                .findOverlapping(calendarId, startTime, endTime)
                .stream()
                .anyMatch(slot ->
                        !Objects.equals(slot.getId(), excludedSlotId)
                );

        if (overlaps) {
            throw new SchedulingConflictException(
                    "Time slot overlaps an existing slot"
            );
        }
    }

    private void validateQueryRange(Instant from, Instant to) {
        validateRange(from, to);

        if (Duration.between(from, to).compareTo(MAX_QUERY_RANGE) > 0) {
            throw new IllegalArgumentException(
                    "Requested time range must not exceed 31 days"
            );
        }
    }
    private void validateRange(Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException(
                    "Start time and end time are required"
            );
        }

        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException(
                    "Start time must be before end time"
            );
        }
    }
}