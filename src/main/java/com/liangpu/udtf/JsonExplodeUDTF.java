package com.liangpu.udtf;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.liangpu.json.JsonSupport;
import com.liangpu.json.JsonSyntaxException;
import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDTF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.StructField;
import org.apache.hadoop.hive.serde2.objectinspector.StructObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.spark.sql.catalyst.expressions.ExpressionDescription;

/**
 * json_explode(json)：将 JSON 数组或对象展开为多行（对齐 MaxCompute JSON_EXPLODE）。
 *
 * <p>固定输出两列 {@code (key STRING, value STRING)}：</p>
 * <ul>
 *   <li>JSON 数组 → 每元素一行，key 为 NULL，value 为元素 JSON 文本</li>
 *   <li>JSON 对象 → 每键一行，key 为键名，value 为对应值 JSON 文本</li>
 * </ul>
 *
 * <p>按最外层拆解，不递归嵌套；单条输入内部顺序保持。</p>
 *
 * <p>边界（数据宽容，ADR-3）：SQL NULL / 非法 JSON 文本 / 合法但非数组或对象 → 0 行无输出
 * （与 MC 报错行为有偏差，见 design ADR-3）。</p>
 *
 * <p>调用（Spark 3.3 需 LATERAL VIEW）：</p>
 * <pre>
 * SELECT t.key, t.value
 * FROM (SELECT 1) x
 * LATERAL VIEW json_explode('{"a":123,"b":"hello"}') t AS key, value;
 * -- 2 行: (a,123) / (b,hello)
 * </pre>
 */
@ExpressionDescription(
        usage = "json_explode(json) - 将 JSON 数组/对象展开为多行（固定输出两列 key, value）：数组每元素一行 key 为 NULL，对象每键一行；NULL/非法 JSON/非数组对象输出 0 行。",
        arguments = "json - JSON 数组或对象文本")
public class JsonExplodeUDTF extends GenericUDTF {

    private transient PrimitiveObjectInspector inputOI;
    private transient List<String> fieldNames;
    private transient List<ObjectInspector> fieldOIs;

    @Override
    public StructObjectInspector initialize(StructObjectInspector argOIs) throws UDFArgumentException {
        List<? extends StructField> fields = argOIs.getAllStructFieldRefs();
        if (fields.size() != 1) {
            throw new UDFArgumentException("json_explode 需要 1 个参数 (json)");
        }
        inputOI = (PrimitiveObjectInspector) fields.get(0).getFieldObjectInspector();

        fieldNames = new ArrayList<String>();
        fieldOIs = new ArrayList<ObjectInspector>();
        fieldNames.add("key");
        fieldOIs.add(PrimitiveObjectInspectorFactory.javaStringObjectInspector);
        fieldNames.add("value");
        fieldOIs.add(PrimitiveObjectInspectorFactory.javaStringObjectInspector);
        return ObjectInspectorFactory.getStandardStructObjectInspector(fieldNames, fieldOIs);
    }

    @Override
    public void process(Object[] record) throws HiveException {
        if (record == null || record.length < 1 || record[0] == null) {
            return; // NULL 输入无输出
        }
        String json = String.valueOf(inputOI.getPrimitiveJavaObject(record[0]));

        final Object parsed;
        try {
            parsed = JsonSupport.parse(json);
        } catch (JsonSyntaxException e) {
            return; // 非法 JSON（数据问题）宽容处理：0 行
        }

        if (parsed instanceof JSONArray) {
            JSONArray arr = (JSONArray) parsed;
            for (int i = 0; i < arr.size(); i++) {
                forward(new Object[]{null, JsonSupport.toJsonString(arr.get(i))});
            }
        } else if (parsed instanceof JSONObject) {
            for (Map.Entry<String, Object> entry : ((JSONObject) parsed).entrySet()) {
                forward(new Object[]{entry.getKey(), JsonSupport.toJsonString(entry.getValue())});
            }
        }
        // 合法但非数组/对象（标量、JSON null）→ 无输出（数据宽容，与 MC 报错有偏差）
    }

    @Override
    public void close() throws HiveException {
        // 无资源需释放
    }
}
