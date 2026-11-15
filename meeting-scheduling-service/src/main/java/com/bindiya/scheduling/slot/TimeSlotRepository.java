package com.bindiya.scheduling.slot;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TimeSlotRepository
        extends JpaRepository<TimeSlot, Long> {

    @Query("""
        select s
        from TimeSlot s
        where s.calendar.id = :calendarId
          and s.startTime < :to
          and s.endTime > :from
        order by s.startTime, s.id
        """)
    List<TimeSlot> findOverlapping(
            @Param("calendarId") Long calendarId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select s
        from TimeSlot s
        where s.id = :slotId
          and s.calendar.user.id = :userId
        """)
    Optional<TimeSlot> findForUpdate(
            @Param("userId") Long userId,
            @Param("slotId") Long slotId
    );
}