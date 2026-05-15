package com.connecthub.media.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MediaPlanLimitException.class)
    public ResponseEntity<Map<String, Object>> handlePlanLimit(MediaPlanLimitException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", ex.getMessage(), "status", 429));
    }

    @ExceptionHandler(MediaStorageQuotaException.class)
    public ResponseEntity<Map<String, Object>> handleQuota(MediaStorageQuotaException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("error", ex.getMessage(), "status", 413));
    }

    @ExceptionHandler(S3Exception.class)
    public ResponseEntity<Map<String, Object>> handleS3(S3Exception ex) {
        String code = ex.awsErrorDetails() != null ? ex.awsErrorDetails().errorCode() : "S3_ERROR";
        String message = ex.awsErrorDetails() != null && ex.awsErrorDetails().errorMessage() != null
                ? ex.awsErrorDetails().errorMessage()
                : ex.getMessage();
        int status = ex.statusCode();
        HttpStatus httpStatus = HttpStatus.resolve(status);
        if (httpStatus == null) {
            httpStatus = HttpStatus.BAD_GATEWAY;
            status = httpStatus.value();
        }
        log.error("S3 upload/request failed. code={}, status={}, message={}", code, status, message);
        return ResponseEntity.status(httpStatus)
                .body(Map.of(
                        "error", "AWS S3 rejected the media request: " + message,
                        "code", code,
                        "status", status
                ));
    }

    @ExceptionHandler(SdkClientException.class)
    public ResponseEntity<Map<String, Object>> handleAwsClient(SdkClientException ex) {
        log.error("AWS client error: ", ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of(
                        "error", "Could not connect to AWS S3. Check AWS credentials, region, bucket name, and network.",
                        "status", 502
                ));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, Object>> handleIo(IOException ex) {
        log.error("Media file IO error: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Could not process uploaded file.", "status", 500));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handle(RuntimeException ex) {
        log.error("Unhandled exception caught: ", ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }
}
