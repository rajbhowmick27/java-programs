public class ExcelPartitionWriter implements ItemWriter<MyEntity>, ItemStream {

    private SXSSFWorkbook workbook;
    private Sheet sheet;
    private FileOutputStream fos;
    private File currentFile;
    private int currentRow = 0;
    private int sheetRowCount = 0;

    private final PartitionFileSequenceGenerator sequenceGenerator;
    private final AmazonS3 s3Client;
    private final String bucketName;

    private final long maxBytes = 500L * 1024 * 1024; // 500 MB

    // Thread-safe manifest list per partition
    private final List<Map<String, Object>> manifestEntries =
            Collections.synchronizedList(new ArrayList<>());

    public ExcelPartitionWriter(PartitionFileSequenceGenerator sequenceGenerator,
                                AmazonS3 s3Client,
                                String bucketName) {
        this.sequenceGenerator = sequenceGenerator;
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    @Override
    public void write(List<? extends MyEntity> items) throws Exception {
        if (workbook == null) startNewFile();

        for (MyEntity e : items) {

            // Create new sheet if Excel row limit reached
            if (sheetRowCount >= 1_048_576) {
                sheet = workbook.createSheet("Sheet" + (workbook.getNumberOfSheets() + 1));
                sheetRowCount = 0;
                addHeaderRow();
            }

            Row row = sheet.createRow(sheetRowCount++);
            populateRow(row, e);
            currentRow++;

            // Periodically flush & check file size
            if ((currentRow % 500) == 0) {
                workbook.write(fos);
                fos.flush();
                if (currentFile.length() >= maxBytes) {
                    // Without expiration
                    closeAndUpload(Optional.empty());
                    
                    // With expiration of 7 days
                    // closeAndUpload(Optional.of(Duration.ofDays(7)));
                    
                    startNewFile();
                }
            }
        }
    }

    private void startNewFile() throws Exception {
        workbook = new SXSSFWorkbook(1000); // streaming workbook
        sheet = workbook.createSheet("Sheet1");
        sheetRowCount = 0;
        addHeaderRow();

        String fileName = sequenceGenerator.nextFileName();
        currentFile = File.createTempFile(fileName.replace(".xlsx", ""), ".xlsx");
        fos = new FileOutputStream(currentFile);
        currentRow = 0;
    }

    private void addHeaderRow() {
        Row header = sheet.createRow(sheetRowCount++);
        header.createCell(0).setCellValue("ID");
        header.createCell(1).setCellValue("Col1");
        header.createCell(2).setCellValue("Col2");
        // Add more headers as needed
    }

    private void populateRow(Row row, MyEntity e) {
        row.createCell(0).setCellValue(e.getId());
        row.createCell(1).setCellValue(e.getCol1());
        row.createCell(2).setCellValue(e.getCol2());
        // Add more columns as needed
    }

    private void closeAndUpload(Optional<Duration> expirationOpt) throws Exception {
        workbook.write(fos);
        fos.close();
        workbook.dispose();
        workbook.close();
    
        String s3Key = "exports/" + currentFile.getName();
        s3Client.putObject(bucketName, s3Key, currentFile);
    
        String downloadUrl;
        if (expirationOpt.isPresent()) {
            // Generate pre-signed URL with expiration
            Date expiration = Date.from(Instant.now().plus(expirationOpt.get()));
            GeneratePresignedUrlRequest presignedRequest =
                    new GeneratePresignedUrlRequest(bucketName, s3Key)
                            .withMethod(HttpMethod.GET)
                            .withExpiration(expiration);
            downloadUrl = s3Client.generatePresignedUrl(presignedRequest).toString();
        } else {
            // Generate URL without expiration (public URL style)
            downloadUrl = s3Client.getUrl(bucketName, s3Key).toString();
        }
    
        // Add metadata to manifest
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("fileName", currentFile.getName());
        meta.put("s3Key", s3Key);
        meta.put("sizeBytes", currentFile.length());
        meta.put("rowCount", currentRow);
        meta.put("downloadUrl", downloadUrl);
        manifestEntries.add(meta);
    
        currentFile.delete();
        workbook = null;
        fos = null;
    }


    // ------------------- ItemStream methods -------------------

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        // Nothing to initialize at open
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        /*
         * This method is called **periodically by Spring Batch** during the step execution
         * (controlled by the chunk interval). Its purpose is to persist any state you want
         * to survive a job restart. Here we store a **copy** of the manifest entries for the partition.
         *
         * Thread safety:
         * - Each partition has its own StepExecution and ExecutionContext.
         * - manifestEntries is thread-safe using synchronizedList.
         * - We write a **new ArrayList copy** into ExecutionContext to avoid concurrent modification.
         */
        executionContext.put("manifestEntries", new ArrayList<>(manifestEntries));
    }

    @Override
    public void close() throws ItemStreamException {
        try {
            if (workbook != null) {
                closeAndUpload();
            }
        } catch (Exception e) {
            throw new ItemStreamException("Error closing workbook", e);
        }
    }
}
