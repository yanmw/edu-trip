package com.cui.edu.common;

/**
 * Constants enumerating the HTTP status codes.
 * All status codes defined in RFC1945 (HTTP/1.0), RFC2616 (HTTP/1.1), and
 * RFC2518 (WebDAV) are listed.
 */
public interface HttpStatus {
    // --- 2xx Success ---
    /** {@code 200 OK} 请求成功 */
    public static final int SC_OK = 200;

    // --- 4xx Client Error ---
    /** {@code 400 Bad Request} 一般表示客户端参数异常 */
    public static final int SC_BAD_REQUEST = 400;
    /** {@code 403 Forbidden} 无权限 */
    public static final int SC_FORBIDDEN = 403;
    /** {@code 404 Not Found} 请求的路径不存在 */
    public static final int SC_NOT_FOUND = 404;

    // --- 5xx Server Error ---
    /** {@code 500 Server Error} 服务器炸了 */
    public static final int SC_INTERNAL_SERVER_ERROR = 500;
    /** {@code 500 Server Logical Error} 服务器繁忙 */
    public static final int SC_SERVICE_UNAVAILABLE = 503;
    /** {@code 555 Server Error} 自定义异常 */
    public static final int SC_MY_ERROR = 555;

}
