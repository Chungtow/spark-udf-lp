package com.liangpu.udf.geo;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.StructObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * st_boundingbox 单元测试：外接矩形 STRUCT&lt;xmin,ymin,xmax,ymax&gt;。
 */
public class StBoundingboxUdfTest {

    private StBoundingboxUdf udf;

    @Before
    public void setUp() throws Exception {
        udf = new StBoundingboxUdf();
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

    private List<Object> row(String wkt) throws Exception {
        return (List<Object>) udf.evaluate(args(wkt));
    }

    private static double d(Object o) {
        return ((org.apache.hadoop.io.DoubleWritable) o).get();
    }

    @Test
    public void testPolygon() throws Exception {
        List<Object> r = row("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))");
        assertEquals(4, r.size());
        assertEquals(0.0, d(r.get(0)), 1e-9); // xmin
        assertEquals(0.0, d(r.get(1)), 1e-9); // ymin
        assertEquals(10.0, d(r.get(2)), 1e-9); // xmax
        assertEquals(10.0, d(r.get(3)), 1e-9); // ymax
    }

    @Test
    public void testPoint() throws Exception {
        // 点退化为零宽矩形
        List<Object> r = row("POINT (2 4)");
        assertEquals(2.0, d(r.get(0)), 1e-9);
        assertEquals(4.0, d(r.get(1)), 1e-9);
        assertEquals(2.0, d(r.get(2)), 1e-9);
        assertEquals(4.0, d(r.get(3)), 1e-9);
    }

    @Test
    public void testNegativeCoords() throws Exception {
        List<Object> r = row("POINT (-180 -90)");
        assertEquals(-180.0, d(r.get(0)), 1e-9);
        assertEquals(-90.0, d(r.get(1)), 1e-9);
    }

    @Test
    public void testLineString() throws Exception {
        List<Object> r = row("LINESTRING (2 4, 10 8)");
        assertEquals(2.0, d(r.get(0)), 1e-9);
        assertEquals(4.0, d(r.get(1)), 1e-9);
        assertEquals(10.0, d(r.get(2)), 1e-9);
        assertEquals(8.0, d(r.get(3)), 1e-9);
    }

    @Test
    public void testEmptyGeometry() throws Exception {
        assertNull(udf.evaluate(args("POINT EMPTY")));
    }

    @Test
    public void testNullInput() throws Exception {
        assertNull(udf.evaluate(args((Object) null)));
    }

    @Test
    public void testStructObjectInspectorFields() throws Exception {
        StBoundingboxUdf fresh = new StBoundingboxUdf();
        ObjectInspector oi = fresh.initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
        assertTrue(oi instanceof StructObjectInspector);
        List<?> fieldRefs = ((StructObjectInspector) oi).getAllStructFieldRefs();
        assertEquals(4, fieldRefs.size());
        assertEquals("xmin", ((org.apache.hadoop.hive.serde2.objectinspector.StructField) fieldRefs.get(0)).getFieldName());
        assertEquals("ymin", ((org.apache.hadoop.hive.serde2.objectinspector.StructField) fieldRefs.get(1)).getFieldName());
        assertEquals("xmax", ((org.apache.hadoop.hive.serde2.objectinspector.StructField) fieldRefs.get(2)).getFieldName());
        assertEquals("ymax", ((org.apache.hadoop.hive.serde2.objectinspector.StructField) fieldRefs.get(3)).getFieldName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidWkt() throws Exception {
        udf.evaluate(args("NOT A WKT"));
    }

    @Test(expected = UDFArgumentException.class)
    public void testWrongArgCount() throws Exception {
        new StBoundingboxUdf().initialize(new ObjectInspector[]{
                PrimitiveObjectInspectorFactory.javaStringObjectInspector,
                PrimitiveObjectInspectorFactory.javaStringObjectInspector
        });
    }
}
