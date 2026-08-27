package com.liangpu.help;

import com.liangpu.help.LpudfFunction.Kind;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * lpudf 全部 30 个函数的帮助信息清单（迭代 5：聚合函数族）。
 *
 * <p>帮助文本与 {@code specs/api-spec.yaml} 各函数条目 description 为同一事实来源（REQ-HELP-5），
 * 由 {@code LpudfFunctionRegistryTest} 勾稽校验。</p>
 *
 * <p>覆盖范围：迭代 1 示例 3 个（udf_prefix / udaf_string_agg / udtf_split_rows）+ 迭代 2 JSON 9 个
 * + 迭代 4 字符串处理 10 个 + 迭代 5 聚合函数 8 个。</p>
 */
public final class LpudfFunctionRegistry {

    private LpudfFunctionRegistry() {
    }

    /** 全部 30 个函数描述条目。 */
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
                    "json - JSON 数组或对象文本"),

            // ---- 迭代 4 字符串处理函数（10 个，ADR-8 无前缀注册名）----
            new LpudfFunction("keyvalue", "lpudf",
                    "com.liangpu.udf.KeyvalueUdf", Kind.UDF,
                    "keyvalue(str, key[, split1, split2]) - 从半结构化 kv 串中提取指定 key 的值，2 参用默认分隔符（& 与 =），4 参自定义 split1/split2；key 不存在返回 NULL。",
                    "str - 半结构化字符串，如 'k1=v1&k2=v2'\nkey - 要提取的 key 名（2 参形式第 2 参）\nsplit1 - 键值对分隔符（4 参形式，默认 &）\nsplit2 - key/value 分隔符（4 参形式，默认 =）"),
            new LpudfFunction("keyvalue_tuple", "lpudf",
                    "com.liangpu.udtf.KeyvalueTupleUDTF", Kind.UDTF,
                    "keyvalue_tuple(str, split1, split2, key1, key2, ...) - 半结构化 kv 串多键一次提取（UDTF）：每 key 一列，列序与参数一致，找不到的 key 为 NULL；str 为 NULL 或非 kv 结构输出 0 行。",
                    "str - 半结构化字符串，如 'k1=v1&k2=v2'\nsplit1 - 键值对分隔符，如 '&'\nsplit2 - key/value 分隔符，如 '='\nkey1..keyN - 至少 1 个 key（第 4 参起），输出一列对应一个 key"),
            new LpudfFunction("url_encode", "lpudf",
                    "com.liangpu.udf.UrlEncodeUdf", Kind.UDF,
                    "url_encode(str) - URL 百分号编码（x-www-form-urlencoded）：空格→+、-_.* 保留、~→%7E、非 ASCII 按 UTF-8 字节编码。",
                    "str - 待编码字符串"),
            new LpudfFunction("url_decode", "lpudf",
                    "com.liangpu.udf.UrlDecodeUdf", Kind.UDF,
                    "url_decode(str) - URL 百分号解码（url_encode 逆操作）：+→空格、%XX 按 UTF-8 还原；非法百分号序列返回 NULL。",
                    "str - 待解码字符串"),
            new LpudfFunction("mask_hash", "lpudf",
                    "com.liangpu.udf.MaskHashUdf", Kind.UDF,
                    "mask_hash(str) - 脱敏哈希：SHA-256 十六进制（固定 64 字符小写），不可逆；NULL 或非字符串类型入参返回 NULL。",
                    "str - 待脱敏字符串"),
            new LpudfFunction("regexp_count", "lpudf",
                    "com.liangpu.udf.RegexpCountUdf", Kind.UDF,
                    "regexp_count(str, pattern[, fromPos]) - 统计正则匹配次数；fromPos 为起始位置（1-based，默认 1）；无匹配返回 0，fromPos 越界返回 0。",
                    "str - 源字符串\npattern - 正则表达式（Java Pattern 语法）\nfromPos - 起始匹配位置（1-based，可选，默认 1）"),
            new LpudfFunction("regexp_extract_all", "lpudf",
                    "com.liangpu.udf.RegexpExtractAllUdf", Kind.UDF,
                    "regexp_extract_all(str, pattern[, group]) - 正则全量提取，返回所有匹配子串的 array<string>；group 为捕获组（默认 0 全匹配）；无匹配返回空数组。",
                    "str - 源字符串\npattern - 正则表达式（Java Pattern 语法）\ngroup - 捕获组索引（可选，默认 0，全匹配）"),
            new LpudfFunction("regexp_substr", "lpudf",
                    "com.liangpu.udf.RegexpSubstrUdf", Kind.UDF,
                    "regexp_substr(str, pattern[, fromPos[, occurrence]]) - 返回正则匹配的子串；fromPos 起始位置（1-based），occurrence 第几次出现；无匹配返回 NULL。",
                    "str - 源字符串\npattern - 正则表达式（Java Pattern 语法）\nfromPos - 起始匹配位置（可选，默认 1）\noccurrence - 第几次出现（可选，默认 1）"),
            new LpudfFunction("regexp_replace_nth", "lpudf",
                    "com.liangpu.udf.RegexpReplaceNthUdf", Kind.UDF,
                    "regexp_replace_nth(str, pattern, repl[, occurrence]) - 只替换第 nth 次正则匹配（occurrence 默认 1）；repl 支持 \\1 后向引用；匹配次数不足原样返回。",
                    "str - 源字符串\npattern - 正则表达式（Java Pattern 语法）\nrepl - 替换串，支持 \\1 后向引用\noccurrence - 只替换第几次匹配（可选，默认 1）"),
            new LpudfFunction("find_in_set_ex", "lpudf",
                    "com.liangpu.udf.FindInSetExUdf", Kind.UDF,
                    "find_in_set_ex(str, str_list[, delimiter]) - 返回 str 在 str_list 中的位置（1-based）；找不到返回 0；支持第 3 参自定义分隔符（默认逗号）；str 或 str_list 为 NULL 返回 0。",
                    "str - 要查找的字符串\nstr_list - 由分隔符连接的元素列表，如 'a,b,c'\ndelimiter - 自定义分隔符（可选，默认逗号）"),

            // ---- 迭代 5 聚合函数（8 个，ADR-8 无前缀注册名）----
            new LpudfFunction("any_value", "lpudf",
                    "com.liangpu.udaf.AnyValueUDAF", Kind.UDAF,
                    "any_value(col) - 任选组内一个非 NULL 值返回；全 NULL 或空组返回 NULL。",
                    "col - 基础类型的输入列，NULL 值被忽略"),
            new LpudfFunction("map_agg", "lpudf",
                    "com.liangpu.udaf.MapAggUDAF", Kind.UDAF,
                    "map_agg(key, value) - 将两列聚合为 Map：key 为第一个参数，value 为第二个参数；重复 key 后者覆盖，NULL key 忽略，NULL value 保留。",
                    "key - 作为 Map key 的列（基础类型），NULL 所在行被忽略\nvalue - 作为 Map value 的列（基础类型），NULL 保留"),
            new LpudfFunction("median", "lpudf",
                    "com.liangpu.udaf.MedianUDAF", Kind.UDAF,
                    "median(col) - 返回数值列的中位数（精确）：排序后取中间值，偶数个取中间两值均值，NULL 忽略，全 NULL 或空组返回 NULL。",
                    "col - 数值列（BIGINT/DOUBLE），NULL 被忽略"),
            new LpudfFunction("arg_max", "lpudf",
                    "com.liangpu.udaf.ArgMaxUDAF", Kind.UDAF,
                    "arg_max(v_max, v_ret) - 返回 v_max 取到最大值时对应的 v_ret；参数序为比较列在前、返回列在后，NULL 忽略，并列结果非确定，全 NULL 返回 NULL。",
                    "v_max - 用于比较取最大值的列（基础类型），NULL 所在行被忽略\nv_ret - v_max 最大时返回的关联列值（基础类型）"),
            new LpudfFunction("arg_min", "lpudf",
                    "com.liangpu.udaf.ArgMinUDAF", Kind.UDAF,
                    "arg_min(v_min, v_ret) - 返回 v_min 取到最小值时对应的 v_ret；参数序为比较列在前、返回列在后，NULL 忽略，并列结果非确定，全 NULL 返回 NULL。",
                    "v_min - 用于比较取最小值的列（基础类型），NULL 所在行被忽略\nv_ret - v_min 最小时返回的关联列值（基础类型）"),
            new LpudfFunction("histogram", "lpudf",
                    "com.liangpu.udaf.HistogramUDAF", Kind.UDAF,
                    "histogram(col) - 统计列值频次，返回 Map（key 为输入值，value 为出现次数 bigint）；NULL 不计入，空组返回空 Map。",
                    "col - 基础类型的输入列，NULL 不计入"),
            new LpudfFunction("multimap_agg", "lpudf",
                    "com.liangpu.udaf.MultimapAggUDAF", Kind.UDAF,
                    "multimap_agg(key, value) - 将两列聚合为 Multimap（Map<k, Array<v>>）：key 为第一个参数，value 为第二个参数；NULL key 忽略，NULL value 保留进数组。",
                    "key - 作为 Map key 的列（基础类型），NULL 所在行被忽略\nvalue - 作为数组元素的列（基础类型），NULL 保留进数组"),
            new LpudfFunction("wm_concat", "lpudf",
                    "com.liangpu.udaf.WmConcatUDAF", Kind.UDAF,
                    "wm_concat(sep, col) - 按分隔符 sep 连接组内字符串（不去重、不排序），NULL 忽略，全 NULL 或空组返回 NULL；sep 建议为常量。",
                    "sep - 连接分隔符（建议常量）\ncol - 待连接的字符串列（基础类型），NULL 被忽略")
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
