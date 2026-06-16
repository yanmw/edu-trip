package com.cui.edu.config.satoken;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SaTokenConfigureTest {

    @Test
    void nonControllerHandlerSkipsLoginCheck() {
        SaTokenConfigure configure = new SaTokenConfigure();

        Boolean result = ReflectionTestUtils.invokeMethod(configure, "isSaIgnore", new ResourceHttpRequestHandler());

        assertTrue(result);
    }
}
