package com.liangpu.udf;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.exec.UDFArgumentTypeException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorUtils;
import org.apache.spark.sql.catalyst.expressions.ExpressionDescription;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * regexp_count(str, pattern[, fromPos])：统计正则匹配次数（对齐 MaxCompute REGEXP_COUNT）。
 *
 * <p>契约：fromPos 为起始匹配位置（1-based，默认 1，POC 实证从位置 3 起 'a1b2c3' 计数 = 2）；
 * 无匹配返回 0；fromPos &lt;= 0 或 &gt; 字符串长度不抛错、返回 0；pattern 语法非法为写法错误 → 抛错。</p>
 *
 * <p>边界：str 或 pattern 为 SQL NULL → NULL。</p>
 *
 * <p>调用：</p>
 * <pre>
 * SELECT regexp_count('a1b2c3', '[0-9]');       -- 3
 * SELECT regexp_count('a1b2c3', '[0-9]', 3);    -- 2
 * </pre>
 */
@ExpressionDescription(
        usage = "regexp_count(str, pattern[, fromPos]) - 统计正则匹配次数；fromPos 为起始位置（1-based，默认 1）；无匹配返回 0，fromPos 越界返回 0。",
        arguments = "str - 源字符串\npattern - 正则表达式（Java Pattern 语法）\nfromPos - 起始匹配位置（1-based，可选，默认 1）")
public class RegexpCountUdf extends GenericUDF {

    private transient PrimitiveObjectInspector fromPosOI; // 第 3 参（fromPos）OI

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length != 2 && args.length != 3) {
            throw new UDFArgumentException("regexp_count 需要 2 或 3 个参数: regexp_count(str, pattern[, fromPos])，实际 " + args.length);
        }
        checkString(args[0], 0);
        checkString(args[1], 1);
        if (args.length == 3) {
            checkInt(args[2], 2);
            fromPosOI = (PrimitiveObjectInspector) args[2];
        }
        return PrimitiveObjectInspectorFactory.javaIntObjectInspector;
    }

    @Override
    public Object evaluate(DeferredObject[] args) throws HiveException {
        Object s = args[0].get();
        Object p = args[1].get();
        if (s == null || p == null) {
            return null;
        }
        String str = String.valueOf(s);
        String pattern = String.valueOf(p);
        int fromPos = 1;
        if (args.length == 3) {
            if (args[2].get() == null) {
                return null; // fromPos 为 NULL → NULL
            }
            fromPos = PrimitiveObjectInspectorUtils.getInt(args[2].get(), fromPosOI);
        }
        if (fromPos <= 0) {
            return 0; // 起始位置非法 → 0，不抛错
        }
        Matcher m;
        try {
            m = Pattern.compile(pattern).matcher(str);
        } catch (PatternSyntaxException e) {
            throw new HiveException("regexp_count 正则语法非法: " + pattern, e);
        }
        int start = Math.min(fromPos - 1, str.length());
        m.region(start, str.length());
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }

    private void checkString(ObjectInspector oi, int idx) throws UDFArgumentException {
        if (oi.getCategory() != ObjectInspector.Category.PRIMITIVE
                || (((PrimitiveObjectInspector) oi).getPrimitiveCategory()
                != PrimitiveObjectInspector.PrimitiveCategory.STRING
                && ((PrimitiveObjectInspector) oi).getPrimitiveCategory()
                != PrimitiveObjectInspector.PrimitiveCategory.VOID)) { // NULL 字面量宽容
            throw new UDFArgumentTypeException(idx, "regexp_count 参数 " + idx + " 必须为字符串");
        }
    }

    private void checkInt(ObjectInspector oi, int idx) throws UDFArgumentException {
        if (oi.getCategory() != ObjectInspector.Category.PRIMITIVE) {
            throw new UDFArgumentTypeException(idx, "regexp_count 参数 " + idx + " 必须为整数");
        }
        PrimitiveObjectInspector.PrimitiveCategory cat = ((PrimitiveObjectInspector) oi).getPrimitiveCategory();
        if (cat != PrimitiveObjectInspector.PrimitiveCategory.INT
                && cat != PrimitiveObjectInspector.PrimitiveCategory.LONG
                && cat != PrimitiveObjectInspector.PrimitiveCategory.VOID) { // NULL 字面量宽容
            throw new UDFArgumentTypeException(idx, "regexp_count 参数 " + idx + " 必须为整数");
        }
    }

    @Override
    public String getDisplayString(String[] children) {
        StringBuilder sb = new StringBuilder("regexp_count(");
        for (int i = 0; i < children.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(children[i]);
        }
        return sb.append(")").toString();
    }
}
