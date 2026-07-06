package com.cui.edu.util.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 手机号脱敏序列化器。
 * <p>
 * 将手机号中间4位替换为 * 后输出，保留前3位和后4位。
 * 例如：13812345678 → 138****5678
 * </p>
 * 通过 {@code @JsonSerialize(using = MobileDesensitizationSerializer.class)}
 * 注解声明在字段上即可生效，对所有接口的 JSON 输出透明脱敏。
 */
public class MobileDesensitizationSerializer extends JsonSerializer<String> {

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        // 值为空时原样输出，不做处理
        if (value == null || value.isEmpty()) {
            gen.writeNull();
            return;
        }
        // 手机号标准长度为11位；长度不足时原样输出，避免越界
        if (value.length() != 11) {
            gen.writeString(value);
            return;
        }
        // 保留前3位 + 4个星号 + 后4位
        String masked = value.substring(0, 3) + "****" + value.substring(7);
        gen.writeString(masked);
    }
}
