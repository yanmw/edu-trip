package com.cui.edu.trip.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityManageControllerTest {

    @Test
    void activityManageDoesNotExposeLegacyAuditEndpoint() {
        assertFalse(hasPostMapping("/audit/{id}"));
        assertTrue(hasPostMapping("/updateStatus"));
    }

    @Test
    void updateStatusUsesRequestBody() throws NoSuchMethodException {
        boolean hasBodyParam = Arrays.stream(ActivityManageController.class.getDeclaredMethods())
                .filter(method -> "updateStatus".equals(method.getName()))
                .anyMatch(method -> method.getParameterCount() == 1
                        && method.getParameters()[0].getAnnotation(RequestBody.class) != null
                        && method.getParameters()[0].getAnnotation(RequestParam.class) == null);

        assertTrue(hasBodyParam);
    }

    private boolean hasPostMapping(String path) {
        return Arrays.stream(ActivityManageController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(postMapping -> postMapping != null)
                .flatMap(postMapping -> Arrays.stream(postMapping.value()))
                .anyMatch(path::equals);
    }
}
