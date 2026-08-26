package com.liangpu.udf;

import com.liangpu.json.JsonPathException;
import com.liangpu.json.JsonPathSupport;
import com.liangpu.json.JsonSupport;
import com.liangpu.json.JsonSyntaxException;
import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.spark.sql.catalyst.expressions.ExpressionDescription;

/**
 * json_extract(json, path)：按 JSONPath 提取值，返回 JSON 文本（对齐 MaxCompute JSON_EXTRACT）。
 *
 * <p>path 支持子集见 {@link JsonPathParser}（ADR-1）。边界：</p>
 * <ul>
 *   <li>path 合法但目标不存在 → NULL</li>
 *   <li>目标存在但值为 JSON null → {@code "null"}（四字符文本，与"不存在"区分）</li>
 *   <li>path 语法非法 → 抛 {@link JsonPathException}（写法严格，对齐 MC 报错）</li>
 *   <li>非法 JSON 文本 / SQL NULL 入参 → NULL（数据宽容）</li>
 * </ul>
 *
 * <p>调用：</p>
 * <pre>
 * SELECT json_extract('{"a":{"b":3}}', '$.a.b'); -- 3
 * SELECT json_extract('[1,2,{"a":34}]', '$[2].a'); -- 34
 * </pre>
 */
@ExpressionDescription(
        usage = "json_extract(json, path) - 按 JSONPath 提取值并返回 JSON 文本；path 支持 $.key / $[n] / $['key'] 子集，目标不存在返回 NULL，path 语法非法抛错，非法 JSON 返回 NULL。",
        arguments = "json - JSON 文本\npath - JSONPath 表达式，如 '$.a'、'$.a[0]'")
public class JsonExtractUdf extends GenericUDF {

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length != 2) {
            throw new UDFArgumentException("json_extract 需要 2 个参数 (json, path)，实际 " + args.length);
        }
        return PrimitiveObjectInspectorFactory.javaStringObjectInspector;
    }

    @Override
    public Object evaluate(DeferredObject[] args) throws HiveException {
        Object j = args[0].get();
        Object p = args[1].get();
        if (j == null || p == null) {
            return null;
        }
        String json = String.valueOf(j);
        String path = String.valueOf(p);

        final Object root;
        try {
            root = JsonSupport.parse(json);
        } catch (JsonSyntaxException e) {
            return null; // 非法 JSON（数据问题）宽容处理
        }

        JsonPathSupport.Resolved resolved = JsonPathSupport.resolve(root, path); // path 非法直接抛出
        if (!resolved.exists()) {
            return null;
        }
        return JsonSupport.toJsonString(resolved.value());
    }

    @Override
    public String getDisplayString(String[] children) {
        return "json_extract(" + (children.length > 0 ? children[0] : "?")
                + ", " + (children.length > 1 ? children[1] : "?") + ")";
    }
}
