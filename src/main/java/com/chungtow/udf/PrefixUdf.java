package com.chungtow.udf;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;

/**
 * 示例 UDF：字符串前缀截断（截取前 4 个字符）。
 *
 * <p>注册（写入 Hive Metastore 永久函数，STS 重启后依然可用）：</p>
 * <pre>
 * CREATE OR REPLACE FUNCTION udf_prefix AS 'com.chungtow.udf.PrefixUdf'
 *   USING JAR 'hdfs://mycluster/udf/spark-udf-lp-&lt;VER&gt;.jar';
 * </pre>
 *
 * <p>新增 UDF 规范：</p>
 * <ol>
 *   <li>在 com.chungtow.udf 包下新增类，继承 {@link GenericUDF}；</li>
 *   <li>新增对应 JUnit 单测（src/test/java）；</li>
 *   <li>在 scripts/udf-manifest.txt 追加一行：注册名|类名；</li>
 *   <li>重新构建发布：bash build.sh &lt;VER&gt; + scripts/deploy_spark_udf_lp.sh &lt;VER&gt;。</li>
 * </ol>
 */
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
