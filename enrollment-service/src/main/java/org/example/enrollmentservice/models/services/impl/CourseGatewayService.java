package org.example.enrollmentservice.models.services.impl;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.enrollmentservice.clients.CourseClient;
import org.example.enrollmentservice.exceptions.CourseNotFoundException;
import org.example.enrollmentservice.exceptions.CourseServiceException;
import org.example.enrollmentservice.models.dto.responses.CourseResponse;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseGatewayService {

    private final CourseClient courseClient;

    @CircuitBreaker(name = "courseService", fallbackMethod = "getCourseByIdFallback")
    public CourseResponse getCourseById(long courseId) {
        try {
            CourseResponse response = courseClient.getCourseById(courseId);
            if (response == null || response.courseFee() == null || response.courseFee() < 0) {
                throw new CourseServiceException("Course service returned invalid data for course id: " + courseId);
            }
            if (response.courseId() == null) {
                response = new CourseResponse(courseId, response.courseName(), response.courseFee());
            }
            return response;
        } catch (FeignException.NotFound e) {
            throw new CourseNotFoundException(courseId);
        } catch (FeignException e) {
            if (e.status() == 404) {
                throw new CourseNotFoundException(courseId);
            }
            throw new CourseServiceException("Course service error for course id " + courseId + ": " + e.getMessage(), e);
        }
    }

    public CourseResponse getCourseByIdFallback(Long courseId, Throwable throwable) {
        if (throwable instanceof CourseNotFoundException notFoundException) {
            throw notFoundException;
        }
        if (throwable instanceof CourseServiceException serviceException) {
            throw serviceException;
        }
        throw new CourseServiceException("Course service is unavailable: " + throwable.getMessage(), throwable);
    }

}
