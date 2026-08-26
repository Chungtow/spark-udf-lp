package com.liangpu.udf;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.liangpu.json.JsonPathException;
import com.liangpu.json.JsonPathSupport;
import com.liangpu.json.JsonSupport;
import com.liangpu.json.JsonSyntaxException;
import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;

import java.util.Iterator;
import java.util.Map;

/**
 * json_strip_nulls(json[, include_arrays][, remove_empty][, path])：
 * 递归移除值为 null 的字段/元素（对齐 MaxCompute JSON_STRIP_NULLS），返回处理后的 JSON 文本。
 *
 * <p>参数规则（仅可依序出现）：</p>
 * <ul>
 *   <li>{@code include_arrays}：是否删除数组内的 null 值，默认 true；对象内 null 字段总是删除</li>
 *   <li>{@code remove_empty}：删除后是否删除空对象/空数组，默认 false</li>
 *   <li>{@code path}：仅处理该 JSONPath 下的 null（必须作为第 4 个参数传入）</li>
 * </ul>
 *
 * <p>边界：JSON null 输入 → 返回 {@code "null"}；移除后无内容 → 返回 {@code "null"}；
 * json / include_arrays / remove_empty 为 SQL NULL → NULL；path 为 NULL 或无效 → 原样返回 json；
 * 非法 JSON 文本 → NULL。</p>
 *
 * <p>调用：</p>
 * <pre>
 * SELECT json_strip_nulls('[1,null,2,null]');                       -- [1,2]
 * SELECT json_strip_nulls('[1,null,2,null]', false);                -- [1,null,2,null]
 * SELECT json_strip_nulls('{"a":{"c":null},"b":1}', true, true);    -- {"b":1}
 * </pre>
 */
public class JsonStripNullsUdf extends GenericUDF {

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length < 1 || args.length > 4) {
            throw new UDFArgumentException("json_strip_nulls 需要 1~4 个参数 "
                    + "(json[, include_arrays][, remove_empty][, path])，实际 " + args.length);
        }
        return PrimitiveObjectInspectorFactory.javaStringObjectInspector;
    }

    @Override
    public Object evaluate(DeferredObject[] args) throws HiveException {
        Object j = args[0].get();
        if (j == null) {
            return null;
        }
        String json = String.valueOf(j);

        // 布尔参数默认值
        boolean includeArrays = true;
        boolean removeEmpty = false;
        String path = null;

        if (args.length >= 2) {
            Object ia = args[1].get();
            if (ia == null) {
                return null; // MC: include_arrays 为 NULL → NULL
            }
            includeArrays = Boolean.parseBoolean(String.valueOf(ia));
        }
        if (args.length >= 3) {
            Object re = args[2].get();
            if (re == null) {
                return null; // MC: remove_empty 为 NULL → NULL
            }
            removeEmpty = Boolean.parseBoolean(String.valueOf(re));
        }
        if (args.length == 4) {
            Object p = args[3].get();
            path = p == null ? null : String.valueOf(p);
        }

        final Object root;
        try {
            root = JsonSupport.parse(json);
        } catch (JsonSyntaxException e) {
            return null; // 非法 JSON（数据问题）宽容处理
        }
        if (root == null) {
            return "null"; // JSON null 字面量输入
        }

        if (path != null) {
            final JsonPathSupport.Resolved resolved;
            try {
                resolved = JsonPathSupport.resolve(root, path);
            } catch (JsonPathException e) {
                return json; // MC: path 无效 → 原样返回
            }
            if (!resolved.exists()) {
                return json;
            }
            // 仅处理该 path 节点内部（strip 原地修改 root 中对应节点），节点本身不删除
            strip(resolved.value(), includeArrays, removeEmpty);
            return JsonSupport.toJsonString(root);
        }

        StripResult result = strip(root, includeArrays, removeEmpty);
        if (result.remove) {
            return "null"; // 根结构被清空且 remove_empty=true → JSON null
        }
        return JsonSupport.toJsonString(result.node);
    }

    private static final class StripResult {
        private final boolean remove;
        private final Object node;

        private StripResult(boolean remove, Object node) {
            this.remove = remove;
            this.node = node;
        }

        static StripResult keep(Object node) {
            return new StripResult(false, node);
        }

        static StripResult remove() {
            return new StripResult(true, null);
        }
    }

    /**
     * 递归剥离 null（原地修改节点对象）。返回 remove=true 表示该节点应被删除
     * （值为 JSON null，或 remove_empty 时清空的空结构）。
     */
    private StripResult strip(Object node, boolean includeArrays, boolean removeEmpty) {
        if (node == null) {
            return StripResult.remove(); // JSON null 值（父级决定删除/保留）
        }
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            Iterator<Map.Entry<String, Object>> it = obj.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Object> entry = it.next();
                if (entry.getValue() == null) {
                    it.remove(); // 对象内 null 字段总是删除
                } else {
                    StripResult r = strip(entry.getValue(), includeArrays, removeEmpty);
                    if (r.remove) {
                        it.remove();
                    } else {
                        entry.setValue(r.node);
                    }
                }
            }
            if (removeEmpty && obj.isEmpty()) {
                return StripResult.remove();
            }
            return StripResult.keep(obj);
        }
        if (node instanceof JSONArray) {
            if (!includeArrays) {
                // include_arrays=false：数组整体跳过（不删 null、不递归、不做空数组删除），
                // 对齐 MC 契约示例：'{"a":{"b":{"c":null}},"d":[null],"e":[],"f":1}', false, true
                // → '{"d":[null],"e":[],"f":1}'（e:[] 与 d 内 null 均保留）
                return StripResult.keep(node);
            }
            JSONArray arr = (JSONArray) node;
            for (int i = arr.size() - 1; i >= 0; i--) {
                Object element = arr.get(i);
                if (element == null) {
                    arr.remove(i); // include_arrays=true：数组内 null 删除
                } else {
                    StripResult r = strip(element, includeArrays, removeEmpty);
                    if (r.remove) {
                        arr.remove(i);
                    } else {
                        arr.set(i, r.node);
                    }
                }
            }
            if (removeEmpty && arr.isEmpty()) {
                return StripResult.remove();
            }
            return StripResult.keep(arr);
        }
        return StripResult.keep(node); // 非 null 标量
    }

    @Override
    public String getDisplayString(String[] children) {
        StringBuilder sb = new StringBuilder("json_strip_nulls(");
        for (int i = 0; i < children.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(children[i]);
        }
        return sb.append(")").toString();
    }
}
