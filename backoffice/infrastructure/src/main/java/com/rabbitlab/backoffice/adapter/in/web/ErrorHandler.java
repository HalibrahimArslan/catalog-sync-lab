package com.rabbitlab.backoffice.adapter.in.web;

import com.rabbitlab.backoffice.domain.InvalidValueException;
import com.rabbitlab.backoffice.domain.NotFoundException;
import com.rabbitlab.backoffice.domain.RuleViolationException;
import com.rabbitlab.backoffice.domainservice.ConcurrentUpdateException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Domain hatalarını HTTP'ye çevirir (RFC 9457 Problem Details). Tek tek exception sınıflarını değil,
 * domain'in hata TÜRLERİNİ tanır; yeni bir kural ihlali eklemek bu sınıfı değiştirmez.
 */
@RestControllerAdvice
class ErrorHandler {

    @ExceptionHandler(InvalidValueException.class)
    ProblemDetail invalid(InvalidValueException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** 409: istek geçerli ama ürünün mevcut durumuyla (veya eşzamanlı bir değişiklikle) çelişiyor. */
    @ExceptionHandler({RuleViolationException.class, ConcurrentUpdateException.class})
    ProblemDetail conflict(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}
