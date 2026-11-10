package com.panenku.backend.common.exception

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class ExceptionHandler {
    @ExceptionHandler(ApiException::class)
    fun handle(ex: ApiException) = ResponseEntity.status(ex.status).body(ErrorResponse(code = ex.code, message = ex.message))
    @ExceptionHandler(AppException::class)
    fun handleAppException(ex: AppException) = ResponseEntity.status(ex.status).body(ErrorResponse(code = ex.status.name, message = ex.message))
    @ExceptionHandler(DataIntegrityViolationException::class, PessimisticLockingFailureException::class)
    fun databaseConflict(ex: Exception) = ResponseEntity.status(HttpStatus.CONFLICT).body(
        ErrorResponse(code = "CONFLICT", message = "Data berubah atau operasi berbenturan. Muat ulang atau ulangi request dengan key yang sama.")
    )
    @ExceptionHandler(MethodArgumentNotValidException::class, HttpMessageNotReadableException::class, MethodArgumentTypeMismatchException::class)
    fun badInput(ex: Exception) = ResponseEntity.badRequest().body(ErrorResponse(code = "INVALID_REQUEST", message = "Periksa format dan field request."))
}
