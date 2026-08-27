package com.liangpu.udf;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.exec.UDFArgumentTypeException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.spark.sql.catalyst.expressions.ExpressionDescription;

import java.util.regex.Pattern;

/**
 * keyvalue(str, key[, split1, split2])：从半结构化 kv 串中提取指定 key 的值
 * （对齐 MaxCompute KEYVALUE）。
 *
 * <p>两种形式：</p>
 * <ul>
 *   <li>2 参 keyvalue(str, key)：默认分隔符 & 分隔键值对、= 分隔 key/value</li>
 *   <li>4 参 keyvalue(str, split1, split2, key)：自定义分隔符（split1 为键值对分隔符、
 *       split2 为 key/value 分隔符）</li>
 * </ul>
 *
 * <p>边界：key 不存在 / 分隔符为 NULL 或空串 → NULL；str 或 key 为 SQL NULL → NULL；
 * 重复 key 取第一个匹配；无 '=' 的段跳过；空 value（如 k=）返回空串。</p>
 *
 * <p>调用：</p>
 * <pre>
 * SELECT keyvalue('k1=v1&amp;k2=v2', 'k2');                    -- v2
 * SELECT keyvalue('k1:v1;k2:v2', ';', ':', 'k2');           -- v2
 * </pre>
 */
@ExpressionDescription(
        usage = "keyvalue(str, key[, split1, split2]) - 从半结构化 kv 串中提取指定 key 的值，2 参用默认分隔符（& 与 =），4 参自定义 split1/split2；key 不存在返回 NULL。",
        arguments = "str - 半结构化字符串，如 'k1=v1&k2=v2'\nkey - 要提取的 key 名（2 参形式第 2 参）\nsplit1 - 键值对分隔符（4 参形式，默认 &）\nsplit2 - key/value 分隔符（4 参形式，默认 =）")
public class KeyvalueUdf extends GenericUDF {

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length != 2 && args.length != 4) {
            throw new UDFArgumentException("keyvalue 需要 2 或 4 个参数: keyvalue(str, key) 或 keyvalue(str, split1, split2, key)，实际 " + args.length);
        }
        for (int i = 0; i < args.length; i++) {
            if (!isStringLike(args[i])) {
                throw new UDFArgumentTypeException(i, "keyvalue 参数 " + i + " 必须为字符串");
            }
        }
        return PrimitiveObjectInspectorFactory.javaStringObjectInspector;
    }

    @Override
    public Object evaluate(DeferredObject[] args) throws HiveException {
        Object s = args[0].get();
        if (s == null) {
            return null;
        }
        String str = String.valueOf(s);
        String split1 = "&";
        String split2 = "=";
        String key;
        if (args.length == 2) {
            Object k = args[1].get();
            if (k == null) {
                return null;
            }
            key = String.valueOf(k);
        } else {
            Object s1 = args[1].get();
            Object s2 = args[2].get();
            Object k = args[3].get();
            if (s1 == null || s2 == null || k == null) {
                return null;
            }
            split1 = String.valueOf(s1);
            split2 = String.valueOf(s2);
            key = String.valueOf(k);
        }
        if (split1.isEmpty() || split2.isEmpty()) {
            return null; // 分隔符不合法（数据问题）宽容处理
        }
        for (String pair : str.split(Pattern.quote(split1), -1)) {
            String[] kv = pair.split(Pattern.quote(split2), 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return kv[1]; // 重复 key 取第一个匹配
            }
        }
        return null; // key 不存在
    }

    /** 字符串或 NULL 字面量（VOID，引擎中 NULL 常量类型）放行。 */
    private static boolean isStringLike(ObjectInspector oi) {
        if (oi.getCategory() != ObjectInspector.Category.PRIMITIVE) {
            return false;
        }
        PrimitiveObjectInspector.PrimitiveCategory cat = ((PrimitiveObjectInspector) oi).getPrimitiveCategory();
        return cat == PrimitiveObjectInspector.PrimitiveCategory.STRING
                || cat == PrimitiveObjectInspector.PrimitiveCategory.VOID;
    }

    @Override
    public String getDisplayString(String[] children) {
        StringBuilder sb = new StringBuilder("keyvalue(");
        for (int i = 0; i < children.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(children[i]);
        }
        return sb.append(")").toString();
    }
}
