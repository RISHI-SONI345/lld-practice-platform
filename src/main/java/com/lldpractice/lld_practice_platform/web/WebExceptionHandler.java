package com.lldpractice.lld_practice_platform.web;

import com.lldpractice.lld_practice_platform.attempt.IllegalTransitionException;
import com.lldpractice.lld_practice_platform.orchestration.AttemptNotFoundException;
import com.lldpractice.lld_practice_platform.orchestration.SubmissionRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class WebExceptionHandler {
    @ExceptionHandler({AttemptNotFoundException.class, UnknownProblemException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String notFound(RuntimeException e, Model model) {
        model.addAttribute("title", "Not found");
        model.addAttribute("message", "We could not find that page. It may belong to a different browser session.");
        return "error";
    }

    /** For example pressing Retry twice: the second event is an illegal transition. */
    @ExceptionHandler(IllegalTransitionException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    String conflict(IllegalTransitionException e, Model model) {
        model.addAttribute("title", "That action is not available");
        model.addAttribute("message", e.getMessage() + ". Reload the attempt page to see where it is now.");
        return "error";
    }

    @ExceptionHandler(SubmissionRejectedException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    String rejected(SubmissionRejectedException e, Model model) {
        model.addAttribute("title", "Request not accepted");
        model.addAttribute("message", e.getMessage());
        return "error";
    }
}
