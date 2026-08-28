package com.liangpu.json;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.List;

/**
 * JSONPath 求值支持：在已解析的 JSON 树（fastjson2 JSONObject/JSONArray）上按 path 定位。
 *
 * <p>自行遍历而非 fastjson2 JSONPath 求值的原因：需精确区分「目标不存在」与
 * 「目标存在但值为 JSON null」（json_exists 对值为 null 的键返回 true；json_extract
 * 对值为 null 的键返回 {@code "null"} 文本）。</p>
 */
public final class JsonPathSupport {

    private JsonPathSupport() {
    }

    /**
     * 定位结果：exists=false 表示路径目标不存在；exists=true 时 value 为定位值（可为 null，即 JSON null）。
     */
    public static final class Resolved {
        private final boolean exists;
        private final Object value;

        private Resolved(boolean exists, Object value) {
            this.exists = exists;
            this.value = value;
        }

        public static Resolved notFound() {
            return new Resolved(false, null);
        }

        public static Resolved found(Object value) {
            return new Resolved(true, value);
        }

        public boolean exists() {
            return exists;
        }

        public Object value() {
            return value;
        }
    }

    /**
     * 按 path 定位节点；path 语法非法抛 {@link JsonPathException}。
     */
    public static Resolved resolve(Object root, String path) {
        List<JsonPathParser.Segment> segments = JsonPathParser.parse(path);
        Object cur = root;
        for (JsonPathParser.Segment seg : segments) {
            if (seg.isIndex()) {
                if (!(cur instanceof JSONArray)) {
                    return Resolved.notFound();
                }
                JSONArray arr = (JSONArray) cur;
                int idx = seg.index();
                if (idx < 0 || idx >= arr.size()) {
                    return Resolved.notFound();
                }
                cur = arr.get(idx);
            } else {
                if (!(cur instanceof JSONObject)) {
                    return Resolved.notFound();
                }
                JSONObject obj = (JSONObject) cur;
                if (!obj.containsKey(seg.key())) {
                    return Resolved.notFound();
                }
                cur = obj.get(seg.key());
            }
        }
        return Resolved.found(cur);
    }
}
