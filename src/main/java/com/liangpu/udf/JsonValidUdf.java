package com.liangpu.udf;

import com.liangpu.json.JsonSupport;
import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;

/**
 * json_valid(str)：校验输入字符串是否为合法 JSON（RFC 8259），返回 true / false。
 *
 * <p>对齐 MaxCompute JSON_VALID。合法 JSON（对象/数组/字符串/数字/布尔/null 字面量）→ true；
 * 裸词（未加引号的字符串，如 {@code abc}）→ false；SQL NULL 入参 → NULL。</p>
 *
 * <p>调用（lpudf 库内裸名；跨库 lpudf.json_valid）：</p>
 * <pre>
 * SELECT json_valid('{"a":1}');  -- true
 * SELECT json_valid('abc');      -- false
 * </pre>
 */
public class JsonValidUdf extends GenericUDF {

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length != 1) {
            throw new UDFArgumentException("json_valid 需要 1 个参数 (json)，实际 " + args.length);
        }
        return PrimitiveObjectInspectorFactory.javaBooleanObjectInspector;
    }

    @Override
    public Object evaluate(DeferredObject[] args) throws HiveException {
        Object value = args[0].get();
        if (value == null) {
            return null;
        }
        return JsonSupport.isValid(String.valueOf(value));
    }

    @Override
    public String getDisplayString(String[] children) {
        return "json_valid(" + (children.length > 0 ? children[0] : "?") + ")";
    }
}
