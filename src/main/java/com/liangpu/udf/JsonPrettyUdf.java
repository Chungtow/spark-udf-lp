package com.liangpu.udf;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.liangpu.json.JsonSupport;
import com.liangpu.json.JsonSyntaxException;
import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;

import java.util.Map;

/**
 * json_pretty(json)：美化 JSON 输出（增加换行与缩进，对齐 MaxCompute JSON_PRETTY 展示格式：
 * 每层 4 空格缩进、键值冒号后无空格）。
 *
 * <p>边界：SQL NULL 入参 → NULL；非法 JSON 文本 → NULL（数据宽容）。</p>
 *
 * <p>调用：</p>
 * <pre>
 * SELECT json_pretty('{"a":1,"b":2}');
 * -- 输出:
 * -- {
 * --     "a":1,
 * --     "b":2
 * -- }
 * </pre>
 */
public class JsonPrettyUdf extends GenericUDF {

    private static final String INDENT_UNIT = "    "; // 4 空格

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length != 1) {
            throw new UDFArgumentException("json_pretty 需要 1 个参数 (json)，实际 " + args.length);
        }
        return PrimitiveObjectInspectorFactory.javaStringObjectInspector;
    }

    @Override
    public Object evaluate(DeferredObject[] args) throws HiveException {
        Object value = args[0].get();
        if (value == null) {
            return null;
        }
        final Object parsed;
        try {
            parsed = JsonSupport.parse(String.valueOf(value));
        } catch (JsonSyntaxException e) {
            return null; // 非法 JSON（数据问题）宽容处理
        }
        return pretty(parsed, 0);
    }

    private String pretty(Object value, int indent) {
        if (value instanceof JSONObject) {
            JSONObject obj = (JSONObject) value;
            if (obj.isEmpty()) {
                return "{}";
            }
            StringBuilder sb = new StringBuilder("{\n");
            int i = 0;
            for (Map.Entry<String, Object> entry : obj.entrySet()) {
                sb.append(indent(indent + 1))
                        .append(JSON.toJSONString(entry.getKey())) // 键名 JSON 字符串字面量（带转义）
                        .append(':')
                        .append(pretty(entry.getValue(), indent + 1));
                if (++i < obj.size()) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            return sb.append(indent(indent)).append('}').toString();
        }
        if (value instanceof JSONArray) {
            JSONArray arr = (JSONArray) value;
            if (arr.isEmpty()) {
                return "[]";
            }
            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < arr.size(); i++) {
                sb.append(indent(indent + 1)).append(pretty(arr.get(i), indent + 1));
                if (i < arr.size() - 1) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            return sb.append(indent(indent)).append(']').toString();
        }
        if (value instanceof String) {
            return JSON.toJSONString(value); // 字符串字面量（带引号与转义）
        }
        if (value == null) {
            return "null";
        }
        return String.valueOf(value); // 数字 / 布尔
    }

    private String indent(int level) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            sb.append(INDENT_UNIT);
        }
        return sb.toString();
    }

    @Override
    public String getDisplayString(String[] children) {
        return "json_pretty(" + (children.length > 0 ? children[0] : "?") + ")";
    }
}
