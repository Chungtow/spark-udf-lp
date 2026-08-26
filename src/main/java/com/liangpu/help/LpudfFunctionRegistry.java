package com.liangpu.help;

import com.liangpu.help.LpudfFunction.Kind;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * lpudf 全部 12 个函数的帮助信息清单（迭代 3：desc function 支持）。
 *
 * <p>帮助文本与 {@code specs/api-spec.yaml} 各函数条目 description 为同一事实来源（REQ-HELP-4），
 * 由 {@code LpudfFunctionRegistryTest} 勾稽校验。</p>
 *
 * <p>覆盖范围：迭代 1 示例 3 个（udf_prefix / udaf_string_agg / udtf_split_rows）+ 迭代 2 JSON 9 个。</p>
 */
public final class LpudfFunctionRegistry {

    private LpudfFunctionRegistry() {
    }

    /** 全部 12 个函数描述条目。 */
    public static final List<LpudfFunction> ALL = Collections.unmodifiableList(Arrays.asList(
            // ---- 迭代 1 示例（3 个）----
            // 注: udf_prefix 迭代 1 曾注册于 default 库（无库名 CREATE FUNCTION），
            //     现统一注入 lpudf 库，保证 DESC FUNCTION lpudf.<fn> 12/12 显示帮助（REQ-HELP-3）；
            //     metastore 中 default.udf_prefix 旧记录冗余无害（registry 注入条目优先）。
            new LpudfFunction("udf_prefix", "lpudf",
                    "com.liangpu.udf.PrefixUdf", Kind.UDF,
                    "udf_prefix(str) - 返回字符串前 4 个字符（示例 UDF）；str 为 NULL 时返回 NULL。",
                    "str - 字符串表达式"),
            new LpudfFunction("udaf_string_agg", "lpudf",
                    "com.liangpu.udaf.StringAggUDAF", Kind.UDAF,
                    "udaf_string_agg(col) - 字符串列聚合去重拼接：按字典序排序、逗号分隔输出，NULL 行忽略。",
                    "col - 待聚合的字符串列"),
            new LpudfFunction("udtf_split_rows", "lpudf",
                    "com.liangpu.udtf.SplitRowsUDTF", Kind.UDTF,
                    "udtf_split_rows(str, delim) - 字符串按分隔符拆分为多行（单列输出）；NULL/空串无输出，分隔符 NULL/空时默认逗号。",
                    "str - 待拆分的字符串\ndelim - 分隔符（字面量匹配）"),

            // ---- 迭代 2 JSON 函数（9 个）----
            new LpudfFunction("json_valid", "lpudf",
                    "com.liangpu.udf.JsonValidUdf", Kind.UDF,
                    "json_valid(json) - 校验输入字符串是否为合法 JSON（RFC 8259），合法返回 true，非法返回 false；SQL NULL 入参返回 NULL。",
                    "json - 待校验的 JSON 文本"),
            new LpudfFunction("json_extract", "lpudf",
                    "com.liangpu.udf.JsonExtractUdf", Kind.UDF,
                    "json_extract(json, path) - 按 JSONPath 提取值并返回 JSON 文本；path 支持 $.key / $[n] / $['key'] 子集，目标不存在返回 NULL，path 语法非法抛错，非法 JSON 返回 NULL。",
                    "json - JSON 文本\npath - JSONPath 表达式，如 '$.a'、'$.a[0]'"),
            new LpudfFunction("json_length", "lpudf",
                    "com.liangpu.udf.JsonLengthUdf", Kind.UDF,
                    "json_length(json[, path]) - 返回 JSON 数据长度：数组为元素数、对象为成员数、其它类型为 1，不递归计算；未指定 path 时作用于整个 JSON。",
                    "json - JSON 文本\npath - JSONPath 表达式（可选）"),
            new LpudfFunction("json_type", "lpudf",
                    "com.liangpu.udf.JsonTypeUdf", Kind.UDF,
                    "json_type(json) - 返回 JSON 数据类型名称（小写）：string / number / boolean / null / object / array。",
                    "json - JSON 文本"),
            new LpudfFunction("json_exists", "lpudf",
                    "com.liangpu.udf.JsonExistsUdf", Kind.UDF,
                    "json_exists(json, path) - 判断 JSONPath 对应值是否存在，存在返回 true（含值为 null 的键），不存在或数组越界返回 false。",
                    "json - JSON 文本\npath - JSONPath 表达式"),
            new LpudfFunction("json_contains", "lpudf",
                    "com.liangpu.udf.JsonContainsUdf", Kind.UDF,
                    "json_contains(json, candidate[, path]) - 判断 JSON 数据是否包含与 candidate 深度相等的节点（覆盖数组元素/对象值匹配），包含返回 true；path 不存在或语法非法返回 false。",
                    "json - 待检查的 JSON 文本\ncandidate - 要匹配的 JSON 元素文本，如 '4'、'\"abc\"'、'{\"a\":1}'\npath - JSONPath 表达式（可选）"),
            new LpudfFunction("json_pretty", "lpudf",
                    "com.liangpu.udf.JsonPrettyUdf", Kind.UDF,
                    "json_pretty(json) - 美化 JSON：增加换行与缩进（每层 4 空格缩进、键值冒号后无空格）。",
                    "json - JSON 文本"),
            new LpudfFunction("json_strip_nulls", "lpudf",
                    "com.liangpu.udf.JsonStripNullsUdf", Kind.UDF,
                    "json_strip_nulls(json[, include_arrays[, remove_empty[, path]]]) - 递归移除值为 null 的字段/元素；include_arrays 默认 true，remove_empty 默认 false，path 仅限第 4 参。",
                    "json - JSON 文本（对象或数组）\ninclude_arrays - 是否删除数组内 null（可选，默认 true）\nremove_empty - 删除后是否移除空对象/空数组（可选，默认 false）\npath - JSONPath，仅处理该路径下的 null（必须作为第 4 参）"),
            new LpudfFunction("json_explode", "lpudf",
                    "com.liangpu.udtf.JsonExplodeUDTF", Kind.UDTF,
                    "json_explode(json) - 将 JSON 数组/对象展开为多行（固定输出两列 key, value）：数组每元素一行 key 为 NULL，对象每键一行；NULL/非法 JSON/非数组对象输出 0 行。",
                    "json - JSON 数组或对象文本")
    ));

    /** 按注册名查找条目（不含库名，仅匹配 name），未找到返回 null。 */
    public static LpudfFunction byName(String name) {
        for (LpudfFunction f : ALL) {
            if (f.name.equals(name)) {
                return f;
            }
        }
        return null;
    }
}
