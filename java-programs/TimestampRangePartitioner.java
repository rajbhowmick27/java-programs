public class TimestampRangePartitioner implements Partitioner {

    private final JdbcTemplate jdbcTemplate;

    public TimestampRangePartitioner(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        Map<String, ExecutionContext> result = new HashMap<>();

        // Find min and max timestamp from DB
        LocalDateTime minTs = jdbcTemplate.queryForObject(
            "SELECT MIN(last_updated_timestamp) FROM my_table", LocalDateTime.class);
        LocalDateTime maxTs = jdbcTemplate.queryForObject(
            "SELECT MAX(last_updated_timestamp) FROM my_table", LocalDateTime.class);

        LocalDateTime start = minTs;
        int partitionIndex = 0;

        while (start.isBefore(maxTs)) {
            LocalDateTime end = start.plusMonths(3).isAfter(maxTs) ? maxTs : start.plusMonths(3);

            ExecutionContext ctx = new ExecutionContext();
            ctx.putString("startTs", start.toString());
            ctx.putString("endTs", end.toString());
            ctx.putInt("partitionIndex", partitionIndex);

            result.put("partition" + partitionIndex, ctx);

            start = end;
            partitionIndex++;
        }
        return result;
    }
}
