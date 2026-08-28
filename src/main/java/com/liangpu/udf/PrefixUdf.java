package com.liangpu.udf;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.spark.sql.catalyst.expressions.ExpressionDescription;

/**
 * 示例 UDF：字符串前缀截断（截取前 4 个字符）。
 *
 * <p>注册方式：函数经 SparkSessionExtensions 会话级注入（ADR-9，详见
 * README「注意事项」），禁止手动 DROP/CREATE FUNCTION。</p>
 *
 * <p>新增 UDF 的完整流程（含注册三连同步：父仓库测试槽 udf-manifest.txt /
 * {@link com.liangpu.help.LpudfFunctionRegistry} / 单测 EXPECTED_NAMES，
 * 以及构建发布步骤）见 README「新增一个 UDF」。</p>
 */
@ExpressionDescription(
        usage = "udf_prefix(str) - 返回字符串前 4 个字符（示例 UDF）；str 为 NULL 时返回 NULL。",
        arguments = "str - 字符串表达式")
public class PrefixUdf extends GenericUDF {

    public static final int PREFIX_LEN = 4;

    /**
     * 校验入参并声明返回类型。
     *
     * @param args 入参 ObjectInspector 数组（长度应为 1）
     * @return 返回值的 ObjectInspector（String）
     */
    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length != 1) {
            throw new UDFArgumentException("udf_prefix 需要 1 个参数，实际 " + args.length);
        }
        return PrimitiveObjectInspectorFactory.javaStringObjectInspector;
    }

    /**
     * 核心逻辑：截取字符串前 4 个字符；null 透传返回 null。
     */
    @Override
    public Object evaluate(DeferredObject[] args) throws HiveException {
        Object value = args[0].get();
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value);
        if (s.length() <= PREFIX_LEN) {
            return s;
        }
        return s.substring(0, PREFIX_LEN);
    }

    @Override
    public String getDisplayString(String[] children) {
        return "udf_prefix(" + (children.length > 0 ? children[0] : "?") + ")";
    }
}
