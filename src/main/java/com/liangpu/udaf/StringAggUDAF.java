package com.liangpu.udaf;

import org.apache.hadoop.hive.ql.exec.UDFArgumentTypeException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.parse.SemanticException;
import org.apache.hadoop.hive.ql.udf.generic.AbstractGenericUDAFResolver;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFParameterInfo;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.io.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 示例 UDAF：字符串列聚合去重拼接。
 *
 * <p>语义：把一列字符串值聚合为逗号分隔的结果，去重并按字典序排序输出（分布式聚合输入顺序不确定，
 * 排序保证结果确定性）；NULL 行忽略。</p>
 *
 * <p>注册（写入 lpudf 库，与其他 UDF/UDAF/UDTF 共用同一 jar）：</p>
 * <pre>
 * CREATE OR REPLACE FUNCTION lpudf.udaf_string_agg AS 'com.liangpu.udaf.StringAggUDAF'
 *   USING JAR 'hdfs://mycluster/udf/spark-udf-lp-&lt;VER&gt;.jar';
 * </pre>
 *
 * <p>调用（STS/Dinky）：</p>
 * <pre>
 * SELECT k, lpudf.udaf_string_agg(v) FROM t GROUP BY k;
 * -- 例: k=a, v 依次为 b,a,b -> 结果 "b,a"
 * </pre>
 *
 * <p>实现要点（Hive GenericUDAF 四阶段）：</p>
 * <ul>
 *   <li>COMPLETE / PARTIAL1: iterate 收集 → terminate / terminatePartial；</li>
 *   <li>PARTIAL2 / FINAL: merge 合并分片 → terminate；</li>
 *   <li>partial 分片用 {@code \u0001}（Hive 不可见分隔符）连接为 Text，避免值内含逗号歧义。</li>
 * </ul>
 */
public class StringAggUDAF extends AbstractGenericUDAFResolver {

    @Override
    public GenericUDAFEvaluator getEvaluator(GenericUDAFParameterInfo info) throws SemanticException {
        if (info.getParameters().length != 1) {
            throw new UDFArgumentTypeException(0,
                    "udaf_string_agg 需要 1 个字符串参数，实际 " + info.getParameters().length);
        }
        return new StringAggEvaluator();
    }

    public static class StringAggEvaluator extends GenericUDAFEvaluator {

        /** 分片间分隔符（不可见字符，避免与聚合值冲突）。 */
        private static final String PARTIAL_SEP = "\u0001";

        private transient PrimitiveObjectInspector inputOI;

        @Override
        public ObjectInspector init(Mode m, ObjectInspector[] parameters) throws HiveException {
            super.init(m, parameters);
            if (parameters.length > 0) {
                inputOI = (PrimitiveObjectInspector) parameters[0];
            }
            // 输出统一用 Text（writable），与 terminate / terminatePartial 返回类型一致
            return PrimitiveObjectInspectorFactory.writableStringObjectInspector;
        }

        /** 聚合缓冲：去重有序集合。 */
        public static class StringAggBuffer implements AggregationBuffer {
            private final LinkedHashSet<String> values = new LinkedHashSet<String>();
        }

        @Override
        public AggregationBuffer getNewAggregationBuffer() throws HiveException {
            return new StringAggBuffer();
        }

        @Override
        public void reset(AggregationBuffer agg) throws HiveException {
            ((StringAggBuffer) agg).values.clear();
        }

        @Override
        public void iterate(AggregationBuffer agg, Object[] parameters) throws HiveException {
            if (parameters == null || parameters.length == 0 || parameters[0] == null) {
                return; // NULL 忽略（与 count(col) 语义一致）
            }
            Object raw = inputOI.getPrimitiveJavaObject(parameters[0]);
            if (raw == null) {
                return;
            }
            ((StringAggBuffer) agg).values.add(String.valueOf(raw));
        }

        @Override
        public Object terminatePartial(AggregationBuffer agg) throws HiveException {
            List<String> raw = new ArrayList<String>(((StringAggBuffer) agg).values);
            return new Text(join(raw, PARTIAL_SEP));
        }

        @Override
        public void merge(AggregationBuffer agg, Object partial) throws HiveException {
            if (partial == null) {
                return;
            }
            String p = partial.toString();
            if (p.isEmpty()) {
                return;
            }
            for (String v : p.split(PARTIAL_SEP, -1)) {
                if (!v.isEmpty()) {
                    ((StringAggBuffer) agg).values.add(v);
                }
            }
        }

        @Override
        public Object terminate(AggregationBuffer agg) throws HiveException {
            // 分布式聚合下 iterate 顺序不确定，输出按字典序排序保证确定性
            List<String> sorted = new ArrayList<String>(((StringAggBuffer) agg).values);
            Collections.sort(sorted);
            return new Text(join(sorted, ","));
        }

        private static String join(List<String> values, String sep) {
            StringBuilder sb = new StringBuilder();
            for (String v : values) {
                if (sb.length() > 0) {
                    sb.append(sep);
                }
                sb.append(v);
            }
            return sb.toString();
        }
    }
}
