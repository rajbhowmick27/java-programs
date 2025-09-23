@Configuration
@EnableBatchProcessing
public class ExportJobConfig {

    @Bean
    public ManifestJobListener manifestJobListener(AmazonS3 s3Client,
                                                   @Value("${app.s3.bucket}") String bucketName) {
        return new ManifestJobListener(s3Client, bucketName);
    }

    @Bean
    public Partitioner partitioner(DataSource dataSource) {
        return new TimestampRangePartitioner(dataSource);
    }

    @Bean
    public JdbcPagingItemReader<MyEntity> reader(DataSource dataSource,
                                                 @Value("#{stepExecutionContext['startTs']}") String startTs,
                                                 @Value("#{stepExecutionContext['endTs']}") String endTs) throws Exception {
        OraclePagingQueryProvider provider = new OraclePagingQueryProvider();
        provider.setSelectClause("id, col1, col2, last_updated_timestamp");
        provider.setFromClause("from my_table");
        provider.setWhereClause("where last_updated_timestamp >= TO_TIMESTAMP(:startTs, 'YYYY-MM-DD HH24:MI:SS') " +
                                "and last_updated_timestamp < TO_TIMESTAMP(:endTs, 'YYYY-MM-DD HH24:MI:SS')");
        provider.setSortKeys(Map.of("id", Order.ASCENDING));

        Map<String, Object> params = new HashMap<>();
        params.put("startTs", startTs);
        params.put("endTs", endTs);

        return new JdbcPagingItemReaderBuilder<MyEntity>()
                .name("reader")
                .dataSource(dataSource)
                .queryProvider(provider)
                .parameterValues(params)
                .pageSize(5000)
                .rowMapper(new MyEntityRowMapper())
                .build();
    }

    @Bean
    @StepScope
    public ExcelPartitionWriter writer(AmazonS3 s3Client,
                                       @Value("#{stepExecutionContext['startTs']}") String startTs,
                                       @Value("#{stepExecutionContext['endTs']}") String endTs) {
        LocalDate start = LocalDate.parse(startTs.substring(0, 10));
        LocalDate end = LocalDate.parse(endTs.substring(0, 10));
        PartitionFileSequenceGenerator seqGen = new PartitionFileSequenceGenerator(start, end);
        return new ExcelPartitionWriter(seqGen, s3Client, "my-bucket");
    }

    @Bean
    public Step partitionedStep(StepBuilderFactory stepBuilderFactory,
                                Partitioner partitioner,
                                TaskExecutorPartitionHandler partitionHandler) {
        return stepBuilderFactory.get("partitionedStep")
                .partitioner("exportStep", partitioner)
                .partitionHandler(partitionHandler)
                .build();
    }

    @Bean
    public Step exportStep(StepBuilderFactory stepBuilderFactory,
                           ItemReader<MyEntity> reader,
                           ItemWriter<MyEntity> writer) {
        return stepBuilderFactory.get("exportStep")
                .<MyEntity, MyEntity>chunk(5000)
                .reader(reader)
                .writer(writer)
                .build();
    }

    @Bean
    public TaskExecutorPartitionHandler partitionHandler(Step exportStep,
                                                         TaskExecutor taskExecutor) {
        TaskExecutorPartitionHandler handler = new TaskExecutorPartitionHandler();
        handler.setTaskExecutor(taskExecutor);
        handler.setStep(exportStep);
        handler.setGridSize(4);
        return handler;
    }

    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);   // match gridSize
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("partition-worker-");
        executor.initialize();
        return executor;
    }


    // @Bean
    // public TaskExecutor taskExecutor() {
    //     return new SimpleAsyncTaskExecutor("partition-worker-");
    // }

    @Bean
    public Job exportJob(JobBuilderFactory jobBuilderFactory, Step partitionedStep,ManifestJobListener manifestJobListener) {
        return jobBuilderFactory.get("exportJob")
                .listener(manifestJobListener)
                .start(partitionedStep)
                .build();
    }
}
