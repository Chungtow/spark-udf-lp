package com.liangpu.udf;

import com.liangpu.json.JsonPathSupport;
import com.liangpu.json.JsonSupport;
import com.liangpu.json.JsonSyntaxException;
import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;

/**
 * json_exists(json, path)：判断 JSONPath 对应的值是否存在（对齐 MaxCompute JSON_EXISTS）。
 *
 * <p>边界：键存在（含值为 JSON null 的键）→ true；目标不存在 / 数组下标越界 → false；
 * path 语法非法 → 抛错（写法严格）；SQL NULL 入参 → NULL；非法 JSON 文本 → NULL。</p>
 *
 * <p>调用：</p>
 * <pre>
 * SELECT json_exists('{"a":1,"b":2}', '$.a');   -- true
 * SELECT json_exists('{"a":1,"b":2}', '$.c');   -- false
 * SELECT json_exists('{"a":null}', '$.a');      -- true（键存在）
 * </pre>
 */
public class JsonExistsUdf extends GenericUDF {

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length != 2) {
            throw new UDFArgumentException("json_exists 需要 2 个参数 (json, path)，实际 " + args.length);
        }
        return PrimitiveObjectInspectorFactory.javaBooleanObjectInspector;
    }

    @Override
    public Object evaluate(DeferredObject[] args) throws HiveException {
        Object j = args[0].get();
        Object p = args[1].get();
        if (j == null || p == null) {
            return null;
        }
        String json = String.valueOf(j);

        final Object root;
        try {
            root = JsonSupport.parse(json);
        } catch (JsonSyntaxException e) {
            return null; // 非法 JSON（数据问题）宽容处理
        }

        JsonPathSupport.Resolved resolved = JsonPathSupport.resolve(root, String.valueOf(p)); // path 非法直接抛出
        return resolved.exists();
    }

    @Override
    public String getDisplayString(String[] children) {
        return "json_exists(" + (children.length > 0 ? children[0] : "?")
                + ", " + (children.length > 1 ? children[1] : "?") + ")";
    }
}
