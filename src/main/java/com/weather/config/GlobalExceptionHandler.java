package com.weather.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import javax.servlet.http.HttpServletRequest;

/**
 * 全局异常处理：未捕获异常统一落到 /error 页面，避免白屏。
 * API 接口返回统一 Result 错误体。
 */
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ModelAndView handleAll(Exception e, HttpServletRequest request) {
        log.error("处理请求 {} 时发生未捕获异常", request.getRequestURI(), e);
        ModelAndView mv = new ModelAndView("error");
        mv.addObject("status", 500);
        mv.addObject("errorMsg", "服务器内部错误，请稍后再试");
        mv.addObject("path", request.getRequestURI());
        return mv;
    }
}
