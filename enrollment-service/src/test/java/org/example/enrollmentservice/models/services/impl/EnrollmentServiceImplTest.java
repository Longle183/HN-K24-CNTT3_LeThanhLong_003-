package org.example.enrollmentservice.models.services.impl;

import org.example.enrollmentservice.exceptions.CourseNotFoundException;
import org.example.enrollmentservice.exceptions.CourseServiceException;
import org.example.enrollmentservice.exceptions.DuplicateCourseException;
import org.example.enrollmentservice.models.constants.EnrollmentStatus;
import org.example.enrollmentservice.models.dto.requests.CreateEnrollmentDetailRequest;
import org.example.enrollmentservice.models.dto.requests.CreateEnrollmentRequest;
import org.example.enrollmentservice.models.dto.responses.CourseResponse;
import org.example.enrollmentservice.models.dto.responses.EnrollmentResponse;
import org.example.enrollmentservice.models.entities.Enrollment;
import org.example.enrollmentservice.models.entities.EnrollmentDetail;
import org.example.enrollmentservice.models.repositories.EnrollmentDetailRepository;
import org.example.enrollmentservice.models.repositories.EnrollmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceImplTest {

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private EnrollmentDetailRepository enrollmentDetailRepository;

    @Mock
    private CourseGatewayService courseGatewayService;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private EnrollmentServiceImpl enrollmentService;

    @BeforeEach
    void setUp() {
        enrollmentService = new EnrollmentServiceImpl(
                enrollmentRepository,
                enrollmentDetailRepository,
                courseGatewayService,
                kafkaTemplate
        );
    }

    @Test
    void createEnrollment_Success() {
        CreateEnrollmentRequest request = new CreateEnrollmentRequest(
                "Nguyen Van A",
                "nguyenvana@example.com",
                List.of(
                        new CreateEnrollmentDetailRequest(1L),
                        new CreateEnrollmentDetailRequest(2L)
                )
        );

        CourseResponse course1 = new CourseResponse(1L, "Course 1", 100.0);
        CourseResponse course2 = new CourseResponse(2L, "Course 2", 150.0);

        when(courseGatewayService.getCourseById(1L)).thenReturn(course1);
        when(courseGatewayService.getCourseById(2L)).thenReturn(course2);

        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(invocation -> {
            Enrollment e = invocation.getArgument(0);
            e.setId(10L);
            return e;
        });

        when(enrollmentDetailRepository.save(any(EnrollmentDetail.class))).thenAnswer(invocation -> {
            EnrollmentDetail d = invocation.getArgument(0);
            d.setId(100L);
            return d;
        });

        EnrollmentResponse response = enrollmentService.createEnrollment(request);

        assertNotNull(response);
        assertEquals(10L, response.id());
        assertEquals("Nguyen Van A", response.studentName());
        assertEquals("nguyenvana@example.com", response.studentEmail());
        assertEquals(250.0, response.totalFee());
        assertEquals(EnrollmentStatus.PENDING, response.status());
        assertEquals(2, response.items().size());

        // Verify Kafka message sent
        verify(kafkaTemplate).send("enrollment-created", "nguyenvana@example.com");

        // Verify enrollment saved
        ArgumentCaptor<Enrollment> enrollmentCaptor = ArgumentCaptor.forClass(Enrollment.class);
        verify(enrollmentRepository).save(enrollmentCaptor.capture());
        assertEquals(250.0, enrollmentCaptor.getValue().getTotalFee());
        assertEquals(EnrollmentStatus.PENDING, enrollmentCaptor.getValue().getStatus());

        // Verify details saved
        verify(enrollmentDetailRepository, times(2)).save(any(EnrollmentDetail.class));
    }

    @Test
    void createEnrollment_ThrowsDuplicateCourseException_WhenDuplicateCourses() {
        CreateEnrollmentRequest request = new CreateEnrollmentRequest(
                "Nguyen Van A",
                "nguyenvana@example.com",
                List.of(
                        new CreateEnrollmentDetailRequest(1L),
                        new CreateEnrollmentDetailRequest(1L)
                )
        );

        assertThrows(DuplicateCourseException.class, () -> enrollmentService.createEnrollment(request));

        verifyNoInteractions(courseGatewayService);
        verifyNoInteractions(enrollmentRepository);
        verifyNoInteractions(enrollmentDetailRepository);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void createEnrollment_ThrowsCourseNotFoundException_WhenCourseDoesNotExist() {
        CreateEnrollmentRequest request = new CreateEnrollmentRequest(
                "Nguyen Van A",
                "nguyenvana@example.com",
                List.of(new CreateEnrollmentDetailRequest(999L))
        );

        when(courseGatewayService.getCourseById(999L)).thenThrow(new CourseNotFoundException(999L));

        assertThrows(CourseNotFoundException.class, () -> enrollmentService.createEnrollment(request));

        verifyNoInteractions(enrollmentRepository);
        verifyNoInteractions(enrollmentDetailRepository);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void createEnrollment_ThrowsCourseServiceException_WhenCourseServiceFails() {
        CreateEnrollmentRequest request = new CreateEnrollmentRequest(
                "Nguyen Van A",
                "nguyenvana@example.com",
                List.of(new CreateEnrollmentDetailRequest(1L))
        );

        when(courseGatewayService.getCourseById(1L)).thenThrow(new CourseServiceException("Course service is down"));

        assertThrows(CourseServiceException.class, () -> enrollmentService.createEnrollment(request));

        verifyNoInteractions(enrollmentRepository);
        verifyNoInteractions(enrollmentDetailRepository);
        verifyNoInteractions(kafkaTemplate);
    }
}
