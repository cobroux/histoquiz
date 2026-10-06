package io.histoquiz.web;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.histoquiz.game.GameException;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(GameException.class)
	public ProblemDetail onGameException(GameException e) {
		return ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
	}
}
