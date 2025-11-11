SELECT
    tablespace_name,
    ROUND(SUM(bytes_used) / 1024 / 1024, 2) AS used_mb,
    ROUND(SUM(bytes_free) / 1024 / 1024, 2) AS free_mb,
    ROUND(SUM(bytes_used + bytes_free) / 1024 / 1024, 2) AS total_mb,
    ROUND(SUM(bytes_used) * 100 / SUM(bytes_used + bytes_free), 2) AS pct_used
FROM v$temp_space_header
GROUP BY tablespace_name;

SELECT tablespace_name, SUM(bytes_used)/1024/1024 used_mb
FROM v$temp_space_header
GROUP BY tablespace_name;


SELECT name, ROUND(value/1024/1024,2) mb
FROM v$pgastat
WHERE name IN ('total PGA allocated','total PGA inuse','maximum PGA allocated');


SELECT * FROM v$sysstat WHERE name LIKE 'workarea executions - %';
