package br.com.reabilitacare.shared;

import br.com.reabilitacare.auth.CaregiverNotFoundException;
import br.com.reabilitacare.auth.EmailAlreadyRegisteredException;
import br.com.reabilitacare.auth.InvalidCredentialsException;
import br.com.reabilitacare.care.CareRecordNotFoundException;
import br.com.reabilitacare.care.InvalidCareRecordException;
import br.com.reabilitacare.patient.InvalidPaginationException;
import br.com.reabilitacare.patient.PatientNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiError> emailAlreadyRegistered() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("email_conflict", "Este email já está cadastrado."));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> invalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("invalid_credentials", "Email ou senha inválidos."));
    }

    @ExceptionHandler(CaregiverNotFoundException.class)
    public ResponseEntity<ApiError> caregiverNotFound() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("unauthorized", "É necessário entrar para acessar este recurso."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalidRequest() {
        return ResponseEntity.badRequest()
                .body(new ApiError("invalid_request", "Revise os dados enviados."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadableRequest() {
        return ResponseEntity.badRequest()
                .body(new ApiError("invalid_request", "O formato da solicitação é inválido."));
    }

    @ExceptionHandler(InvalidPaginationException.class)
    public ResponseEntity<ApiError> invalidPagination() {
        return ResponseEntity.badRequest()
                .body(new ApiError("invalid_request", "Revise os parâmetros de paginação."));
    }

    @ExceptionHandler(InvalidCareRecordException.class)
    public ResponseEntity<ApiError> invalidCareRecord() {
        return ResponseEntity.badRequest()
                .body(new ApiError("invalid_request", "Revise os dados do registro de cuidado."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> invalidParameter() {
        return ResponseEntity.badRequest()
                .body(new ApiError("invalid_request", "Um parâmetro da solicitação é inválido."));
    }

    @ExceptionHandler(PatientNotFoundException.class)
    public ResponseEntity<ApiError> patientNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("patient_not_found", "Paciente não encontrado."));
    }

    @ExceptionHandler(CareRecordNotFoundException.class)
    public ResponseEntity<ApiError> careRecordNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("care_record_not_found", "Registro de cuidado não encontrado."));
    }
}
