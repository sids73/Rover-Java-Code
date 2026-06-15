package com.rover.ac13.web;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

class WebControllerApplicationTest {

    @Test
    void mainDelegatesToSpringApplicationRun() {
        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            springApplication.when(() -> SpringApplication.run(any(Class.class), any(String[].class))).thenReturn(null);

            WebControllerApplication.main(new String[] {"--spring.main.web-application-type=none"});

            springApplication.verify(() -> SpringApplication.run(any(Class.class), any(String[].class)));
        }
    }
}
