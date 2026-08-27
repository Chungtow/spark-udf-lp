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
import java.net.URLEncoder;

/**
 * url_encode(str)：URL 百分号编码（对齐 MaxCompute URL_ENCODE）。
 *
 * <p>契约：application/x-www-form-urlencoded（JDK URLEncoder，UTF-8）——
 * 空格→+、字母数字与 -_.* 保留、~→%7E、非 ASCII 按 UTF-8 字节百分号编码。
 * 禁止手工按 char 直接编码（POC 实证产出错误字节序列）。</p>
 *
 * <p>边界：str 为 SQL NULL → NULL。</p>
 *
 * <p>调用：</p>
 * <pre>
 * SELECT url_encode('a b');              -- a+b
 * SELECT url_encode('中文');              -- %E4%B8%AD%E6%96%87
 * </pre>
 */
@ExpressionDescription(
        usage = "url_encode(str) - URL 百分号编码（x-www-form-urlencoded）：空格→+、-_.* 保留、~→%7E、非 ASCII 按 UTF-8 字节编码。",
        arguments = "str - 待编码字符串")
public class UrlEncodeUdf extends GenericUDF {

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length != 1) {
            throw new UDFArgumentException("url_encode 需要 1 个参数: url_encode(str)，实际 " + args.length);
        }
        if (args[0].getCategory() != ObjectInspector.Category.PRIMITIVE
                || (((PrimitiveObjectInspector) args[0]).getPrimitiveCategory()
                != PrimitiveObjectInspector.PrimitiveCategory.STRING
                && ((PrimitiveObjectInspector) args[0]).getPrimitiveCategory()
                != PrimitiveObjectInspector.PrimitiveCategory.VOID)) { // NULL 字面量宽容
            throw new UDFArgumentTypeException(0, "url_encode 参数必须为字符串");
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
            return URLEncoder.encode(String.valueOf(v), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // UTF-8 必然支持，理论不可达
            throw new HiveException("UTF-8 编码不可用", e);
        }
    }

    @Override
    public String getDisplayString(String[] children) {
        return "url_encode(" + (children.length > 0 ? children[0] : "") + ")";
    }
}
