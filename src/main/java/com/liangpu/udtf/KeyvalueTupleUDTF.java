package com.liangpu.udtf;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.exec.UDFArgumentTypeException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDTF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.StructObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.spark.sql.catalyst.expressions.ExpressionDescription;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * keyvalue_tuple(str, split1, split2, key1, key2, ...)：半结构化 kv 串多键一次提取（UDTF）
 * （对齐 MaxCompute KEYVALUE_TUPLE）。
 *
 * <p>语义：按 split1 拆键值对、split2 拆 key/value，每 key 输出一列，列序与参数一致；
 * 找不到的 key 为 NULL；重复 key 取第一个匹配。</p>
 *
 * <p>边界：str / split1 / split2 为 SQL NULL 或分隔符为空串 → 0 行；str 非 kv 结构
 * （段内无 split2）→ 该段跳过，全部跳过则输出 1 行全 NULL。</p>
 *
 * <p>调用（输出列名固定为 k1, k2, ...，可用 AS 重命名）：</p>
 * <pre>
 * SELECT kt.k1, kt.k2
 *   FROM (SELECT 1) t
 *   LATERAL VIEW lpudf.keyvalue_tuple('k1=v1&amp;k2=v2', '&amp;', '=', 'k1', 'k2') kt AS k1, k2;
 * </pre>
 */
@ExpressionDescription(
        usage = "keyvalue_tuple(str, split1, split2, key1, key2, ...) - 半结构化 kv 串多键一次提取（UDTF）：每 key 一列，列序与参数一致，找不到的 key 为 NULL；str 为 NULL 或非 kv 结构输出 0 行。",
        arguments = "str - 半结构化字符串，如 'k1=v1&k2=v2'\nsplit1 - 键值对分隔符，如 '&'\nsplit2 - key/value 分隔符，如 '='\nkey1..keyN - 至少 1 个 key（第 4 参起），输出一列对应一个 key")
public class KeyvalueTupleUDTF extends GenericUDTF {

    private int keyCount;

    @Override
    public StructObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length < 4) {
            throw new UDFArgumentException("keyvalue_tuple 至少需要 4 个参数: keyvalue_tuple(str, split1, split2, key1, ...)，实际 " + args.length);
        }
        for (int i = 0; i < args.length; i++) {
            if (ObjectInspector.Category.PRIMITIVE != args[i].getCategory()) {
                throw new UDFArgumentTypeException(i, "keyvalue_tuple 参数 " + i + " 必须为字符串");
            }
            PrimitiveObjectInspector.PrimitiveCategory cat = ((PrimitiveObjectInspector) args[i]).getPrimitiveCategory();
            if (cat != PrimitiveObjectInspector.PrimitiveCategory.STRING
                    && cat != PrimitiveObjectInspector.PrimitiveCategory.VOID) { // NULL 字面量宽容
                throw new UDFArgumentTypeException(i, "keyvalue_tuple 参数 " + i + " 必须为字符串");
            }
        }
        keyCount = args.length - 3;
        List<String> names = new ArrayList<String>();
        List<ObjectInspector> fieldOIs = new ArrayList<ObjectInspector>();
        for (int i = 0; i < keyCount; i++) {
            names.add("k" + (i + 1)); // 输出列名固定 k1..kN，可用 AS 重命名
            fieldOIs.add(PrimitiveObjectInspectorFactory.javaStringObjectInspector);
        }
        return ObjectInspectorFactory.getStandardStructObjectInspector(names, fieldOIs);
    }

    @Override
    public void process(Object[] record) throws HiveException {
        Object s = record[0];
        Object s1 = record[1];
        Object s2 = record[2];
        if (s == null || s1 == null || s2 == null) {
            return; // NULL → 0 行
        }
        String str = String.valueOf(s);
        String split1 = String.valueOf(s1);
        String split2 = String.valueOf(s2);
        if (split1.isEmpty() || split2.isEmpty()) {
            return; // 分隔符不合法 → 0 行
        }
        Map<String, String> kv = new LinkedHashMap<String, String>();
        for (String pair : str.split(Pattern.quote(split1), -1)) {
            String[] parts = pair.split(Pattern.quote(split2), 2);
            if (parts.length == 2 && !kv.containsKey(parts[0])) {
                kv.put(parts[0], parts[1]); // 重复 key 取第一个匹配
            }
        }
        Object[] out = new Object[keyCount];
        for (int i = 0; i < keyCount; i++) {
            Object k = record[3 + i];
            out[i] = (k == null) ? null : kv.get(String.valueOf(k));
        }
        forward(out);
    }

    @Override
    public void close() throws HiveException {
        // 无资源需释放
    }
}
