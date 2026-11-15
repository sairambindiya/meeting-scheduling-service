package com.bindiya.scheduling.meeting;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingRepository
        extends JpaRepository<Meeting, Long> {

    boolean existsBySlot_Id(Long slotId);
}