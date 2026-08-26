package com.liangpu.udf;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.liangpu.json.JsonPathSupport;
import com.liangpu.json.JsonSupport;
import com.liangpu.json.JsonSyntaxException;
import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;

/**
 * json_length(json[, path])：返回 JSON 数据的长度（对齐 MaxCompute JSON_LENGTH）。
 *
 * <p>规则：JSON 数组 → 元素数；JSON 对象 → 成员数；其它类型（数字/布尔/字符串/JSON null）→ 1；
 * 不递归计算嵌套数组/对象长度。</p>
 *
 * <p>边界：未指定 path 作用于整个 JSON；path 目标不存在 → NULL；path 语法非法 → 抛错；
 * json 或 path 为 SQL NULL → NULL；非法 JSON 文本 → NULL。</p>
 *
 * <p>调用：</p>
 * <pre>
 * SELECT json_length('[1,2,3,4,5,6]');                       -- 6
 * SELECT json_length('{"x":1,"y":[1,2]}', '$.y');            -- 2
 * SELECT json_length('123');                                 -- 1
 * </pre>
 */
public class JsonLengthUdf extends GenericUDF {

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length < 1 || args.length > 2) {
            throw new UDFArgumentException("json_length 需要 1~2 个参数 (json[, path])，实际 " + args.length);
        }
        return PrimitiveObjectInspectorFactory.javaLongObjectInspector;
    }

    @Override
    public Object evaluate(DeferredObject[] args) throws HiveException {
        Object j = args[0].get();
        if (j == null) {
            return null;
        }
        String json = String.valueOf(j);

        final Object root;
        try {
            root = JsonSupport.parse(json);
        } catch (JsonSyntaxException e) {
            return null; // 非法 JSON（数据问题）宽容处理
        }

        if (args.length == 2) {
            Object p = args[1].get();
            if (p == null) {
                return null; // MC: json_path 为 null 返回 null
            }
            String path = String.valueOf(p);
            JsonPathSupport.Resolved resolved = JsonPathSupport.resolve(root, path); // path 非法直接抛出
            if (!resolved.exists()) {
                return null;
            }
            return lengthOf(resolved.value());
        }
        return lengthOf(root);
    }

    private Long lengthOf(Object value) {
        if (value instanceof JSONArray) {
            return (long) ((JSONArray) value).size();
        }
        if (value instanceof JSONObject) {
            return (long) ((JSONObject) value).size();
        }
        return 1L; // 标量（数字/布尔/字符串/JSON null）
    }

    @Override
    public String getDisplayString(String[] children) {
        return "json_length(" + (children.length > 0 ? children[0] : "?")
                + (children.length > 1 ? ", " + children[1] : "") + ")";
    }
}
