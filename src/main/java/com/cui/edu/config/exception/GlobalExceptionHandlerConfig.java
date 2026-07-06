package com.cui.edu.config.exception;

import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.exception.SaTokenException;
import com.cui.edu.common.HttpResult;
import com.cui.edu.common.HttpStatus;
import com.cui.edu.common.SysConstants;
import com.cui.edu.system.entity.SysException;
import com.cui.edu.system.service.SysExceptionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 异常处理配置
 *
 * @author Cuicui
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandlerConfig {

    // 初始化 SaToken 错误码与提示信息的映射表
    private static final Map<Integer, String> SA_TOKEN_ERROR_MSG_MAP = new HashMap<>();

    // 静态代码块初始化映射关系
    static {
        // 基础异常
        SA_TOKEN_ERROR_MSG_MAP.put(10001, "未能获取有效的上下文处理器");
        SA_TOKEN_ERROR_MSG_MAP.put(10002, "未能获取有效的上下文");
        SA_TOKEN_ERROR_MSG_MAP.put(10003, "JSON 转换器未实现");
        SA_TOKEN_ERROR_MSG_MAP.put(10011, "未能从全局 StpLogic 集合中找到对应 type 的 StpLogic");
        SA_TOKEN_ERROR_MSG_MAP.put(10021, "指定的配置文件加载失败");
        SA_TOKEN_ERROR_MSG_MAP.put(10022, "配置文件属性无法正常读取");
        SA_TOKEN_ERROR_MSG_MAP.put(10031, "重置的侦听器集合不可以为空");
        SA_TOKEN_ERROR_MSG_MAP.put(10032, "注册的侦听器不可以为空");

        // 认证相关
        SA_TOKEN_ERROR_MSG_MAP.put(10301, "提供的 Same-Token 是无效的");
        SA_TOKEN_ERROR_MSG_MAP.put(10311, "表示未能通过 Http Basic 认证校验");
        SA_TOKEN_ERROR_MSG_MAP.put(10321, "提供的 HttpMethod 是无效的");

        // Token 相关
        SA_TOKEN_ERROR_MSG_MAP.put(11001, "未能读取到有效Token");
        SA_TOKEN_ERROR_MSG_MAP.put(11002, "登录时的账号id值为空");
        SA_TOKEN_ERROR_MSG_MAP.put(11003, "更改 Token 指向的 账号Id 时，账号Id值为空");
        SA_TOKEN_ERROR_MSG_MAP.put(11011, "未能读取到有效Token");
        SA_TOKEN_ERROR_MSG_MAP.put(11012, "Token无效");
        SA_TOKEN_ERROR_MSG_MAP.put(11013, "Token已过期");
        SA_TOKEN_ERROR_MSG_MAP.put(11014, "Token已被顶下线");
        SA_TOKEN_ERROR_MSG_MAP.put(11015, "Token已被踢下线");
        SA_TOKEN_ERROR_MSG_MAP.put(11016, "Token已被冻结");
        SA_TOKEN_ERROR_MSG_MAP.put(11031, "在未集成 sa-token-jwt 插件时调用 getExtra() 抛出异常");

        // 权限角色相关
        SA_TOKEN_ERROR_MSG_MAP.put(11041, "缺少指定的角色");
        SA_TOKEN_ERROR_MSG_MAP.put(11051, "缺少指定的权限");
        SA_TOKEN_ERROR_MSG_MAP.put(11061, "当前账号未通过服务封禁校验");
        SA_TOKEN_ERROR_MSG_MAP.put(11062, "提供要解禁的账号无效");
        SA_TOKEN_ERROR_MSG_MAP.put(11063, "提供要解禁的服务无效");
        SA_TOKEN_ERROR_MSG_MAP.put(11064, "提供要解禁的等级无效");
        SA_TOKEN_ERROR_MSG_MAP.put(11071, "二级认证校验未通过");

        // 参数与Cookie相关
        SA_TOKEN_ERROR_MSG_MAP.put(12001, "请求中缺少指定的参数");
        SA_TOKEN_ERROR_MSG_MAP.put(12002, "构建 Cookie 时缺少 name 参数");
        SA_TOKEN_ERROR_MSG_MAP.put(12003, "构建 Cookie 时缺少 value 参数");

        // 编码解码加密相关
        SA_TOKEN_ERROR_MSG_MAP.put(12101, "Base64 编码异常");
        SA_TOKEN_ERROR_MSG_MAP.put(12102, "Base64 解码异常");
        SA_TOKEN_ERROR_MSG_MAP.put(12103, "URL 编码异常");
        SA_TOKEN_ERROR_MSG_MAP.put(12104, "URL 解码异常");
        SA_TOKEN_ERROR_MSG_MAP.put(12111, "md5 加密异常");
        SA_TOKEN_ERROR_MSG_MAP.put(12112, "sha1 加密异常");
        SA_TOKEN_ERROR_MSG_MAP.put(12113, "sha256 加密异常");
        SA_TOKEN_ERROR_MSG_MAP.put(12114, "AES 加密异常");
        SA_TOKEN_ERROR_MSG_MAP.put(12115, "AES 解密异常");
        SA_TOKEN_ERROR_MSG_MAP.put(12116, "RSA 公钥加密异常");
        SA_TOKEN_ERROR_MSG_MAP.put(12117, "RSA 私钥加密异常");
        SA_TOKEN_ERROR_MSG_MAP.put(12118, "RSA 公钥解密异常");
        SA_TOKEN_ERROR_MSG_MAP.put(12119, "RSA 私钥解密异常");

        // 签名相关
        SA_TOKEN_ERROR_MSG_MAP.put(12201, "参与参数签名的秘钥不可为空");
        SA_TOKEN_ERROR_MSG_MAP.put(12202, "给定的签名无效");
        SA_TOKEN_ERROR_MSG_MAP.put(12203, "timestamp 超出允许的范围");
    }
    @Autowired
    private SysExceptionService exceptionService;


    @ExceptionHandler(value = MyException.class)
    @ResponseBody
    public HttpResult myExceptionHandler(HttpServletRequest req, MyException e) {
        exceptionIntoTable(e, e.getCode(), e.getMessage());
        return HttpResult.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理空指针的异常
     *
     * @param req
     * @param e
     * @return
     */
    @ExceptionHandler(value = NullPointerException.class)
    @ResponseBody
    public HttpResult nullExceptionHandler(HttpServletRequest req, NullPointerException e) {
        exceptionIntoTable(e, HttpStatus.SC_SERVICE_UNAVAILABLE, SysConstants.NULL_EXCEPTION);
        return HttpResult.error(HttpStatus.SC_SERVICE_UNAVAILABLE, "服务器繁忙!");
    }

    /**
     * 无权限
     *
     * @param e
     * @return
     */
    @ExceptionHandler(NotPermissionException.class)
    public HttpResult handlerException(NotPermissionException e) {
        exceptionIntoTable(e, HttpStatus.SC_FORBIDDEN, SysConstants.FORBIDDEN);
        return HttpResult.error(HttpStatus.SC_FORBIDDEN, "暂无操作权限，请联系管理员");
    }

    /**
     * 缺少角色
     *
     * @param e
     * @return
     */
    @ExceptionHandler(NotRoleException.class)
    public HttpResult handlerException(NotRoleException e) {
        exceptionIntoTable(e, HttpStatus.SC_FORBIDDEN, SysConstants.FORBIDDEN);
        return HttpResult.error(HttpStatus.SC_FORBIDDEN, "缺少角色：" + e.getRole());
    }

    @ExceptionHandler(SaTokenException.class)
    public HttpResult handlerSaTokenException(SaTokenException e) {
        // 获取异常码
        int errorCode = e.getCode();
        // 从映射表中获取对应的提示信息，不存在则使用默认提示
        String errorMsg = SA_TOKEN_ERROR_MSG_MAP.getOrDefault(errorCode, "服务器繁忙，请稍后重试...");

        // 记录异常到表中
        exceptionIntoTable(e, HttpStatus.SC_FORBIDDEN, errorMsg);
        // 返回统一的错误结果
        return HttpResult.error(HttpStatus.SC_FORBIDDEN, errorMsg);
    }

    /**
     * 请求体JSON格式错误
     */
    @ExceptionHandler(value = HttpMessageNotReadableException.class)
    @ResponseBody
    public HttpResult httpMessageNotReadableExceptionHandler(HttpServletRequest req, HttpMessageNotReadableException e) {
        exceptionIntoTable(req, e, HttpStatus.SC_BAD_REQUEST, "请求体格式错误");
        return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "请求体格式错误，请检查JSON格式");
    }

    /**
     * 捕获Tomcat核心异常（ClientAbortException）
     * 无论返回JSON还是文件，连接断开都会抛这个异常，优先级最高
     */
    @ExceptionHandler(ClientAbortException.class)
    public void handleClientAbortException(ClientAbortException e) {
        // 仅打印WARN级别日志，不输出完整堆栈（避免日志刷屏）
        log.warn("[客户端断开连接] 场景：{} | 原因：{}",
                e.getMessage().contains("Resource") ? "文件/资源响应" : "JSON接口响应",
                e.getMessage());
    }

    /**
     * 处理底层IO异常（Broken pipe/Connection reset by peer）
     * 极少数情况下不会包装成ClientAbortException，直接抛IOException，用这个兜底
     */
    @ExceptionHandler(IOException.class)
    public HttpResult handleIOException(IOException e) {
        String msg = e.getMessage();
        if (msg != null && (msg.contains("Broken pipe") || msg.contains("Connection reset by peer"))) {
            log.warn("[客户端连接重置] 原因：{}", msg);
            return HttpResult.ok();
        } else {
            // 非连接类IO异常（如文件不存在、读取失败），正常打印错误日志（需要关注）
            log.error("[IO异常-需关注] 非客户端连接问题，详情：", e);
            exceptionIntoTable(e, HttpStatus.SC_INTERNAL_SERVER_ERROR, SysConstants.IO_EXCEPTION);
            return HttpResult.error(HttpStatus.SC_INTERNAL_SERVER_ERROR, "当前IO_EXCEPTION。");
        }
    }

    /**
     * 处理其他异常
     *
     * @param req
     * @param e
     * @return
     */
    @ExceptionHandler(value = Exception.class)
    @ResponseBody
    public HttpResult exceptionHandler(HttpServletRequest req, Exception e) {
        exceptionIntoTable(req, e, HttpStatus.SC_INTERNAL_SERVER_ERROR, SysConstants.EXCEPTION);
        return HttpResult.error(HttpStatus.SC_INTERNAL_SERVER_ERROR, "当前网络情况异常请稍后再试。");
    }

    private void exceptionIntoTable(Exception e, int httpStatusCode, String exceptionType) {
        exceptionIntoTable(null, e, httpStatusCode, exceptionType);
    }

    private void exceptionIntoTable(HttpServletRequest req, Exception e, int httpStatusCode, String exceptionType) {
        String requestInfo = buildRequestInfo(req);
        String exceptionInfo = buildExceptionInfo(requestInfo, e.getMessage());
        log.error("系统异常，请求：{}，类型：{}，状态码：{}，消息：{}", requestInfo, exceptionType, httpStatusCode, e.getMessage(), e);
        SysException exception = new SysException();
        exception.setHttpStatusCode(httpStatusCode);
        exception.setExceptionType(exceptionType);
        exception.setExceptionInfo(exceptionInfo);
        StackTraceElement[] elements = e.getStackTrace();
        StringBuffer sb = new StringBuffer();
        for (StackTraceElement element : elements) {
            sb.append(element.getClassName()).append("--");
            sb.append(element.getFileName()).append("--");
            sb.append(element.getMethodName()).append("--");
            sb.append(element.getLineNumber()).append("\n");
        }
        exception.setExceptionDetail(sb.toString());
        try {
            exceptionService.save(exception);
        } catch (Exception saveException) {
            log.error("系统异常信息保存失败，类型：{}，状态码：{}", exceptionType, httpStatusCode, saveException);
        }
    }

    private String buildRequestInfo(HttpServletRequest req) {
        if (req == null) {
            return "未知请求";
        }
        String queryString = req.getQueryString();
        String uri = req.getRequestURI();
        if (queryString != null && queryString.length() > 0) {
            uri = uri + "?" + queryString;
        }
        return req.getMethod() + " " + uri;
    }

    private String buildExceptionInfo(String requestInfo, String message) {
        if (message == null) {
            message = "";
        }
        return requestInfo + " " + message;
    }
}
