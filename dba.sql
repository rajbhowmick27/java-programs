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


SELECT name, value, isdefault, isses_modifiable, issys_modifiable
FROM v$parameter
WHERE name IN ('workarea_size_policy', 'parallel_degree_policy');

SELECT
    tablespace_name,
    ROUND(SUM(phyblkwrt) * 8192 / 1024 / 1024, 2) AS total_mb_written,
    ROUND(SUM(phyblkwrt) / (SUM(elapsed_time) / 1000), 2) AS approx_iops
FROM v$tempstat
GROUP BY tablespace_name;

SELECT
    s.sid,
    s.serial#,
    s.username,
    s.program,
    su.tablespace,
    ROUND(su.blocks * 8192 / 1024 / 1024, 2) AS temp_used_mb
FROM v$sort_usage su
JOIN v$session s ON su.session_addr = s.saddr
ORDER BY temp_used_mb DESC;

SELECT
    name,
    ROUND(value/1024/1024, 2) AS mb
FROM v$sysstat
WHERE name LIKE 'physical write total bytes%' OR name LIKE 'physical write direct temporary tablespace%';


SELECT
    file_id,
    file_name,
    tablespace_name,
    phyrds AS reads,
    phywrts AS writes,
    ROUND(phywrts / ((SYSDATE - startup_time) * 24 * 60 * 60), 2) AS avg_write_iops
FROM v$filestat f
JOIN dba_data_files d ON f.file# = d.file_id
CROSS JOIN (SELECT startup_time FROM v$instance)
ORDER BY avg_write_iops DESC;


SELECT
    f.file#,
    t.file_name,
    t.tablespace_name,
    f.phywrts,
    ROUND(f.phywrts / ((SYSDATE - i.startup_time) * 24*60*60), 2) AS avg_write_iops
FROM v$filestat f
JOIN dba_temp_files t ON f.file# = t.file_id
JOIN v$instance i ON 1=1;

SELECT
    s.sid,
    s.serial#,
    s.username,
    s.program,
    q.sql_id,
    q.sql_text
FROM v$session s
JOIN v$sql q ON s.sql_id = q.sql_id
WHERE q.sql_text LIKE '%DBMS_STATS.GATHER%';



