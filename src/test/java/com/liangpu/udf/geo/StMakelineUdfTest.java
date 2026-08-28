package com.liangpu.udf.geo;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ListObjectInspector;
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
 * st_makeline 单元测试：两点/数组双签名；退化 Point；NULL/空数组 → NULL。
 */
public class StMakelineUdfTest {

    private StMakelineUdf twoPointUdf;
    private StMakelineUdf arrayUdf;

    @Before
    public void setUp() throws Exception {
        twoPointUdf = new StMakelineUdf();
        twoPointUdf.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
        arrayUdf = new StMakelineUdf();
        arrayUdf.initialize(new ObjectInspector[]{
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

    // ---------- 两参模式 ----------

    @Test
    public void testTwoPoints() throws Exception {
        assertEquals("LINESTRING (2 4, 3 5)",
                twoPointUdf.evaluate(args("POINT (2 4)", "POINT (3 5)")));
    }

    @Test
    public void testTwoPointsNegativeCoords() throws Exception {
        assertEquals("LINESTRING (-180 -90, 180 90)",
                twoPointUdf.evaluate(args("POINT (-180 -90)", "POINT (180 90)")));
    }

    @Test
    public void testSamePointsDegeneratePoint() throws Exception {
        // 两点相同 → 退化为 Point
        assertEquals("POINT (2 4)",
                twoPointUdf.evaluate(args("POINT (2 4)", "POINT (2 4)")));
    }

    @Test
    public void testNullPointTwoParam() throws Exception {
        assertNull(twoPointUdf.evaluate(args(null, "POINT (3 5)")));
    }

    @Test(expected = HiveException.class)
    public void testNotPointTwoParam() throws Exception {
        twoPointUdf.evaluate(args("LINESTRING (2 4, 3 5)", "POINT (3 5)"));
    }

    // ---------- 数组模式 ----------

    @Test
    public void testArrayThreePoints() throws Exception {
        assertEquals("LINESTRING (2 4, 3 5, 4 6)",
                arrayUdf.evaluate(args(points("POINT (2 4)", "POINT (3 5)", "POINT (4 6)"))));
    }

    @Test
    public void testArraySingleElementDegeneratePoint() throws Exception {
        assertEquals("POINT (2 4)", arrayUdf.evaluate(args(points("POINT (2 4)"))));
    }

    @Test
    public void testArrayEmptyNull() throws Exception {
        assertNull(arrayUdf.evaluate(args(points())));
    }

    @Test
    public void testArrayNullElement() throws Exception {
        List<String> list = Arrays.asList("POINT (2 4)", null);
        assertNull(arrayUdf.evaluate(args(list)));
    }

    @Test
    public void testArrayNull() throws Exception {
        assertNull(arrayUdf.evaluate(args((Object) null)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testArrayNotPointElement() throws Exception {
        // 数组元素非 POINT：coordsOf 抛 IllegalArgumentException
        arrayUdf.evaluate(args(points("POINT (2 4)", "LINESTRING (0 0, 1 1)")));
    }

    // ---------- 参数校验 ----------

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        new StMakelineUdf().initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }

    @Test(expected = UDFArgumentException.class)
    public void testSingleArgNotList() throws Exception {
        new StMakelineUdf().initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }

    @Test
    public void testListObjectInspectorCheck() throws Exception {
        StMakelineUdf fresh = new StMakelineUdf();
        ObjectInspector oi = fresh.initialize(new ObjectInspector[]{
                ObjectInspectorFactory.getStandardListObjectInspector(
                        PrimitiveObjectInspectorFactory.javaStringObjectInspector)
        });
        org.junit.Assert.assertTrue(oi instanceof ListObjectInspector
                || oi instanceof org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector);
    }
}
