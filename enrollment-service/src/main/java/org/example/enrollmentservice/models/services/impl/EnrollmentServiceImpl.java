package org.example.enrollmentservice.models.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.enrollmentservice.exceptions.DuplicateCourseException;
import org.example.enrollmentservice.models.constants.EnrollmentStatus;
import org.example.enrollmentservice.models.dto.requests.CreateEnrollmentDetailRequest;
import org.example.enrollmentservice.models.dto.requests.CreateEnrollmentRequest;
import org.example.enrollmentservice.models.dto.responses.CourseResponse;
import org.example.enrollmentservice.models.dto.responses.EnrollmentDetailResponse;
import org.example.enrollmentservice.models.dto.responses.EnrollmentResponse;
import org.example.enrollmentservice.models.entities.Enrollment;
import org.example.enrollmentservice.models.entities.EnrollmentDetail;
import org.example.enrollmentservice.models.repositories.EnrollmentDetailRepository;
import org.example.enrollmentservice.models.repositories.EnrollmentRepository;
import org.example.enrollmentservice.models.services.EnrollmentService;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentServiceImpl implements EnrollmentService {

    public static final String ENROLLMENT_CREATED_TOPIC = "enrollment-created";

    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentDetailRepository enrollmentDetailRepository;
    private final CourseGatewayService courseGatewayService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public EnrollmentServiceImpl(EnrollmentRepository enrollmentRepository, EnrollmentDetailRepository enrollmentDetailRepository, CourseGatewayService courseGatewayService, KafkaTemplate<java.lang.String,java.lang.String> kafkaTemplate, EnrollmentRepository enrollmentRepository1, EnrollmentDetailRepository enrollmentDetailRepository1, CourseGatewayService courseGatewayService1, KafkaTemplate<String, String> kafkaTemplate1) {
        this.enrollmentRepository = enrollmentRepository1;
        this.enrollmentDetailRepository = enrollmentDetailRepository1;
        this.courseGatewayService = courseGatewayService1;
        this.kafkaTemplate = kafkaTemplate1;
    }

    @Override
    @Transactional
    public EnrollmentResponse createEnrollment(CreateEnrollmentRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("Enrollment items cannot be empty");
        }

        // Check for duplicate course IDs in request items
        Set<Long> seenCourseIds = new HashSet<>();
        for (CreateEnrollmentDetailRequest item : request.items()) {
            if (!seenCourseIds.add(item.courseId())) {
                throw new DuplicateCourseException();
            }
        }

        // Fetch latest course details from Course Service for each item
        List<CourseResponse> courseResponses = new ArrayList<>();
        double totalFee = 0.0;
        for (CreateEnrollmentDetailRequest item : request.items()) {
            CourseResponse course = courseGatewayService.getCourseById(item.courseId());
            courseResponses.add(course);
            totalFee += course.courseFee();
        }

        // Create and save Enrollment entity with status PENDING and calculated totalFee
        Enrollment enrollment = Enrollment.builder()
                .studentName(request.studentName())
                .studentEmail(request.studentEmail())
                .totalFee(totalFee)
                .status(EnrollmentStatus.PENDING)
                .build();
        Enrollment savedEnrollment = enrollmentRepository.save(enrollment);

        // Create and save EnrollmentDetail entity for each course
        List<EnrollmentDetailResponse> detailResponses = new ArrayList<>();
        for (CourseResponse course : courseResponses) {
            EnrollmentDetail detail = EnrollmentDetail.builder()
                    .enrollment(savedEnrollment)
                    .courseId(course.courseId())
                    .courseFee(course.courseFee())
                    .build();
            EnrollmentDetail savedDetail = enrollmentDetailRepository.save(detail);

            detailResponses.add(new EnrollmentDetailResponse(
                    savedDetail.getId(),
                    savedDetail.getCourseId(),
                    course.courseName(),
                    savedDetail.getCourseFee(),
                    savedDetail.getCourseFee()
            ));
        }

        // Publish event to Kafka topic "enrollment-created" with studentEmail as payload
        try {
            kafkaTemplate.send(ENROLLMENT_CREATED_TOPIC, request.studentEmail());
        } catch (Exception e) {
            log.error("Failed to send Kafka message to topic {}: {}", ENROLLMENT_CREATED_TOPIC, e.getMessage(), e);
        }

        return new EnrollmentResponse(
                savedEnrollment.getId(),
                savedEnrollment.getStudentName(),
                savedEnrollment.getStudentEmail(),
                savedEnrollment.getTotalFee(),
                savedEnrollment.getStatus(),
                detailResponses
        );
    }

}
