import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public class TimingInterceptor implements ClientHttpRequestInterceptor {
    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution exec) throws IOException {
        long t0 = System.nanoTime();
        log.info("REQ_START {} {} thread={}", request.getMethod(), request.getURI(), Thread.currentThread().getName());
        try {
            ClientHttpResponse resp = exec.execute(request, body);
            long t1 = System.nanoTime();
            log.info("REQ_END {} {} elapsedMs={} thread={}", request.getMethod(), request.getURI(), (t1-t0)/1_000_000, Thread.currentThread().getName());
            return resp;
        } catch (IOException e) {
            long t2 = System.nanoTime();
            log.info("REQ_ERROR {} {} elapsedMs={} thread={} ex={}", request.getMethod(), request.getURI(), (t2-t0)/1_000_000, Thread.currentThread().getName(), e.toString());
            throw e;
        }
    }
}

PoolingHttpClientConnectionManager cm = ...;
ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
ses.scheduleAtFixedRate(() -> {
    System.out.println("CM stats total: " + cm.getTotalStats());
}, 0, 5, TimeUnit.SECONDS);


org.apache.hc.client5.http.impl=DEBUG
org.apache.hc.core5.http.io=DEBUG
