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

import java.math.BigDecimal;
import java.util.Map;

/**
 * json_contains(json, candidate[, path])：判断 JSON 数据是否包含指定元素
 * （对齐 MaxCompute JSON_CONTAINS）。
 *
 * <p>语义：</p>
 * <ul>
 *   <li>未指定 path：递归查找 json 中是否存在与 candidate 深度相等的节点
 *       （覆盖数组元素匹配、对象值匹配、嵌套结构）</li>
 *   <li>指定 path：取 path 节点值，与 candidate 深度相等比较</li>
 *   <li>candidate 为 JSON 文本（数字/布尔/字符串须带引号/对象/数组均可）</li>
 * </ul>
 *
 * <p>边界：path 不存在或语法非法 → false（MC 本函数文档特例，不抛错）；
 * json 或 candidate 为 SQL NULL → NULL；json/candidate 为非法 JSON 文本 → NULL。
 * 数字比较按数值相等（如 4 与 4.0 视为相等）。</p>
 *
 * <p>调用：</p>
 * <pre>
 * SELECT json_contains('[1,2,3,4,5,6,7,8]', '4');                     -- true
 * SELECT json_contains('{"a":1,"b":2,"c":{"d":4}}', '1', '$.a');      -- true
 * </pre>
 */
public class JsonContainsUdf extends GenericUDF {

    @Override
    public ObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException {
        if (args.length < 2 || args.length > 3) {
            throw new UDFArgumentException("json_contains 需要 2~3 个参数 (json, candidate[, path])，实际 " + args.length);
        }
        return PrimitiveObjectInspectorFactory.javaBooleanObjectInspector;
    }

    @Override
    public Object evaluate(DeferredObject[] args) throws HiveException {
        Object j = args[0].get();
        Object c = args[1].get();
        if (j == null || c == null) {
            return null;
        }
        final Object root;
        final Object candidate;
        try {
            root = JsonSupport.parse(String.valueOf(j));
            candidate = JsonSupport.parse(String.valueOf(c));
        } catch (JsonSyntaxException e) {
            return null; // 非法 JSON（数据问题）宽容处理
        }

        if (args.length == 3) {
            Object p = args[2].get();
            if (p == null) {
                return false; // path 为 NULL → false
            }
            final JsonPathSupport.Resolved resolved;
            try {
                resolved = JsonPathSupport.resolve(root, String.valueOf(p));
            } catch (JsonPathException e) {
                return false; // MC JSON_CONTAINS 特例：path 非法 → false（不抛错）
            }
            if (!resolved.exists()) {
                return false;
            }
            return depthEquals(resolved.value(), candidate);
        }
        return containsDeep(root, candidate);
    }

    /**
     * 深度相等比较：对象/数组逐成员递归；Number 按数值相等；其余走 equals。
     */
    private boolean depthEquals(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a instanceof JSONObject && b instanceof JSONObject) {
            JSONObject oa = (JSONObject) a;
            JSONObject ob = (JSONObject) b;
            if (oa.size() != ob.size()) {
                return false;
            }
            for (Map.Entry<String, Object> entry : oa.entrySet()) {
                if (!ob.containsKey(entry.getKey())) {
                    return false;
                }
                if (!depthEquals(entry.getValue(), ob.get(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof JSONArray && b instanceof JSONArray) {
            JSONArray aa = (JSONArray) a;
            JSONArray ab = (JSONArray) b;
            if (aa.size() != ab.size()) {
                return false;
            }
            for (int i = 0; i < aa.size(); i++) {
                if (!depthEquals(aa.get(i), ab.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof Number && b instanceof Number) {
            return new BigDecimal(String.valueOf(a)).compareTo(new BigDecimal(String.valueOf(b))) == 0;
        }
        return a.equals(b);
    }

    /**
     * 递归搜索：节点本身或其嵌套子节点中是否存在与 candidate 深度相等的节点。
     */
    private boolean containsDeep(Object node, Object candidate) {
        if (depthEquals(node, candidate)) {
            return true;
        }
        if (node instanceof JSONObject) {
            for (Object value : ((JSONObject) node).values()) {
                if (containsDeep(value, candidate)) {
                    return true;
                }
            }
        } else if (node instanceof JSONArray) {
            for (Object element : (JSONArray) node) {
                if (containsDeep(element, candidate)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String getDisplayString(String[] children) {
        StringBuilder sb = new StringBuilder("json_contains(");
        for (int i = 0; i < children.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(children[i]);
        }
        return sb.append(")").toString();
    }
}
