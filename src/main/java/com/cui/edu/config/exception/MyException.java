package com.cui.edu.config.exception;

/**
 * 自定义异常
 * @author Cuicui
 */
public class MyException extends RuntimeException {
    private int code;
    private String message;

    public MyException(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
