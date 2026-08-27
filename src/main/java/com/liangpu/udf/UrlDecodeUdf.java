package com.liangpu.udf;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.exec.UDFArgumentTypeException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.spark.sql.catalyst.expressions.ExpressionDescription;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/**
 * url_decode(str)：URL 百分号解码（对齐 MaxCompute URL_DECODE，url_encode 的逆操作）。
 *
 * <p>契约：JDK URLDecoder，UTF-8——+→空格、%XX 按 UTF-8 还原。</p>
 *
 * <p>边界：str 为 SQL NULL → NULL；非法百分号序列（如 %zz、截断的 %，数据问题）
 * → NULL（捕获 IllegalArgumentException，数据宽容）。</p>
 *
 * <p>调用：</p>
 * <pre>
 * SELECT url_decode('a+b');              -- a b
 * SELECT url_decode('%E4%B8%AD');        -- 中
 * </pre>
 */
@ExpressionDescription(
        usage = "url_decode(str) - URL 百分号解码（url_encode 逆操作）：+→空格、%XX 按 UTF-8 还原；非法百分号序列返回 NULL。",
        arguments = "str - 待解码字符串")
public class UrlDecodeUdf extends GenericUDF {

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length != 1) {
            throw new UDFArgumentException("url_decode 需要 1 个参数: url_decode(str)，实际 " + args.length);
        }
        if (args[0].getCategory() != ObjectInspector.Category.PRIMITIVE
                || (((PrimitiveObjectInspector) args[0]).getPrimitiveCategory()
                != PrimitiveObjectInspector.PrimitiveCategory.STRING
                && ((PrimitiveObjectInspector) args[0]).getPrimitiveCategory()
                != PrimitiveObjectInspector.PrimitiveCategory.VOID)) { // NULL 字面量宽容
            throw new UDFArgumentTypeException(0, "url_decode 参数必须为字符串");
        }
        return PrimitiveObjectInspectorFactory.javaStringObjectInspector;
    }

    @Override
    public Object evaluate(DeferredObject[] args) throws HiveException {
        Object v = args[0].get();
        if (v == null) {
            return null;
        }
        try {
            return URLDecoder.decode(String.valueOf(v), "UTF-8");
        } catch (IllegalArgumentException e) {
            return null; // 非法百分号序列（数据问题）宽容处理
        } catch (UnsupportedEncodingException e) {
            throw new HiveException("UTF-8 编码不可用", e); // 理论不可达
        }
    }

    @Override
    public String getDisplayString(String[] children) {
        return "url_decode(" + (children.length > 0 ? children[0] : "") + ")";
    }
}
