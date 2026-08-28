package com.liangpu.help;

/**
 * lpudf 函数帮助信息描述条目（迭代 3：desc function 支持）。
 *
 * <p>字段与 {@code specs/api-spec.yaml} 为同一事实来源（REQ-HELP-4），改动需同步勾稽。</p>
 */
public class LpudfFunction {

    /** 函数种类，决定注入 builder 使用的 Hive 包装类。 */
    public enum Kind { UDF, UDAF, UDTF }

    /** 注册名（如 json_pretty、udf_prefix）。 */
    public final String name;
    /** 注册库名（default / lpudf）。 */
    public final String database;
    /** 函数类全限定名。 */
    public final String className;
    /** 函数种类。 */
    public final Kind kind;
    /** usage 帮助文本（直接书写函数名，注入路径无占位符替换）。 */
    public final String usage;
    /** arguments 帮助文本（多行用 '\n' 分隔，可为空）。 */
    public final String arguments;

    public LpudfFunction(String name, String database, String className, Kind kind,
                         String usage, String arguments) {
        this.name = name;
        this.database = database;
        this.className = className;
        this.kind = kind;
        this.usage = usage;
        this.arguments = arguments;
    }
}
