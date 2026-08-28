package com.liangpu.udf.geo;

import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * GeoUtils 工具类单元测试：WKT/WKB 解析输出、经度归一化、haversine 锚点、距离、自动闭合。
 */
public class GeoUtilsTest {

    // ---------- toDouble ----------

    @Test
    public void testToDoubleJavaNumber() {
        assertEquals(2.5, GeoUtils.toDouble(2.5), 1e-9);
        assertEquals(2.0, GeoUtils.toDouble(2), 1e-9);
        assertEquals(4.0, GeoUtils.toDouble(4L), 1e-9);
    }

    @Test
    public void testToDoubleHiveWritable() {
        // Spark 运行时经 HiveGenericUDF 传入 Writable 数值
        assertEquals(2.0, GeoUtils.toDouble(new org.apache.hadoop.io.IntWritable(2)), 1e-9);
        assertEquals(2.5, GeoUtils.toDouble(new org.apache.hadoop.io.DoubleWritable(2.5)), 1e-9);
        assertEquals(4.0, GeoUtils.toDouble(new org.apache.hadoop.io.LongWritable(4L)), 1e-9);
        assertEquals(3.0, GeoUtils.toDouble(new org.apache.hadoop.io.FloatWritable(3.0f)), 1e-9);
        assertEquals(5.5, GeoUtils.toDouble(new org.apache.hadoop.io.Text("5.5")), 1e-9);
    }

    @Test
    public void testToDoubleHiveDecimalWritable() {
        // Spark 3.3 常量折叠路径下嵌套 UDF 调用的数值字面量被包装为 HiveDecimalWritable
        assertEquals(8000.0, GeoUtils.toDouble(
                new org.apache.hadoop.hive.serde2.io.HiveDecimalWritable(
                        org.apache.hadoop.hive.common.type.HiveDecimal.create("8000"))), 1e-9);
        assertEquals(0.0, GeoUtils.toDouble(
                new org.apache.hadoop.hive.serde2.io.HiveDecimalWritable(
                        org.apache.hadoop.hive.common.type.HiveDecimal.create("0.0"))), 1e-9);
        assertEquals(2.5, GeoUtils.toDouble(
                new org.apache.hadoop.hive.serde2.io.HiveDecimalWritable(
                        org.apache.hadoop.hive.common.type.HiveDecimal.create("2.5"))), 1e-9);
    }

    @Test
    public void testToDoubleHiveDecimal() {
        // VALUES 表字面量列（如 SELECT st_geogpoint(lng,lat) FROM VALUES (3.0,4.0) tmp(lng,lat)）
        // 走另一条常量折叠路径，数值包装为非 Writable 的 HiveDecimal，需显式兼容（集群实测）
        assertEquals(3.0, GeoUtils.toDouble(
                org.apache.hadoop.hive.common.type.HiveDecimal.create("3.0")), 1e-9);
        assertEquals(8000.0, GeoUtils.toDouble(
                org.apache.hadoop.hive.common.type.HiveDecimal.create("8000")), 1e-9);
        assertEquals(2.5, GeoUtils.toDouble(
                org.apache.hadoop.hive.common.type.HiveDecimal.create("2.5")), 1e-9);
        assertEquals(-80.0, GeoUtils.toDouble(
                org.apache.hadoop.hive.common.type.HiveDecimal.create("-80")), 1e-9);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToDoubleUnsupported() {
        GeoUtils.toDouble("abc");
    }

    // ---------- normalizeLon ----------

    @Test
    public void testNormalizeLonInRange() {
        assertEquals(0.0, GeoUtils.normalizeLon(0), 1e-9);
        assertEquals(179.5, GeoUtils.normalizeLon(179.5), 1e-9);
        assertEquals(-179.5, GeoUtils.normalizeLon(-179.5), 1e-9);
    }

    @Test
    public void testNormalizeLonBoundary() {
        // 180 → -180（半开区间 [-180,180)）
        assertEquals(-180.0, GeoUtils.normalizeLon(180), 1e-9);
        assertEquals(-180.0, GeoUtils.normalizeLon(-180), 1e-9);
    }

    @Test
    public void testNormalizeLonOverflow() {
        // 270 → -90、360 → 0、-190 → 170、540 → 180? 不：540 → -180
        assertEquals(-90.0, GeoUtils.normalizeLon(270), 1e-9);
        assertEquals(0.0, GeoUtils.normalizeLon(360), 1e-9);
        assertEquals(170.0, GeoUtils.normalizeLon(-190), 1e-9);
        assertEquals(-180.0, GeoUtils.normalizeLon(540), 1e-9);
    }

    // ---------- haversineMeters ----------

    @Test
    public void testHaversineAnchorMcDoc() {
        // MC 文档锚点：ST_DISTANCE(ST_GEOGPOINT(0,0), ST_GEOGPOINT(1,1)) ≈ 157249.63
        double d = GeoUtils.haversineMeters(0, 0, 1, 1);
        assertEquals(157249.63, d, 100);
    }

    @Test
    public void testHaversineSamePointZero() {
        assertEquals(0.0, GeoUtils.haversineMeters(30, 60, 30, 60), 1e-6);
    }

    @Test
    public void testHaversineEquatorOneDegree() {
        // 赤道上 1° 经度 ≈ 111194.9m
        double d = GeoUtils.haversineMeters(0, 0, 1, 0);
        assertEquals(111195, d, 100);
    }

    @Test
    public void testHaversineCommutative() {
        double d1 = GeoUtils.haversineMeters(-73.9857, 40.7484, 116.4074, 39.9042);
        double d2 = GeoUtils.haversineMeters(116.4074, 39.9042, -73.9857, 40.7484);
        assertEquals(d1, d2, 1e-6);
    }

    // ---------- parseWkt / toWkt ----------

    @Test
    public void testParseWktPoint() {
        Geometry g = GeoUtils.parseWkt("POINT (2 4)");
        assertTrue(g instanceof Point);
        assertEquals(2.0, g.getCoordinate().x, 1e-9);
        assertEquals(4.0, g.getCoordinate().y, 1e-9);
    }

    @Test
    public void testParseWktNull() {
        assertNull(GeoUtils.parseWkt(null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseWktInvalid() {
        GeoUtils.parseWkt("NOT A WKT");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseWktEmptyString() {
        GeoUtils.parseWkt("");
    }

    @Test
    public void testToWktNormalizes() {
        // 无空格输入 → 规范化输出
        Geometry g = GeoUtils.parseWkt("POINT(2 4)");
        assertEquals("POINT (2 4)", GeoUtils.toWkt(g));
    }

    @Test
    public void testToWktEmpty() {
        Geometry empty = GeoUtils.parseWkt("POINT EMPTY");
        assertTrue(empty.isEmpty());
        assertEquals("GEOMETRYCOLLECTION EMPTY", GeoUtils.toWkt(empty));
    }

    @Test
    public void testToWktNull() {
        assertNull(GeoUtils.toWkt(null));
    }

    // ---------- WKB ----------

    @Test
    public void testWkbRoundTrip() throws Exception {
        Geometry g = GeoUtils.parseWkt("POINT (2 4)");
        byte[] wkb = GeoUtils.toWkb(g);
        // JTS WKBWriter 默认 big-endian：00 + 类型 00000001(POINT) + 8B x + 8B y = 21 字节
        assertEquals(21, wkb.length);
        assertEquals(0x00, wkb[0] & 0xff); // big-endian 字节序标记
        assertEquals(0x00, wkb[1] & 0xff);
        assertEquals(0x00, wkb[2] & 0xff);
        assertEquals(0x00, wkb[3] & 0xff);
        assertEquals(0x01, wkb[4] & 0xff); // POINT 类型
        Geometry back = GeoUtils.parseWkb(wkb);
        assertEquals("POINT (2 4)", GeoUtils.toWkt(back));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseWkbInvalid() {
        GeoUtils.parseWkb(new byte[]{0x01, 0x02, 0x03});
    }

    // ---------- distanceMeters ----------

    @Test
    public void testDistanceMetersPointPoint() {
        Geometry g1 = GeoUtils.parseWkt("POINT (0 0)");
        Geometry g2 = GeoUtils.parseWkt("POINT (1 1)");
        assertEquals(157249.63, GeoUtils.distanceMeters(g1, g2), 100);
    }

    @Test
    public void testDistanceMetersPointToLine() {
        // 点到线段的最近点对距离 > 0
        Geometry pt = GeoUtils.parseWkt("POINT (0 0)");
        Geometry line = GeoUtils.parseWkt("LINESTRING (10 10, 20 20)");
        double d = GeoUtils.distanceMeters(pt, line);
        assertTrue(d > 1500000); // (0,0) 到 (15,15) 约 2361km
    }

    // ---------- ensureClosed ----------

    @Test
    public void testEnsureClosedAutoClose() {
        Coordinate[] coords = new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(4, 0), new Coordinate(4, 4), new Coordinate(0, 4)};
        Coordinate[] closed = GeoUtils.ensureClosed(coords);
        assertEquals(5, closed.length);
        assertTrue(closed[4].equals2D(coords[0]));
    }

    @Test
    public void testEnsureClosedAlreadyClosed() {
        Coordinate[] coords = new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(4, 0), new Coordinate(4, 4), new Coordinate(0, 4),
                new Coordinate(0, 0)};
        Coordinate[] closed = GeoUtils.ensureClosed(coords);
        assertSame(coords, closed); // 已闭合原样返回（同一引用）
        assertEquals(5, closed.length);
    }

    @Test
    public void testEnsureClosedEmpty() {
        Coordinate[] closed = GeoUtils.ensureClosed(new Coordinate[0]);
        assertEquals(0, closed.length);
    }

    // ---------- distinctVertices ----------

    @Test
    public void testDistinctVertices() {
        Coordinate[] coords = new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(1, 1), new Coordinate(1, 1), new Coordinate(2, 2)};
        assertEquals(3, GeoUtils.distinctVertices(coords));
    }

    @Test
    public void testDistinctVerticesAllSame() {
        Coordinate[] coords = new Coordinate[]{
                new Coordinate(2, 4), new Coordinate(2, 4)};
        assertEquals(1, GeoUtils.distinctVertices(coords));
    }
}
