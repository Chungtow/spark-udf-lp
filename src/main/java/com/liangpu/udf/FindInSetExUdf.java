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
 * find_in_set_ex(str, str_list[, delimiter])：返回 str 在分隔符列表中首次出现的位置
 * （1-based；MC find_in_set 的增强：支持自定义分隔符，换名避免与内置冲突，ADR-12）。
 *
 * <p>契约：默认分隔符为逗号（与内置 find_in_set 一致）；精确匹配（不 trim）；
 * 找不到返回 0；str 或 str_list 为 SQL NULL → 0（用户确认的契约，与内置 find_in_set 行为对齐）；
 * delimiter 为 NULL 或空串 → 使用默认逗号。</p>
 *
 * <p>调用：</p>
 * <pre>
 * SELECT find_in_set_ex('b', 'a,b,c');        -- 2
 * SELECT find_in_set_ex('b', 'a;b;c', ';');   -- 2
 * </pre>
 */
@ExpressionDescription(
        usage = "find_in_set_ex(str, str_list[, delimiter]) - 返回 str 在 str_list 中的位置（1-based）；找不到返回 0；支持第 3 参自定义分隔符（默认逗号）；str 或 str_list 为 NULL 返回 0。",
        arguments = "str - 要查找的字符串\nstr_list - 由分隔符连接的元素列表，如 'a,b,c'\ndelimiter - 自定义分隔符（可选，默认逗号）")
public class FindInSetExUdf extends GenericUDF {

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length != 2 && args.length != 3) {
            throw new UDFArgumentException("find_in_set_ex 需要 2 或 3 个参数: find_in_set_ex(str, str_list[, delimiter])，实际 " + args.length);
        }
        for (int i = 0; i < args.length; i++) {
            if (args[i].getCategory() != ObjectInspector.Category.PRIMITIVE) {
                throw new UDFArgumentTypeException(i, "find_in_set_ex 参数 " + i + " 必须为字符串");
            }
            PrimitiveObjectInspector.PrimitiveCategory cat = ((PrimitiveObjectInspector) args[i]).getPrimitiveCategory();
            if (cat != PrimitiveObjectInspector.PrimitiveCategory.STRING
                    && cat != PrimitiveObjectInspector.PrimitiveCategory.VOID) { // NULL 字面量宽容
                throw new UDFArgumentTypeException(i, "find_in_set_ex 参数 " + i + " 必须为字符串");
            }
        }
        return PrimitiveObjectInspectorFactory.javaIntObjectInspector;
    }

    @Override
    public Object evaluate(DeferredObject[] args) throws HiveException {
        Object s = args[0].get();
        Object list = args[1].get();
        if (s == null || list == null) {
            return 0; // NULL → 0（与内置 find_in_set 行为对齐）
        }
        String str = String.valueOf(s);
        String strList = String.valueOf(list);
        String delimiter = ",";
        if (args.length == 3) {
            Object d = args[2].get();
            if (d != null && !String.valueOf(d).isEmpty()) {
                delimiter = String.valueOf(d);
            }
        }
        String[] elements = strList.split(Pattern.quote(delimiter), -1);
        for (int i = 0; i < elements.length; i++) {
            if (elements[i].equals(str)) {
                return i + 1; // 1-based，取首次出现
            }
        }
        return 0; // 找不到
    }

    @Override
    public String getDisplayString(String[] children) {
        StringBuilder sb = new StringBuilder("find_in_set_ex(");
        for (int i = 0; i < children.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(children[i]);
        }
        return sb.append(")").toString();
    }
}
