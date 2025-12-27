package kr.hhplus.be.server.token.interfaces;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Token API 예외 핸들러
 *
 * 대기열 관련 비즈니스 예외를 적절한 HTTP 상태 코드로 변환합니다.
 */
@Slf4j
@RestControllerAdvice(assignableTypes = TokenController.class)
public class TokenExceptionHandler {

    /**
     * 중복 대기열 진입 등 비즈니스 예외 처리
     * - "이미 대기열에 있습니다" → HTTP 409 Conflict
     * - "이미 활성화된 상태입니다" → HTTP 409 Conflict
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
        log.debug("Token API IllegalStateException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("DUPLICATE_REQUEST", e.getMessage()));
    }

    /**
     * 잘못된 요청 파라미터
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.debug("Token API IllegalArgumentException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_REQUEST", e.getMessage()));
    }

    public record ErrorResponse(String code, String message) {}
}
