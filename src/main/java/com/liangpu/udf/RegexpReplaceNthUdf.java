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
 * regexp_replace_nth(str, pattern, repl[, occurrence])：只替换第 nth 次正则匹配
 * （MC regexp_replace 的 occurrence 增强，换名避免与内置 regexp_replace 冲突，ADR-12）。
 *
 * <p>契约：occurrence 默认 1；repl 支持 \\1 后向引用（等价 appendReplacement 的组引用语义）；
 * 非命中段一律追加原文本（防 $ 误解析，POC 实证）；匹配次数不足 occurrence → 原样返回；
 * occurrence &lt;= 0 → 原样返回。</p>
 *
 * <p>边界：str / pattern / repl 任一为 SQL NULL → NULL；pattern 语法非法为写法错误 → 抛错。</p>
 *
 * <p>调用：</p>
 * <pre>
 * SELECT regexp_replace_nth('abc123def456', '[0-9]+', 'X');      -- abcXdef456
 * SELECT regexp_replace_nth('abc123def456', '[0-9]+', 'X', 2);   -- abc123defX
 * SELECT regexp_replace_nth('a12b34', '(\\d+)', '[\\1]');        -- a[12]b34
 * </pre>
 */
@ExpressionDescription(
        usage = "regexp_replace_nth(str, pattern, repl[, occurrence]) - 只替换第 nth 次正则匹配（occurrence 默认 1）；repl 支持 \\1 后向引用；匹配次数不足原样返回。",
        arguments = "str - 源字符串\npattern - 正则表达式（Java Pattern 语法）\nrepl - 替换串，支持 \\1 后向引用\noccurrence - 只替换第几次匹配（可选，默认 1）")
public class RegexpReplaceNthUdf extends GenericUDF {

    private transient PrimitiveObjectInspector occurrenceOI; // 第 4 参（occurrence）OI

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length != 3 && args.length != 4) {
            throw new UDFArgumentException("regexp_replace_nth 需要 3 或 4 个参数: regexp_replace_nth(str, pattern, repl[, occurrence])，实际 " + args.length);
        }
        checkString(args[0], 0);
        checkString(args[1], 1);
        checkString(args[2], 2);
        if (args.length == 4) {
            checkInt(args[3], 3);
            occurrenceOI = (PrimitiveObjectInspector) args[3];
        }
        return PrimitiveObjectInspectorFactory.javaStringObjectInspector;
    }

    @Override
    public Object evaluate(DeferredObject[] args) throws HiveException {
        Object s = args[0].get();
        Object p = args[1].get();
        Object r = args[2].get();
        if (s == null || p == null || r == null) {
            return null;
        }
        String str = String.valueOf(s);
        String patternStr = String.valueOf(p);
        String repl = String.valueOf(r);
        int occurrence = 1;
        if (args.length == 4) {
            if (args[3].get() == null) {
                return null;
            }
            occurrence = PrimitiveObjectInspectorUtils.getInt(args[3].get(), occurrenceOI);
        }
        if (occurrence <= 0) {
            return str; // occurrence 非法 → 原样返回
        }
        Matcher m;
        try {
            m = Pattern.compile(patternStr).matcher(str);
        } catch (PatternSyntaxException e) {
            throw new HiveException("regexp_replace_nth 正则语法非法: " + patternStr, e);
        }
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        int n = 0;
        while (m.find()) {
            n++;
            if (n == occurrence) {
                // 目标匹配：前段原文本 + 展开替换串（支持 \1 / $1 组引用）
                sb.append(str, pos, m.start()).append(expandReplacement(repl, m));
                pos = m.end();
                break;
            }
        }
        sb.append(str, pos, str.length());
        return sb.toString();
    }

    /**
     * 展开替换串中的组引用（\1..\9 与 $1..$9），未捕获组替换为空串（与 appendReplacement 语义一致）。
     */
    private String expandReplacement(String repl, Matcher m) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < repl.length(); i++) {
            char c = repl.charAt(i);
            int group = -1;
            if (c == '\\' && i + 1 < repl.length() && isDigit(repl.charAt(i + 1))) {
                group = repl.charAt(++i) - '0';
            } else if (c == '$' && i + 1 < repl.length() && isDigit(repl.charAt(i + 1))) {
                group = repl.charAt(++i) - '0';
            }
            if (group >= 0 && group <= m.groupCount()) {
                String g = m.group(group);
                out.append(g == null ? "" : g);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private void checkString(ObjectInspector oi, int idx) throws UDFArgumentException {
        if (oi.getCategory() != ObjectInspector.Category.PRIMITIVE
                || (((PrimitiveObjectInspector) oi).getPrimitiveCategory()
                != PrimitiveObjectInspector.PrimitiveCategory.STRING
                && ((PrimitiveObjectInspector) oi).getPrimitiveCategory()
                != PrimitiveObjectInspector.PrimitiveCategory.VOID)) { // NULL 字面量宽容
            throw new UDFArgumentTypeException(idx, "regexp_replace_nth 参数 " + idx + " 必须为字符串");
        }
    }

    private void checkInt(ObjectInspector oi, int idx) throws UDFArgumentException {
        if (oi.getCategory() != ObjectInspector.Category.PRIMITIVE) {
            throw new UDFArgumentTypeException(idx, "regexp_replace_nth 参数 " + idx + " 必须为整数");
        }
        PrimitiveObjectInspector.PrimitiveCategory cat = ((PrimitiveObjectInspector) oi).getPrimitiveCategory();
        if (cat != PrimitiveObjectInspector.PrimitiveCategory.INT
                && cat != PrimitiveObjectInspector.PrimitiveCategory.LONG
                && cat != PrimitiveObjectInspector.PrimitiveCategory.VOID) { // NULL 字面量宽容
            throw new UDFArgumentTypeException(idx, "regexp_replace_nth 参数 " + idx + " 必须为整数");
        }
    }

    @Override
    public String getDisplayString(String[] children) {
        StringBuilder sb = new StringBuilder("regexp_replace_nth(");
        for (int i = 0; i < children.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(children[i]);
        }
        return sb.append(")").toString();
    }
}
