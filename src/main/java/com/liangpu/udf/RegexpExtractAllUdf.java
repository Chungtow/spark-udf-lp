package com.liangpu.udf;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.exec.UDFArgumentTypeException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorUtils;
import org.apache.spark.sql.catalyst.expressions.ExpressionDescription;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * regexp_extract_all(str, pattern[, group])：正则全量提取，返回所有匹配子串的 array&lt;string&gt;
 * （对齐 MaxCompute REGEXP_EXTRACT_ALL）。
 *
 * <p>契约：group 为捕获组索引（默认 0 全匹配）；无匹配返回空数组（非 NULL）；
 * group 为负数（写法错误）→ NULL；group 超出实际组数 → NULL（数据宽容）。
 * 返回类型为 ArrayType（SPIKE 实证可在 Spark 3.3.1 注册并输出）。</p>
 *
 * <p>边界：str 或 pattern 为 SQL NULL → NULL；pattern 语法非法为写法错误 → 抛错。</p>
 *
 * <p>调用：</p>
 * <pre>
 * SELECT regexp_extract_all('a1b2c3', '[0-9]');            -- ["1","2","3"]
 * SELECT regexp_extract_all('k1=1&k2=2', 'k(\\d)', 1);     -- ["1","2"]
 * </pre>
 */
@ExpressionDescription(
        usage = "regexp_extract_all(str, pattern[, group]) - 正则全量提取，返回所有匹配子串的 array<string>；group 为捕获组（默认 0 全匹配）；无匹配返回空数组。",
        arguments = "str - 源字符串\npattern - 正则表达式（Java Pattern 语法）\ngroup - 捕获组索引（可选，默认 0，全匹配）")
public class RegexpExtractAllUdf extends GenericUDF {

    private transient PrimitiveObjectInspector groupOI; // 第 3 参（group）OI

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length != 2 && args.length != 3) {
            throw new UDFArgumentException("regexp_extract_all 需要 2 或 3 个参数: regexp_extract_all(str, pattern[, group])，实际 " + args.length);
        }
        checkString(args[0], 0);
        checkString(args[1], 1);
        if (args.length == 3) {
            checkInt(args[2], 2);
            groupOI = (PrimitiveObjectInspector) args[2];
        }
        return ObjectInspectorFactory.getStandardListObjectInspector(
                PrimitiveObjectInspectorFactory.javaStringObjectInspector);
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
        int group = 0;
        if (args.length == 3) {
            if (args[2].get() == null) {
                return null;
            }
            group = PrimitiveObjectInspectorUtils.getInt(args[2].get(), groupOI);
        }
        if (group < 0) {
            return null; // 负数组索引（写法错误）宽容处理
        }
        Matcher m;
        try {
            m = Pattern.compile(patternStr).matcher(str);
        } catch (PatternSyntaxException e) {
            throw new HiveException("regexp_extract_all 正则语法非法: " + patternStr, e);
        }
        if (group > m.groupCount()) {
            return null; // 组索引超出实际组数（数据宽容）
        }
        List<String> list = new ArrayList<String>();
        while (m.find()) {
            list.add(m.group(group));
        }
        return list;
    }

    private void checkString(ObjectInspector oi, int idx) throws UDFArgumentException {
        if (oi.getCategory() != ObjectInspector.Category.PRIMITIVE
                || (((PrimitiveObjectInspector) oi).getPrimitiveCategory()
                != PrimitiveObjectInspector.PrimitiveCategory.STRING
                && ((PrimitiveObjectInspector) oi).getPrimitiveCategory()
                != PrimitiveObjectInspector.PrimitiveCategory.VOID)) { // NULL 字面量宽容
            throw new UDFArgumentTypeException(idx, "regexp_extract_all 参数 " + idx + " 必须为字符串");
        }
    }

    private void checkInt(ObjectInspector oi, int idx) throws UDFArgumentException {
        if (oi.getCategory() != ObjectInspector.Category.PRIMITIVE) {
            throw new UDFArgumentTypeException(idx, "regexp_extract_all 参数 " + idx + " 必须为整数");
        }
        PrimitiveObjectInspector.PrimitiveCategory cat = ((PrimitiveObjectInspector) oi).getPrimitiveCategory();
        if (cat != PrimitiveObjectInspector.PrimitiveCategory.INT
                && cat != PrimitiveObjectInspector.PrimitiveCategory.LONG
                && cat != PrimitiveObjectInspector.PrimitiveCategory.VOID) { // NULL 字面量宽容
            throw new UDFArgumentTypeException(idx, "regexp_extract_all 参数 " + idx + " 必须为整数");
        }
    }

    @Override
    public String getDisplayString(String[] children) {
        StringBuilder sb = new StringBuilder("regexp_extract_all(");
        for (int i = 0; i < children.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(children[i]);
        }
        return sb.append(")").toString();
    }
}
