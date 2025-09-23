@Component
public class ManifestJobListener implements JobExecutionListener {

    private final AmazonS3 s3Client;
    private final String bucketName;

    public ManifestJobListener(AmazonS3 s3Client, @Value("${app.s3.bucket}") String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        List<Map<String, Object>> globalManifest = new ArrayList<>();

        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            ExecutionContext ec = stepExecution.getExecutionContext();
            if (ec.containsKey("manifestEntries")) {
                List<Map<String, Object>> partitionEntries =
                        (List<Map<String, Object>>) ec.get("manifestEntries");
                globalManifest.addAll(partitionEntries); // append safely
            }
        }

        // Write final global manifest to S3
        try {
            ObjectMapper mapper = new ObjectMapper();
            File manifestFile = File.createTempFile("global-manifest", ".json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(manifestFile, globalManifest);

            String s3Key = "exports/global-manifest-" +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".json";

            s3Client.putObject(bucketName, s3Key, manifestFile);
            manifestFile.delete();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create global manifest", e);
        }
    }
}
