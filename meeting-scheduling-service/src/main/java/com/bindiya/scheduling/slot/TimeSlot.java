package com.bindiya.scheduling.slot;

import com.bindiya.scheduling.calendar.Calendar;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "time_slots",
        indexes = {
                @Index(
                        name = "idx_time_slots_calendar_start",
                        columnList = "calendar_id,start_time"
                )
        }
)
public class TimeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calendar_id", nullable = false)
    private Calendar calendar;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SlotStatus status;

    @Version
    private long version;

    protected TimeSlot() {
        // Required by JPA.
    }

    public TimeSlot(Calendar calendar, Instant startTime, Instant endTime) {
        this.calendar = Objects.requireNonNull(calendar);
        reschedule(startTime, endTime);
        this.status = SlotStatus.FREE;
    }

    public void reschedule(Instant startTime, Instant endTime) {
        Objects.requireNonNull(startTime, "Start time is required");
        Objects.requireNonNull(endTime, "End time is required");

        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException(
                    "Start time must be before end time"
            );
        }

        this.startTime = startTime;
        this.endTime = endTime;
    }

    public void changeStatus(SlotStatus status) {
        this.status = Objects.requireNonNull(status);
    }

    public Long getId() {
        return id;
    }

    public Calendar getCalendar() {
        return calendar;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public SlotStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }
}