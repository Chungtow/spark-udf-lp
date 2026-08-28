package com.liangpu.help;

import org.apache.spark.sql.SparkSessionExtensions;
import org.apache.spark.sql.catalyst.expressions.ExpressionDescription;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 迭代 3（desc function 帮助信息）L1 单测：
 * 帮助信息清单完整性、函数类注解存在性、与 api-spec.yaml 契约勾稽（REQ-HELP-4）。
 */
public class LpudfFunctionRegistryTest {

    /** 全部 46 个注册名（迭代 1 的 3 个 + 迭代 2 的 9 个 JSON + 迭代 4 的 10 个字符串 + 迭代 5 的 8 个聚合 + 迭代 6 的 16 个地理）。 */
    private static final Set<String> EXPECTED_NAMES = new HashSet<>(Arrays.asList(
            "udf_prefix", "udaf_string_agg", "udtf_split_rows",
            "json_valid", "json_extract", "json_length", "json_type",
            "json_exists", "json_contains", "json_pretty", "json_strip_nulls",
            "json_explode",
            "keyvalue", "keyvalue_tuple", "url_encode", "url_decode", "mask_hash",
            "regexp_count", "regexp_extract_all", "regexp_substr",
            "regexp_replace_nth", "find_in_set_ex",
            "any_value", "map_agg", "median", "arg_max", "arg_min",
            "histogram", "multimap_agg", "wm_concat",
            "st_geogpoint", "st_geogfromtext", "st_geogfromwkb", "st_astext", "st_asbinary",
            "st_x", "st_y", "st_boundingbox", "st_distance", "st_dwithin",
            "st_contains", "st_covers", "st_intersects", "st_within",
            "st_makeline", "st_makepolygon"));

    @Test
    public void registryCoversAllFunctions() {
        Set<String> actual = LpudfFunctionRegistry.ALL.stream()
                .map(f -> f.name)
                .collect(Collectors.toSet());
        assertEquals("帮助信息清单应覆盖全部 46 个函数（REQ-HELP-5）", EXPECTED_NAMES, actual);
    }

    @Test
    public void iteration6GeoFunctionsMatchApiSpec() throws IOException {
        // 迭代 6 的 16 个地理函数同样须登记于 api-spec.yaml（REQ-HELP-5）
        Set<String> specNames = extractFunctionNamesFromApiSpec();
        Set<String> it6 = new HashSet<>(Arrays.asList(
                "st_geogpoint", "st_geogfromtext", "st_geogfromwkb", "st_astext", "st_asbinary",
                "st_x", "st_y", "st_boundingbox", "st_distance", "st_dwithin",
                "st_contains", "st_covers", "st_intersects", "st_within",
                "st_makeline", "st_makepolygon"));
        for (String name : it6) {
            assertTrue("api-spec 缺少函数 " + name + "（帮助文本事实来源缺失）", specNames.contains(name));
        }
    }

    @Test
    public void everyEntryHasUsableMetadata() throws Exception {
        for (LpudfFunction f : LpudfFunctionRegistry.ALL) {
            assertNotNull("注册名不能为空: " + f, f.name);
            assertNotNull("库名不能为空: " + f.name, f.database);
            assertNotNull("类名不能为空: " + f.name, f.className);
            assertTrue("usage 非空且包含函数名: " + f.name,
                    f.usage != null && !f.usage.trim().isEmpty() && f.usage.contains(f.name));

            // 函数类必须存在，且标注 @ExpressionDescription（ADR-11 文档化约定）
            Class<?> clazz = Class.forName(f.className);
            assertNotNull("函数类应标注 @ExpressionDescription: " + f.className,
                    clazz.getAnnotation(ExpressionDescription.class));
        }
    }

    @Test
    public void jsonFunctionsMatchApiSpec() throws IOException {
        // api-spec.yaml 为帮助文本唯一事实来源（REQ-HELP-5），JSON 函数名须与其 functions 块一致
        Set<String> specNames = extractFunctionNamesFromApiSpec();
        for (LpudfFunction f : LpudfFunctionRegistry.ALL) {
            if (f.name.startsWith("json_")) {
                assertTrue("api-spec 缺少函数 " + f.name + "（帮助文本事实来源缺失）", specNames.contains(f.name));
            }
        }
    }

    @Test
    public void iteration4FunctionsMatchApiSpec() throws IOException {
        // 迭代 4 的 10 个字符串处理函数同样须登记于 api-spec.yaml（REQ-HELP-5）
        Set<String> specNames = extractFunctionNamesFromApiSpec();
        Set<String> it4 = new HashSet<>(Arrays.asList(
                "keyvalue", "keyvalue_tuple", "url_encode", "url_decode", "mask_hash",
                "regexp_count", "regexp_extract_all", "regexp_substr",
                "regexp_replace_nth", "find_in_set_ex"));
        for (String name : it4) {
            assertTrue("api-spec 缺少函数 " + name + "（帮助文本事实来源缺失）", specNames.contains(name));
        }
    }

    @Test
    public void extensionsEntryIsLoadableAndIsFunction1() throws Exception {
        Class<?> clazz = Class.forName("com.liangpu.help.LpudfExtensions");
        assertTrue("LpudfExtensions 应为 scala.Function1[SparkSessionExtensions, Object]",
                scala.Function1.class.isAssignableFrom(clazz));
        Object instance = clazz.getDeclaredConstructor().newInstance();
        assertNotNull(instance);
    }

    // ---- helpers ----

    private static Set<String> extractFunctionNamesFromApiSpec() throws IOException {
        String yaml = new String(Files.readAllBytes(Paths.get("specs", "api-spec.yaml")),
                StandardCharsets.UTF_8);
        Set<String> names = new HashSet<>();
        Matcher m = Pattern.compile("^  ([a-z_][a-z0-9_]*):", Pattern.MULTILINE).matcher(yaml);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }
}
