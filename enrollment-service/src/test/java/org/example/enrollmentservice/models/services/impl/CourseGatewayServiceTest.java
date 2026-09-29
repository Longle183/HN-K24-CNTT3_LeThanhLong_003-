package org.example.enrollmentservice.models.services.impl;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.example.enrollmentservice.clients.CourseClient;
import org.example.enrollmentservice.exceptions.CourseNotFoundException;
import org.example.enrollmentservice.exceptions.CourseServiceException;
import org.example.enrollmentservice.models.dto.responses.CourseResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseGatewayServiceTest {

    @Mock
    private CourseClient courseClient;

    private CourseGatewayService courseGatewayService;

    @BeforeEach
    void setUp() {
        courseGatewayService = new CourseGatewayService(courseClient);
    }

    private Request createDummyRequest() {
        return Request.create(
                Request.HttpMethod.GET,
                "/api/v1/courses/1",
                Collections.emptyMap(),
                null,
                new RequestTemplate()
        );
    }

    @Test
    void getCourseById_Success() {
        CourseResponse mockResponse = new CourseResponse(1L, "Spring Boot", 200.0);
        when(courseClient.getCourseById(1L)).thenReturn(mockResponse);

        CourseResponse result = courseGatewayService.getCourseById(1L);

        assertNotNull(result);
        assertEquals(1L, result.courseId());
        assertEquals("Spring Boot", result.courseName());
        assertEquals(200.0, result.courseFee());
        verify(courseClient).getCourseById(1L);
    }

    @Test
    void getCourseById_ThrowsCourseNotFoundException_WhenFeignReturns404() {
        Request request = createDummyRequest();
        when(courseClient.getCourseById(1L))
                .thenThrow(new FeignException.NotFound("Course not found", request, null, null));

        assertThrows(CourseNotFoundException.class, () -> courseGatewayService.getCourseById(1L));
    }

    @Test
    void getCourseById_ThrowsCourseServiceException_WhenCourseDataIsInvalid() {
        CourseResponse invalidResponse = new CourseResponse(1L, "Course Without Fee", null);
        when(courseClient.getCourseById(1L)).thenReturn(invalidResponse);

        assertThrows(CourseServiceException.class, () -> courseGatewayService.getCourseById(1L));
    }

    @Test
    void fallback_RethrowsCourseNotFoundException() {
        CourseNotFoundException ex = new CourseNotFoundException(1L);
        CourseNotFoundException thrown = assertThrows(CourseNotFoundException.class,
                () -> courseGatewayService.getCourseByIdFallback(1L, ex));
        assertEquals(ex.getMessage(), thrown.getMessage());
    }

    @Test
    void fallback_WrapsOtherExceptionIntoCourseServiceException() {
        RuntimeException ex = new RuntimeException("Service down");
        CourseServiceException thrown = assertThrows(CourseServiceException.class,
                () -> courseGatewayService.getCourseByIdFallback(1L, ex));
        assertTrue(thrown.getMessage().contains("unavailable") || thrown.getMessage().contains("Service down"));
    }
}
