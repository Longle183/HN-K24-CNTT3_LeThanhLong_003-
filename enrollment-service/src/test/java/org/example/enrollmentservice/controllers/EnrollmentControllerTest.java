package org.example.enrollmentservice.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.enrollmentservice.exceptions.CourseNotFoundException;
import org.example.enrollmentservice.exceptions.CourseServiceException;
import org.example.enrollmentservice.exceptions.DuplicateCourseException;
import org.example.enrollmentservice.exceptions.GlobalExceptionHandler;
import org.example.enrollmentservice.models.constants.EnrollmentStatus;
import org.example.enrollmentservice.models.dto.requests.CreateEnrollmentDetailRequest;
import org.example.enrollmentservice.models.dto.requests.CreateEnrollmentRequest;
import org.example.enrollmentservice.models.dto.responses.EnrollmentDetailResponse;
import org.example.enrollmentservice.models.dto.responses.EnrollmentResponse;
import org.example.enrollmentservice.models.services.EnrollmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EnrollmentControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private EnrollmentService enrollmentService;

    @InjectMocks
    private EnrollmentController enrollmentController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(enrollmentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createEnrollment_Returns201_WhenValid() throws Exception {
        CreateEnrollmentRequest request = new CreateEnrollmentRequest(
                "Nguyen Van A",
                "nguyenvana@example.com",
                List.of(new CreateEnrollmentDetailRequest(1L))
        );

        EnrollmentResponse response = new EnrollmentResponse(
                1L,
                "Nguyen Van A",
                "nguyenvana@example.com",
                100.0,
                EnrollmentStatus.PENDING,
                List.of(new EnrollmentDetailResponse(1L, 1L, "Course 1", 100.0, 100.0))
        );

        when(enrollmentService.createEnrollment(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.studentName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.studentEmail").value("nguyenvana@example.com"))
                .andExpect(jsonPath("$.totalFee").value(100.0))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createEnrollment_Returns400_WhenDuplicateCourse() throws Exception {
        CreateEnrollmentRequest request = new CreateEnrollmentRequest(
                "Nguyen Van A",
                "nguyenvana@example.com",
                List.of(new CreateEnrollmentDetailRequest(1L), new CreateEnrollmentDetailRequest(1L))
        );

        when(enrollmentService.createEnrollment(any())).thenThrow(new DuplicateCourseException());

        mockMvc.perform(post("/api/v1/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Duplicate course"));
    }

    @Test
    void createEnrollment_Returns404_WhenCourseNotFound() throws Exception {
        CreateEnrollmentRequest request = new CreateEnrollmentRequest(
                "Nguyen Van A",
                "nguyenvana@example.com",
                List.of(new CreateEnrollmentDetailRequest(999L))
        );

        when(enrollmentService.createEnrollment(any())).thenThrow(new CourseNotFoundException(999L));

        mockMvc.perform(post("/api/v1/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Course not found"));
    }

    @Test
    void createEnrollment_Returns502_WhenCourseServiceFails() throws Exception {
        CreateEnrollmentRequest request = new CreateEnrollmentRequest(
                "Nguyen Van A",
                "nguyenvana@example.com",
                List.of(new CreateEnrollmentDetailRequest(1L))
        );

        when(enrollmentService.createEnrollment(any())).thenThrow(new CourseServiceException("Course service is down"));

        mockMvc.perform(post("/api/v1/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Course service error"));
    }
}
