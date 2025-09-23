public class PartitionFileSequenceGenerator {
    private final AtomicInteger counter = new AtomicInteger(1);
    private final LocalDate start;
    private final LocalDate end;

    public PartitionFileSequenceGenerator(LocalDate start, LocalDate end) {
        this.start = start;
        this.end = end;
    }

    public String nextFileName() {
        int seq = counter.getAndIncrement();
        return String.format("output-%s-%s-%d.xlsx", start, end, seq);
    }
}
