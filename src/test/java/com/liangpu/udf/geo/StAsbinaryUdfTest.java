package com.liangpu.udf.geo;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * st_asbinary 单元测试：地理对象 → WKB 字节（byte[]）。
 */
public class StAsbinaryUdfTest {

    private StAsbinaryUdf udf;

    @Before
    public void setUp() throws Exception {
        udf = new StAsbinaryUdf();
        udf.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }

    private GenericUDF.DeferredObject[] args(Object... values) {
        GenericUDF.DeferredObject[] result = new GenericUDF.DeferredObject[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = new GenericUDF.DeferredJavaObject(values[i]);
        }
        return result;
    }

    @Test
    public void testPointWkb() throws Exception {
        byte[] wkb = (byte[]) udf.evaluate(args("POINT (2 4)"));
        assertEquals(21, wkb.length);
        // JTS WKBWriter 默认 big-endian：00 + 00000001(POINT)
        assertEquals(0x00, wkb[0] & 0xff);
        assertEquals(0x01, wkb[4] & 0xff); // POINT 类型
        // 回读验证
        assertEquals("POINT (2 4)", GeoUtils.toWkt(GeoUtils.parseWkb(wkb)));
    }

    @Test
    public void testNegativeCoords() throws Exception {
        byte[] wkb = (byte[]) udf.evaluate(args("POINT (-90 -180)"));
        assertEquals("POINT (-90 -180)", GeoUtils.toWkt(GeoUtils.parseWkb(wkb)));
    }

    @Test
    public void testLineString() throws Exception {
        byte[] wkb = (byte[]) udf.evaluate(args("LINESTRING (2 4, 3 5)"));
        assertEquals("LINESTRING (2 4, 3 5)", GeoUtils.toWkt(GeoUtils.parseWkb(wkb)));
    }

    @Test
    public void testMatchesToWkb() throws Exception {
        // 与 GeoUtils.toWkb 结果一致
        byte[] expected = GeoUtils.toWkb(GeoUtils.parseWkt("POINT (2 4)"));
        assertArrayEquals(expected, (byte[]) udf.evaluate(args("POINT (2 4)")));
    }

    @Test
    public void testNullInput() throws Exception {
        assertNull(udf.evaluate(args((Object) null)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidWkt() throws Exception {
        udf.evaluate(args("NOT A WKT"));
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        new StAsbinaryUdf().initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }
}
