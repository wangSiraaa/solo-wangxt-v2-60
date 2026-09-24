package com.railwindow.sim.service;

/** 请求引用的资源不存在（HTTP 404）。 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
