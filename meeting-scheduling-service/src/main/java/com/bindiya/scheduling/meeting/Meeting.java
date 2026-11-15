package com.bindiya.scheduling.meeting;

import com.bindiya.scheduling.slot.TimeSlot;
import com.bindiya.scheduling.user.User;
import jakarta.persistence.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "meetings")
public class Meeting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id", nullable = false, unique = true)
    private TimeSlot slot;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "meeting_participants",
            joinColumns = @JoinColumn(name = "meeting_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_meeting_participant",
                    columnNames = {"meeting_id", "user_id"}
            )
    )
    private Set<User> participants = new HashSet<>();

    protected Meeting() {
        // Required by JPA.
    }

    public Meeting(
            TimeSlot slot,
            String title,
            String description,
            Set<User> participants
    ) {
        this.slot = slot;
        this.title = title;
        this.description = description;
        this.participants = new HashSet<>(participants);
    }

    public Long getId() {
        return id;
    }

    public TimeSlot getSlot() {
        return slot;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Set<User> getParticipants() {
        return Collections.unmodifiableSet(participants);
    }
}