package com.middleproject.reminder.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(name = "scheduler.aws.enabled", havingValue = "true")
class SchedulerReconciliationWorker implements SmartLifecycle {
    private final SchedulerOutboxService service;
    private final long delay;
    private final AtomicBoolean running = new AtomicBoolean();
    private ExecutorService executor;
    SchedulerReconciliationWorker(SchedulerOutboxService service, @Value("${scheduler.reconcile-delay-ms:5000}") long delay) { this.service = service; this.delay = Math.max(100, delay); }
    public void start() { if (running.compareAndSet(false, true)) { executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "scheduler-reconciliation")); executor.submit(() -> loop()); } }
    private void loop() { long backoff = delay; while (running.get()) { try { service.reconcile(50); backoff = delay; } catch (RuntimeException ignored) { backoff = Math.min(60000, Math.max(delay, backoff * 2)); } waitFor(backoff); } }
    private void waitFor(long millis) { try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
    public void stop() { if (running.compareAndSet(true, false) && executor != null) { executor.shutdownNow(); try { executor.awaitTermination(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } } }
    public boolean isRunning() { return running.get(); }
    public boolean isAutoStartup() { return true; }
    public int getPhase() { return Integer.MAX_VALUE - 100; }
    public void stop(Runnable callback) { stop(); callback.run(); }
}

@Component
@ConditionalOnProperty(name = "delivery.sqs.enabled", havingValue = "true")
class ReminderDeliveryWorker implements SmartLifecycle {
    private final ReminderDeliveryConsumer consumer;
    private final long delay;
    private final AtomicBoolean running = new AtomicBoolean();
    private ExecutorService executor;
    ReminderDeliveryWorker(ReminderDeliveryConsumer consumer, @Value("${delivery.sqs.error-backoff-ms:1000}") long delay) { this.consumer = consumer; this.delay = Math.max(100, delay); }
    public void start() { if (running.compareAndSet(false, true)) { executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "reminder-delivery")); executor.submit(this::loop); } }
    private void loop() { long backoff = delay; while (running.get()) { try { consumer.pollOnce(); backoff = delay; } catch (RuntimeException ignored) { backoff = Math.min(60000, Math.max(delay, backoff * 2)); } waitFor(backoff); } }
    private void waitFor(long millis) { try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
    public void stop() { if (running.compareAndSet(true, false) && executor != null) { executor.shutdownNow(); try { executor.awaitTermination(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } } }
    public boolean isRunning() { return running.get(); }
    public boolean isAutoStartup() { return true; }
    public int getPhase() { return Integer.MAX_VALUE - 100; }
    public void stop(Runnable callback) { stop(); callback.run(); }
}
