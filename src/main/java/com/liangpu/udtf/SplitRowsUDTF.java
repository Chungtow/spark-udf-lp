package com.liangpu.udtf;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDTF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.StructField;
import org.apache.hadoop.hive.serde2.objectinspector.StructObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.spark.sql.catalyst.expressions.ExpressionDescription;

/**
 * 示例 UDTF：字符串按分隔符拆分为多行（一进多出，单列）。
 *
 * <p>注册（写入 lpudf 库，与其他 UDF/UDAF/UDTF 共用同一 jar）：</p>
 * <pre>
 * CREATE OR REPLACE FUNCTION lpudf.udtf_split_rows AS 'com.liangpu.udtf.SplitRowsUDTF'
 *   USING JAR 'hdfs://mycluster/udf/spark-udf-lp-&lt;VER&gt;.jar';
 * </pre>
 *
 * <p>调用（STS/Dinky，Spark 3.3 需 LATERAL VIEW）：</p>
 * <pre>
 * SELECT x FROM LATERAL VIEW lpudf.udtf_split_rows('a,b,c', ',') t AS x;
 * -- 输出 3 行: a / b / c
 * </pre>
 *
 * <p>边界约定：输入为 NULL 或空串时无输出；分隔符为 NULL/空时默认逗号；
 * 分隔符按正则字面量匹配（Pattern.quote），且保留尾部分隔产生的空项（split -1）。</p>
 */
@ExpressionDescription(
        usage = "udtf_split_rows(str, delim) - 字符串按分隔符拆分为多行（单列输出）；NULL/空串无输出，分隔符 NULL/空时默认逗号。",
        arguments = "str - 待拆分的字符串\ndelim - 分隔符（字面量匹配）")
public class SplitRowsUDTF extends GenericUDTF {

    private transient PrimitiveObjectInspector inputOI0; // 待拆字符串
    private transient PrimitiveObjectInspector inputOI1; // 分隔符
    private transient List<String> fieldNames;
    private transient List<ObjectInspector> fieldOIs;

    @Override
    public StructObjectInspector initialize(StructObjectInspector argOIs) throws UDFArgumentException {
        List<? extends StructField> fields = argOIs.getAllStructFieldRefs();
        if (fields.size() != 2) {
            throw new UDFArgumentException("udtf_split_rows 需要 2 个参数: (string, delimiter)");
        }
        inputOI0 = (PrimitiveObjectInspector) fields.get(0).getFieldObjectInspector();
        inputOI1 = (PrimitiveObjectInspector) fields.get(1).getFieldObjectInspector();

        fieldNames = new ArrayList<String>();
        fieldOIs = new ArrayList<ObjectInspector>();
        fieldNames.add("item");
        fieldOIs.add(PrimitiveObjectInspectorFactory.javaStringObjectInspector);
        return ObjectInspectorFactory.getStandardStructObjectInspector(fieldNames, fieldOIs);
    }

    @Override
    public void process(Object[] record) throws HiveException {
        if (record == null || record.length < 2 || record[0] == null) {
            return; // NULL 输入无输出
        }
        String str = String.valueOf(inputOI0.getPrimitiveJavaObject(record[0]));
        if (str.isEmpty()) {
            return;
        }
        String delim = record[1] == null
                ? ","
                : String.valueOf(inputOI1.getPrimitiveJavaObject(record[1]));
        if (delim.isEmpty()) {
            delim = ",";
        }

        String[] parts = str.split(Pattern.quote(delim), -1);
        for (String p : parts) {
            forward(new Object[]{p});
        }
    }

    @Override
    public void close() throws HiveException {
        // 无资源需释放
    }
}
