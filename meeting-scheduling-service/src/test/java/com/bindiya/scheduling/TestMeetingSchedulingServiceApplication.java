package com.bindiya.scheduling;

import org.springframework.boot.SpringApplication;

public class TestMeetingSchedulingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(MeetingSchedulingServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
