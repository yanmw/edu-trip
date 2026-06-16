package com.cui.edu.trip.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderControllerTest {

    @Test
    void findByOrderNoKeepsLegacyFindByIdPathAlias() throws Exception {
        Method method = OrderController.class.getMethod("findByOrderNo", String.class);
        GetMapping mapping = method.getAnnotation(GetMapping.class);

        assertTrue(Arrays.asList(mapping.value()).contains("/findByOrderNo/{orderNo}"));
        assertTrue(Arrays.asList(mapping.value()).contains("/findById/{orderNo}"));
    }
}
