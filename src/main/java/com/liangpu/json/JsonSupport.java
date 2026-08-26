package com.liangpu.json;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;

/**
 * JSON 解析统一入口：屏蔽 fastjson2 细节，统一异常分类。
 *
 * <p>解析失败抛 {@link JsonSyntaxException}（数据问题）；调用方按 ADR-3 转为 NULL / 0 行。</p>
 *
 * <p>类型判断约定（json_type / json_length 共用）：</p>
 * <ul>
 *   <li>{@link JSONObject} → 对象</li>
 *   <li>{@link JSONArray} → 数组</li>
 *   <li>{@link String} → 字符串</li>
 *   <li>{@link Number} → 数字</li>
 *   <li>{@link Boolean} → 布尔</li>
 *   <li>null（JSON null 字面量）→ null 类型</li>
 * </ul>
 */
public final class JsonSupport {

    private JsonSupport() {
    }

    /**
     * 解析 JSON 文本；非法 JSON 抛 {@link JsonSyntaxException}。
     * 入参为 null（SQL NULL）时直接返回 null，不做解析。
     */
    public static Object parse(String json) {
        if (json == null) {
            return null;
        }
        try {
            return JSON.parse(json);
        } catch (RuntimeException e) {
            throw new JsonSyntaxException("invalid json: " + abbreviate(json), e);
        }
    }

    /**
     * 宽容解析：非法 JSON 返回 null，不抛异常。
     * 注意：输入为 JSON null 字面量（"null"）时同样返回 null（解析成功），
     * 无法与解析失败区分；需要区分时请用 {@link #isValid} / {@link #parse}。
     */
    public static Object tryParse(String json) {
        if (json == null) {
            return null;
        }
        try {
            return JSON.parse(json);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 校验是否为合法 JSON（RFC 8259），供 json_valid 使用。
     * null 入参返回 false；空串/纯空白返回 false（fastjson2 对空串宽容返回 null，需显式拦截）。
     */
    public static boolean isValid(String json) {
        if (json == null) {
            return false;
        }
        if (json.trim().isEmpty()) {
            return false; // 空串/纯空白不是合法 JSON
        }
        try {
            JSON.parse(json);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 将解析后的值序列化为 JSON 文本（紧凑）。
     * 注意：值为 Java null（JSON null 字面量）时返回 {@code "null"}（四字符文本）。
     *
     * <p>显式开启 {@code WriteNulls}：fastjson2 默认省略对象中值为 null 的字段，
     * 而本库输出必须保真（如 json_extract 取整树时 {@code {"a":null}} 的 null 字段不能丢）。</p>
     */
    public static String toJsonString(Object value) {
        if (value == null) {
            return "null";
        }
        return JSON.toJSONString(value, JSONWriter.Feature.WriteNulls);
    }

    private static String abbreviate(String s) {
        return s.length() <= 64 ? s : s.substring(0, 64) + "...";
    }
}
