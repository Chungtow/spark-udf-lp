package com.liangpu.udf.geo;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * st_makepolygon 单元测试：shell/holes 数组构造；自动闭合；≥3 顶点校验；NULL 传播。
 */
public class StMakepolygonUdfTest {

    private StMakepolygonUdf udf;

    @Before
    public void setUp() throws Exception {
        udf = new StMakepolygonUdf();
        udf.initialize(new ObjectInspector[]{
                ObjectInspectorFactory.getStandardListObjectInspector(
                        PrimitiveObjectInspectorFactory.javaStringObjectInspector)
        });
    }

    private GenericUDF.DeferredObject[] args(Object... values) {
        GenericUDF.DeferredObject[] result = new GenericUDF.DeferredObject[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = new GenericUDF.DeferredJavaObject(values[i]);
        }
        return result;
    }

    private static List<String> points(String... wkts) {
        return Arrays.asList(wkts);
    }

    @Test
    public void testShellAutoClosed() throws Exception {
        // 4 顶点首尾不同 → 自动补闭合
        assertEquals("POLYGON ((0 0, 4 0, 4 4, 0 4, 0 0))",
                udf.evaluate(args(points("POINT (0 0)", "POINT (4 0)", "POINT (4 4)", "POINT (0 4)"))));
    }

    @Test
    public void testShellAlreadyClosed() throws Exception {
        assertEquals("POLYGON ((0 0, 4 0, 4 4, 0 4, 0 0))",
                udf.evaluate(args(points("POINT (0 0)", "POINT (4 0)", "POINT (4 4)", "POINT (0 4)", "POINT (0 0)"))));
    }

    @Test
    public void testWithHoles() throws Exception {
        // shell + holes，holes 自动闭合
        List<String> shell = points("POINT (0 0)", "POINT (10 0)", "POINT (10 10)", "POINT (0 10)");
        List<String> holes = points("POINT (2 2)", "POINT (2 4)", "POINT (4 4)", "POINT (4 2)");
        StMakepolygonUdf twoArg = new StMakepolygonUdf();
        twoArg.initialize(new ObjectInspector[]{
                ObjectInspectorFactory.getStandardListObjectInspector(
                        PrimitiveObjectInspectorFactory.javaStringObjectInspector),
                ObjectInspectorFactory.getStandardListObjectInspector(
                        PrimitiveObjectInspectorFactory.javaStringObjectInspector)
        });
        assertEquals("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0), (2 2, 2 4, 4 4, 4 2, 2 2))",
                twoArg.evaluate(args(shell, holes)));
    }

    @Test
    public void testShellNull() throws Exception {
        assertNull(udf.evaluate(args((Object) null)));
    }

    @Test
    public void testShellNullElement() throws Exception {
        assertNull(udf.evaluate(args(points("POINT (0 0)", null, "POINT (4 4)"))));
    }

    @Test
    public void testHolesNullElement() throws Exception {
        List<String> shell = points("POINT (0 0)", "POINT (10 0)", "POINT (10 10)");
        List<String> holes = Arrays.asList("POINT (2 2)", null);
        StMakepolygonUdf twoArg = new StMakepolygonUdf();
        twoArg.initialize(new ObjectInspector[]{
                ObjectInspectorFactory.getStandardListObjectInspector(
                        PrimitiveObjectInspectorFactory.javaStringObjectInspector),
                ObjectInspectorFactory.getStandardListObjectInspector(
                        PrimitiveObjectInspectorFactory.javaStringObjectInspector)
        });
        assertNull(twoArg.evaluate(args(shell, holes)));
    }

    @Test(expected = HiveException.class)
    public void testShellEmpty() throws Exception {
        udf.evaluate(args(points()));
    }

    @Test(expected = HiveException.class)
    public void testShellLessThan3Vertices() throws Exception {
        udf.evaluate(args(points("POINT (0 0)", "POINT (4 0)")));
    }

    @Test(expected = HiveException.class)
    public void testHolesLessThan3Vertices() throws Exception {
        List<String> shell = points("POINT (0 0)", "POINT (10 0)", "POINT (10 10)");
        List<String> holes = points("POINT (2 2)", "POINT (2 4)");
        StMakepolygonUdf twoArg = new StMakepolygonUdf();
        twoArg.initialize(new ObjectInspector[]{
                ObjectInspectorFactory.getStandardListObjectInspector(
                        PrimitiveObjectInspectorFactory.javaStringObjectInspector),
                ObjectInspectorFactory.getStandardListObjectInspector(
                        PrimitiveObjectInspectorFactory.javaStringObjectInspector)
        });
        twoArg.evaluate(args(shell, holes));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidWktElement() throws Exception {
        udf.evaluate(args(points("POINT (0 0)", "NOT A WKT", "POINT (4 4)")));
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        new StMakepolygonUdf().initialize(new ObjectInspector[]{
                ObjectInspectorFactory.getStandardListObjectInspector(
                        PrimitiveObjectInspectorFactory.javaStringObjectInspector),
                ObjectInspectorFactory.getStandardListObjectInspector(
                        PrimitiveObjectInspectorFactory.javaStringObjectInspector),
                ObjectInspectorFactory.getStandardListObjectInspector(
                        PrimitiveObjectInspectorFactory.javaStringObjectInspector)
        });
    }

    @Test(expected = UDFArgumentException.class)
    public void testArgNotGeogOrList() throws Exception {
        // 数值 primitive（非 geog/string、非数组）报错；string（geog 载体）与 array<geog> 均为合法签名
        new StMakepolygonUdf().initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaIntObjectInspector
        });
    }

    // ---------- LINESTRING 签名（阿里云文档）：shell 为 LINESTRING WKT，holes 为 LINESTRING WKT 数组 ----------

    private StMakepolygonUdf stringUdf() throws Exception {
        StMakepolygonUdf u = new StMakepolygonUdf();
        u.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
        return u;
    }

    private StMakepolygonUdf stringPlusListUdf() throws Exception {
        StMakepolygonUdf u = new StMakepolygonUdf();
        u.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                ObjectInspectorFactory.getStandardListObjectInspector(
                        PrimitiveObjectInspectorFactory.javaStringObjectInspector)
        });
        return u;
    }

    @Test
    public void testShellLineStringAutoClosed() throws Exception {
        // ST_MAKEPOLYGON(ST_GEOGFROMTEXT('LINESTRING(...)'))：首尾不同自动补闭合
        assertEquals("POLYGON ((0 0, 4 0, 4 4, 0 4, 0 0))",
                stringUdf().evaluate(args("LINESTRING (0 0, 4 0, 4 4, 0 4)")));
    }

    @Test
    public void testShellLineStringAlreadyClosed() throws Exception {
        assertEquals("POLYGON ((0 0, 4 0, 4 4, 0 4, 0 0))",
                stringUdf().evaluate(args("LINESTRING (0 0, 4 0, 4 4, 0 4, 0 0)")));
    }

    @Test
    public void testShellLineStringNull() throws Exception {
        assertNull(stringUdf().evaluate(args((Object) null)));
    }

    @Test(expected = HiveException.class)
    public void testShellPointWktRejected() throws Exception {
        // shell 必须是 LINESTRING，POINT WKT 报错
        stringUdf().evaluate(args("POINT (1 1)"));
    }

    @Test(expected = HiveException.class)
    public void testShellPolygonWktRejected() throws Exception {
        stringUdf().evaluate(args("POLYGON ((0 0, 4 0, 4 4, 0 4, 0 0))"));
    }

    @Test(expected = HiveException.class)
    public void testShellLineStringLessThan3Vertices() throws Exception {
        stringUdf().evaluate(args("LINESTRING (0 0, 4 0)"));
    }

    @Test
    public void testLineStringWithHoles() throws Exception {
        // shell 为 LINESTRING、holes 为 LINESTRING 数组：文档示例 LINESTRING(2 2, 2 4, 4 4, 4 2) 自动闭合
        List<String> holes = points("LINESTRING (2 2, 2 4, 4 4, 4 2)");
        assertEquals("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0), (2 2, 2 4, 4 4, 4 2, 2 2))",
                stringPlusListUdf().evaluate(args("LINESTRING (0 0, 10 0, 10 10, 0 10, 0 0)", holes)));
    }

    @Test
    public void testLineStringWithMultiHoles() throws Exception {
        // 多内环：每个 LINESTRING 一个内环
        List<String> holes = points(
                "LINESTRING (1 1, 1 2, 2 2, 2 1)",
                "LINESTRING (8 8, 8 9, 9 9, 9 8)");
        assertEquals("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0), (1 1, 1 2, 2 2, 2 1, 1 1), (8 8, 8 9, 9 9, 9 8, 8 8))",
                stringPlusListUdf().evaluate(args("LINESTRING (0 0, 10 0, 10 10, 0 10, 0 0)", holes)));
    }

    @Test
    public void testHolesLineStringNullElement() throws Exception {
        List<String> holes = points("LINESTRING (2 2, 2 4, 4 4, 4 2)", null);
        assertNull(stringPlusListUdf().evaluate(args("LINESTRING (0 0, 10 0, 10 10, 0 10, 0 0)", holes)));
    }

    @Test(expected = HiveException.class)
    public void testHolesLineStringLessThan3Vertices() throws Exception {
        List<String> holes = points("LINESTRING (2 2, 2 4)");
        stringPlusListUdf().evaluate(args("LINESTRING (0 0, 10 0, 10 10, 0 10, 0 0)", holes));
    }

    @Test(expected = HiveException.class)
    public void testHolesMixedPointAndLine() throws Exception {
        // holes 元素须统一为 POINT 或 LINESTRING
        List<String> holes = points("POINT (2 2)", "LINESTRING (1 1, 1 2, 2 2, 2 1)");
        stringPlusListUdf().evaluate(args("LINESTRING (0 0, 10 0, 10 10, 0 10, 0 0)", holes));
    }
}
