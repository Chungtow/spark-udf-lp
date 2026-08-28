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
 * regexp_substr(str, pattern[, fromPos[, occurrence]])：返回正则匹配的子串
 * （对齐 MaxCompute REGEXP_SUBSTR）。
 *
 * <p>契约：fromPos 为起始位置（1-based，默认 1）；occurrence 为第几次出现（默认 1）；
 * 无匹配返回 NULL；fromPos &lt;= 0 或 &gt; 长度 / occurrence &lt;= 0 → NULL（不抛错）。</p>
 *
 * <p>边界：str 或 pattern 为 SQL NULL → NULL；pattern 语法非法为写法错误 → 抛错。</p>
 *
 * <p>调用：</p>
 * <pre>
 * SELECT regexp_substr('abc123def456', '[0-9]+');           -- 123
 * SELECT regexp_substr('abc123def456', '[0-9]+', 1, 2);     -- 456
 * </pre>
 */
@ExpressionDescription(
        usage = "regexp_substr(str, pattern[, fromPos[, occurrence]]) - 返回正则匹配的子串；fromPos 起始位置（1-based），occurrence 第几次出现；无匹配返回 NULL。",
        arguments = "str - 源字符串\npattern - 正则表达式（Java Pattern 语法）\nfromPos - 起始匹配位置（可选，默认 1）\noccurrence - 第几次出现（可选，默认 1）")
public class RegexpSubstrUdf extends GenericUDF {

    private transient PrimitiveObjectInspector fromPosOI;   // 第 3 参（fromPos）OI
    private transient PrimitiveObjectInspector occurrenceOI; // 第 4 参（occurrence）OI

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length < 2 || args.length > 4) {
            throw new UDFArgumentException("regexp_substr 需要 2~4 个参数: regexp_substr(str, pattern[, fromPos[, occurrence]])，实际 " + args.length);
        }
        checkString(args[0], 0);
        checkString(args[1], 1);
        for (int i = 2; i < args.length; i++) {
            checkInt(args[i], i);
        }
        if (args.length >= 3) {
            fromPosOI = (PrimitiveObjectInspector) args[2];
        }
        if (args.length >= 4) {
            occurrenceOI = (PrimitiveObjectInspector) args[3];
        }
        return PrimitiveObjectInspectorFactory.javaStringObjectInspector;
    }

    @Override
    public Object evaluate(DeferredObject[] args) throws HiveException {
        Object s = args[0].get();
        Object p = args[1].get();
        if (s == null || p == null) {
            return null;
        }
        String str = String.valueOf(s);
        String patternStr = String.valueOf(p);
        int fromPos = 1;
        int occurrence = 1;
        if (args.length >= 3) {
            if (args[2].get() == null) {
                return null;
            }
            fromPos = PrimitiveObjectInspectorUtils.getInt(args[2].get(), fromPosOI);
        }
        if (args.length >= 4) {
            if (args[3].get() == null) {
                return null;
            }
            occurrence = PrimitiveObjectInspectorUtils.getInt(args[3].get(), occurrenceOI);
        }
        if (fromPos <= 0 || occurrence <= 0) {
            return null; // 参数非法 → NULL，不抛错
        }
        Matcher m;
        try {
            m = Pattern.compile(patternStr).matcher(str);
        } catch (PatternSyntaxException e) {
            throw new HiveException("regexp_substr 正则语法非法: " + patternStr, e);
        }
        m.region(Math.min(fromPos - 1, str.length()), str.length());
        int n = 0;
        while (m.find()) {
            if (++n == occurrence) {
                return m.group();
            }
        }
        return null; // 无匹配
    }

    private void checkString(ObjectInspector oi, int idx) throws UDFArgumentException {
        if (oi.getCategory() != ObjectInspector.Category.PRIMITIVE
                || (((PrimitiveObjectInspector) oi).getPrimitiveCategory()
                != PrimitiveObjectInspector.PrimitiveCategory.STRING
                && ((PrimitiveObjectInspector) oi).getPrimitiveCategory()
                != PrimitiveObjectInspector.PrimitiveCategory.VOID)) { // NULL 字面量宽容
            throw new UDFArgumentTypeException(idx, "regexp_substr 参数 " + idx + " 必须为字符串");
        }
    }

    private void checkInt(ObjectInspector oi, int idx) throws UDFArgumentException {
        if (oi.getCategory() != ObjectInspector.Category.PRIMITIVE) {
            throw new UDFArgumentTypeException(idx, "regexp_substr 参数 " + idx + " 必须为整数");
        }
        PrimitiveObjectInspector.PrimitiveCategory cat = ((PrimitiveObjectInspector) oi).getPrimitiveCategory();
        if (cat != PrimitiveObjectInspector.PrimitiveCategory.INT
                && cat != PrimitiveObjectInspector.PrimitiveCategory.LONG
                && cat != PrimitiveObjectInspector.PrimitiveCategory.VOID) { // NULL 字面量宽容
            throw new UDFArgumentTypeException(idx, "regexp_substr 参数 " + idx + " 必须为整数");
        }
    }

    @Override
    public String getDisplayString(String[] children) {
        StringBuilder sb = new StringBuilder("regexp_substr(");
        for (int i = 0; i < children.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(children[i]);
        }
        return sb.append(")").toString();
    }
}
