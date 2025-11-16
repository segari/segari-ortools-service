package id.segari.ortools.handler;

import id.segari.ortools.dto.ResponseDTO;
import id.segari.ortools.exception.BaseException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ExceptionsHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ResponseDTO<?>> handleException(ConstraintViolationException ex){
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ResponseDTO<>(null, ex.getMessage()));
    }

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ResponseDTO<?>> handleException(BaseException ex){
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(new ResponseDTO<>(null, ex.getMessage()));
    }

}
