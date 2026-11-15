package com.bindiya.scheduling.calendar;

import com.bindiya.scheduling.user.User;
import jakarta.persistence.*;

@Entity
@Table(name = "calendars")
public class Calendar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    protected Calendar() {
        // Required by JPA.
    }

    public Calendar(User user) {
        this.user = user;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }
}
