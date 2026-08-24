-- L3.2 功能矩阵：正常值 / null 透传 / 空串 / 短值 / 中文 / 数字入参
SELECT 'L32-NORMAL'  AS c, udf_prefix('abcdefgh')    AS v;
SELECT 'L32-NULL'    AS c, udf_prefix(NULL)          AS v;
SELECT 'L32-EMPTY'   AS c, udf_prefix('')            AS v;
SELECT 'L32-SHORT'   AS c, udf_prefix('ab')          AS v;
SELECT 'L32-CHINESE' AS c, udf_prefix('中文字符串测试') AS v;
SELECT 'L32-NUMERIC' AS c, udf_prefix(CAST(123456 AS STRING)) AS v;
