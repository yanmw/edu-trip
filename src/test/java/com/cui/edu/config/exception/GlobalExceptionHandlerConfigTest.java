package com.cui.edu.config.exception;

import com.cui.edu.common.HttpResult;
import com.cui.edu.common.HttpStatus;
import com.cui.edu.system.entity.SysException;
import com.cui.edu.system.service.SysExceptionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerConfigTest {

    @Test
    void requestBodyParseErrorReturnsBadRequestAndStoresRequestContext() {
        SysExceptionService exceptionService = mock(SysExceptionService.class);
        when(exceptionService.save(any())).thenReturn(true);
        GlobalExceptionHandlerConfig handler = new GlobalExceptionHandlerConfig();
        ReflectionTestUtils.setField(handler, "exceptionService", exceptionService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/trip/order/findPage");
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException(
                "JSON parse error: Unrecognized token 'oXAJFvjU8sVl7oLv16vY6s'");

        HttpResult result = handler.httpMessageNotReadableExceptionHandler(request, exception);

        assertEquals(HttpStatus.SC_BAD_REQUEST, result.getCode());
        assertEquals("请求体格式错误，请检查JSON格式", result.getMsg());
        ArgumentCaptor<SysException> exceptionCaptor = ArgumentCaptor.forClass(SysException.class);
        verify(exceptionService).save(exceptionCaptor.capture());
        SysException savedException = exceptionCaptor.getValue();
        assertEquals(HttpStatus.SC_BAD_REQUEST, savedException.getHttpStatusCode());
        assertTrue(savedException.getExceptionInfo().contains("POST /trip/order/findPage"));
        assertTrue(savedException.getExceptionInfo().contains("Unrecognized token"));
    }
}
