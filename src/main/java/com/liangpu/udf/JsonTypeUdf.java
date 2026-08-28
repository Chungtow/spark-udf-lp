package com.liangpu.udf;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.liangpu.json.JsonSupport;
import com.liangpu.json.JsonSyntaxException;
import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.spark.sql.catalyst.expressions.ExpressionDescription;

/**
 * json_type(json)：返回 JSON 数据类型名称（对齐 MaxCompute JSON_TYPE），小写枚举：
 * {@code string / number / boolean / null / object / array}。
 *
 * <p>边界：SQL NULL 入参 → NULL；非法 JSON 文本 → NULL（数据宽容）。</p>
 *
 * <p>调用：</p>
 * <pre>
 * SELECT json_type('[{"a":1},23]'); -- array
 * SELECT json_type('123');          -- number
 * SELECT json_type('null');         -- null
 * </pre>
 */
@ExpressionDescription(
        usage = "json_type(json) - 返回 JSON 数据类型名称（小写）：string / number / boolean / null / object / array。",
        arguments = "json - JSON 文本")
public class JsonTypeUdf extends GenericUDF {

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length != 1) {
            throw new UDFArgumentException("json_type 需要 1 个参数 (json)，实际 " + args.length);
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
        if (parsed instanceof JSONObject) {
            return "object";
        }
        if (parsed instanceof JSONArray) {
            return "array";
        }
        if (parsed instanceof String) {
            return "string";
        }
        if (parsed instanceof Number) {
            return "number";
        }
        if (parsed instanceof Boolean) {
            return "boolean";
        }
        return "null"; // JSON null 字面量
    }

    @Override
    public String getDisplayString(String[] children) {
        return "json_type(" + (children.length > 0 ? children[0] : "?") + ")";
    }
}
