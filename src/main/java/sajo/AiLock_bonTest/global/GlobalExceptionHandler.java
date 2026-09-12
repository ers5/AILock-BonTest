package sajo.AiLock_bonTest.global;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import sajo.AiLock_bonTest.global.exception.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> methodArgumentNotValidError(
            MethodArgumentNotValidException e
    ) {
        String message = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("잘못된 요청");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_REQUEST", message));
    }

    @ExceptionHandler(DeviceNotFoundException.class)
    public ResponseEntity<ErrorResponse> deviceNotFoundError(DeviceNotFoundException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("SESSION_CREATE_FAILED", e.getMessage()));
    }

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ErrorResponse> sessionNotFoundError(SessionNotFoundException e) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("SESSION_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(ChatNotAllowedException.class)
    public ResponseEntity<ErrorResponse> chatNotAllowedException(ChatNotAllowedException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("CHAT_NOT_ALLOWED", e.getMessage()));
    }

    @ExceptionHandler(PermitNotFoundException.class)
    public ResponseEntity<ErrorResponse> permitNotFoundException(PermitNotFoundException e) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("PERMMIT_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(AiResponseException.class)
    public ResponseEntity<ErrorResponse> aiResponseException(AiResponseException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("AI_RESPONSE_FAILED", e.getMessage()));
    }

    @ExceptionHandler(ChatConflictException.class)
    public ResponseEntity<ErrorResponse> chatConflictException(ChatConflictException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CHAT_CONTEXT_STALE", e.getMessage()));
    }
}
