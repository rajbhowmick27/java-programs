// package com.example.session;
import org.redisson.api.*;
import org.redisson.client.codec.StringCodec;
import org.redisson.api.listener.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class MySessionMapWriter implements MapWriter<String, MySession> {

    private static final Logger log = LoggerFactory.getLogger(MySessionMapWriter.class);

    private final RedissonClient redisson;
    private final MySessionDao dao;

    // retry config
    private final int maxAttempts = 8;
    private final long baseBackoffMs = 250; // base for exponential backoff
    private final long maxBackoffMs = 10_000L;

    // lock config
    private final long lockWaitMs = 500;    // wait for lock acquisition
    private final long lockLeaseMs = 5_000; // lock lease time (should exceed expected DB write time)

    // Redis structures for pending entries
    private final RMap<String, PendingRecord> pendingMap;   // holds pending serialized session + metadata
    private final RQueue<String> retryQueue;               // target queue for delayed retries
    private final RDelayedQueue<String> delayedRetry;

    private final ExecutorService retryWorker;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public MySessionMapWriter(RedissonClient redisson, MySessionDao dao) {
        this.redisson = redisson;
        this.dao = dao;

        // Use a JSON/codec or StringCodec with a serializer for pending objects
        this.pendingMap = redisson.getMap("pending:verified:v2"); // codec set on RedissonClient config
        this.retryQueue = redisson.getQueue("pending:verified:retry:v2");
        this.delayedRetry = redisson.getDelayedQueue(retryQueue);

        this.retryWorker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "session-pending-retry");
            t.setDaemon(true);
            return t;
        });
    }

    @PostConstruct
    private void startRetryWorker() {
        retryWorker.submit(() -> {
            log.info("Starting pending session retry worker");
            while (running.get()) {
                try {
                    // blocking take from retryQueue
                    String sessionId = retryQueue.take();
                    if (sessionId == null) continue;
                    handleRetry(sessionId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception ex) {
                    log.error("Retry worker error", ex);
                }
            }
            log.info("Stopped pending session retry worker");
        });
    }

    @PreDestroy
    private void stop() {
        running.set(false);
        retryWorker.shutdownNow();
        try { retryWorker.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        delayedRetry.destroy(); // cleanup
    }

    @Override
    public void write(Map<String, MySession> items) {
        // iterate entries; items size is usually small if batchSize=1, but handle generically
        for (Map.Entry<String, MySession> e : items.entrySet()) {
            String sessionId = e.getKey();
            MySession session = e.getValue();

            // Acquire a fair lock for sessionId to avoid starvation
            RFairLock lock = redisson.getFairLock("lock:session:" + sessionId);
            boolean locked = false;
            try {
                // Try to acquire within lockWaitMs; set a lease time to avoid orphan locks.
                locked = lock.tryLock(lockWaitMs, lockLeaseMs, TimeUnit.MILLISECONDS);
                if (!locked) {
                    log.warn("Could not acquire lock for session {}, requeueing", sessionId);
                    scheduleRetry(sessionId, session, 1);
                    continue;
                }
                // We have the lock; do an ordered persist
                persistWithOrdering(sessionId, session);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for lock for {}", sessionId);
                scheduleRetry(sessionId, session, 1);
            } catch (Exception ex) {
                log.error("Error persisting session " + sessionId, ex);
                // on unexpected error, increment attempt and schedule retry
                scheduleRetry(sessionId, session, 1);
            } finally {
                if (locked) {
                    try {
                        lock.unlock();
                    } catch (Exception ex) {
                        log.warn("Failed to unlock for session {}: {}", sessionId, ex.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Persist ensuring ordering: if VERIFIED arrives but COMPLETED not in DB, either synthesize completed
     * (cheaper) or buffer + retry. Here we attempt to synthesize completed (see note below).
     *
     * This method must run with the per-key lock already held.
     */
    @Transactional
    protected void persistWithOrdering(String sessionId, MySession session) {
        if (session.isVerified()) {
            // Verified arrived; ensure completed exists first
            if (!dao.existsCompleted(sessionId)) {
                // Strategy: synthesize completed row from current session snapshot and write it.
                // If business forbids synthesis, instead call scheduleRetry(...) and return.
                MySession completed = session.toCompletedSnapshot();
                dao.upsertCompleted(completed);
                log.info("Synthesized COMPLETED for {} before writing VERIFIED", sessionId);
            }
            dao.upsertVerified(session);
            log.info("Persisted VERIFIED for {}", sessionId);
            // Remove pending if any
            pendingMap.remove(sessionId);
        } else if (session.isCompleted()) {
            dao.upsertCompleted(session);
            log.info("Persisted COMPLETED for {}", sessionId);
            // if there's a pending verified, perform it immediately (same lock)
            PendingRecord pending = pendingMap.remove(sessionId);
            if (pending != null && pending.getSession() != null) {
                dao.upsertVerified(pending.getSession());
                log.info("Flushed pending VERIFIED for {}", sessionId);
            }
        } else {
            dao.upsertState(session);
            log.info("Persisted other state {} for {}", session.getState(), sessionId);
        }
    }

    // Called when lock cannot be acquired or verified too early (if chosen to buffer)
    private void scheduleRetry(String sessionId, MySession session, int initialAttempt) {
        PendingRecord existing = pendingMap.get(sessionId);
        int attempts = initialAttempt;
        if (existing != null) attempts = Math.max(existing.getAttempts(), initialAttempt);
        PendingRecord rec = new PendingRecord(session, attempts, System.currentTimeMillis());
        pendingMap.put(sessionId, rec);
        long delay = nextBackoffMillis(attempts);
        delayedRetry.offer(sessionId, delay, TimeUnit.MILLISECONDS);
        log.debug("Scheduled retry for {} attempts={} delayMs={}", sessionId, attempts, delay);
    }

    // Exponential backoff with jitter
    private long nextBackoffMillis(int attempts) {
        long backoff = Math.min(maxBackoffMs, baseBackoffMs * (1L << Math.min(attempts - 1, 10)));
        // add jitter ±20%
        long jitter = (long)(backoff * (ThreadLocalRandom.current().nextDouble(-0.2, 0.2)));
        return Math.max(100L, backoff + jitter);
    }

    // Handles items popped from retryQueue (delayed retries)
    private void handleRetry(String sessionId) {
        PendingRecord rec = pendingMap.get(sessionId);
        if (rec == null) return; // nothing to do
        if (rec.getAttempts() >= maxAttempts) {
            // Move to dead-letter / alert and remove from pendingMap
            log.error("Session {} exceeded max attempts {} - moving to DLQ", sessionId, rec.getAttempts());
            pendingMap.remove(sessionId);
            // TODO: publish event to an alerting/monitoring system or push to DLQ queue
            return;
        }
        // Attempt to acquire lock quickly
        RFairLock lock = redisson.getFairLock("lock:session:" + sessionId);
        boolean locked = false;
        try {
            locked = lock.tryLock(Math.min(200, lockWaitMs), lockLeaseMs, TimeUnit.MILLISECONDS);
            if (!locked) {
                // increment attempts and reschedule
                rec.incrementAttempts();
                pendingMap.put(sessionId, rec);
                delayedRetry.offer(sessionId, nextBackoffMillis(rec.getAttempts()), TimeUnit.MILLISECONDS);
                return;
            }
            // We have the lock; try to persist
            persistWithOrdering(sessionId, rec.getSession());
            // success -> remove pending
            pendingMap.remove(sessionId);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Retry handler interrupted for {}", sessionId);
        } catch (Exception ex) {
            log.error("Error during retry for " + sessionId, ex);
            rec.incrementAttempts();
            pendingMap.put(sessionId, rec);
            delayedRetry.offer(sessionId, nextBackoffMillis(rec.getAttempts()), TimeUnit.MILLISECONDS);
        } finally {
            if (locked) try { lock.unlock(); } catch (Exception ignore) {}
        }
    }

    // Simple pending record container, stored in Redis map (must be serializable or use codec)
    public static class PendingRecord {
        private MySession session;
        private int attempts;
        private long firstSeenMs;

        public PendingRecord() {}

        public PendingRecord(MySession session, int attempts, long firstSeenMs) {
            this.session = session;
            this.attempts = attempts;
            this.firstSeenMs = firstSeenMs;
        }

        public MySession getSession() { return session; }
        public int getAttempts() { return attempts; }
        public long getFirstSeenMs() { return firstSeenMs; }

        public void incrementAttempts() { this.attempts++; }
    }
}
