package com.weather.config;

import com.weather.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import javax.servlet.http.HttpServletRequest;

/**
 * 全局异常处理：页面请求落到 /error 视图，/api 或期望 JSON 的请求返回统一 Result 错误体。
 */
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public Object handleAll(Exception e, HttpServletRequest request) {
        log.error("处理请求 {} 时发生未捕获异常", request.getRequestURI(), e);

        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Result.error("服务器内部错误，请稍后再试"));
        }

        ModelAndView mv = new ModelAndView("error");
        mv.addObject("status", 500);
        mv.addObject("errorMsg", "服务器内部错误，请稍后再试");
        mv.addObject("path", request.getRequestURI());
        return mv;
    }

    /** /api/ 前缀，或 Accept 明确要 JSON 的请求，按接口处理 */
    private boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api/")) {
            return true;
        }
        String accept = request.getHeader("Accept");
        String xhr = request.getHeader("X-Requested-With");
        return (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE))
                || "XMLHttpRequest".equals(xhr);
    }
}
