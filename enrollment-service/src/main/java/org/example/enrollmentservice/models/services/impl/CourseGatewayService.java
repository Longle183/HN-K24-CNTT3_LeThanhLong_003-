package org.example.enrollmentservice.models.services.impl;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.example.enrollmentservice.clients.CourseClient;
import org.example.enrollmentservice.exceptions.CourseNotFoundException;
import org.example.enrollmentservice.exceptions.CourseServiceException;
import org.example.enrollmentservice.models.dto.responses.CourseResponse;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CourseGatewayService {

    private final CourseClient courseClient;

    public CourseResponse getCourseById(Long courseId) {
        throw new UnsupportedOperationException();
    }

}
