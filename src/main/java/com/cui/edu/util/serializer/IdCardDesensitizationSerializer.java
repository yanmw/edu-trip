package com.cui.edu.util.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 身份证号脱敏序列化器。
 * <p>
 * 将身份证中间部分替换为 * 后输出，保留前6位（地址码）和后4位（顺序码/校验码）。
 * 例如：
 * <ul>
 *   <li>18位：110101199001011234 → 110101********1234（中间8位替换）</li>
 *   <li>15位：110101900101123  → 110101*****123（中间5位替换）</li>
 * </ul>
 * </p>
 * 通过 {@code @JsonSerialize(using = IdCardDesensitizationSerializer.class)}
 * 注解声明在字段上即可生效，对所有接口的 JSON 输出透明脱敏。
 */
public class IdCardDesensitizationSerializer extends JsonSerializer<String> {

    /** 脱敏保留的前缀位数（地址码） */
    private static final int PREFIX_LENGTH = 6;

    /** 脱敏保留的后缀位数（顺序码/校验码） */
    private static final int SUFFIX_LENGTH = 4;

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        // 值为空时原样输出，不做处理
        if (value == null || value.isEmpty()) {
            gen.writeNull();
            return;
        }
        // 身份证长度不足以保留前后位数时（异常数据），原样输出，不做脱敏
        if (value.length() <= PREFIX_LENGTH + SUFFIX_LENGTH) {
            gen.writeString(value);
            return;
        }
        // 计算中间需要替换的位数
        int maskedLength = value.length() - PREFIX_LENGTH - SUFFIX_LENGTH;
        // 用对应数量的 * 替换中间部分（兼容 Java 8，不使用 String.repeat）
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < maskedLength; i++) {
            stars.append('*');
        }
        String masked = value.substring(0, PREFIX_LENGTH)
                + stars
                + value.substring(value.length() - SUFFIX_LENGTH);
        gen.writeString(masked);
    }
}
