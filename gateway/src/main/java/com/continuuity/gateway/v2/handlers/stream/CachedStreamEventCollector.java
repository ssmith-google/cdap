package com.continuuity.gateway.v2.handlers.stream;

import com.continuuity.api.flow.flowlet.StreamEvent;
import com.continuuity.common.conf.CConfiguration;
import com.continuuity.common.queue.QueueName;
import com.continuuity.data.operation.executor.OperationExecutor;
import com.continuuity.data.operation.ttqueue.QueueEntry;
import com.continuuity.data2.queue.QueueClientFactory;
import com.continuuity.gateway.Constants;
import com.continuuity.gateway.v2.GatewayConstants;
import com.continuuity.streamevent.StreamEventCodec;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This class caches stream events and enqueues them in batch.
 */
public class CachedStreamEventCollector extends AbstractIdleService {
  private static final Logger LOG = LoggerFactory.getLogger(CachedStreamEventCollector.class);

  private final Timer flushTimer;

  private final StreamEventCodec serializer = new StreamEventCodec();

  private final CachedStreamEvents cachedStreamEvents;

  private final long flushIntervalMs;

  private final ExecutorService callbackExecutorService;

  @Inject
  public CachedStreamEventCollector(CConfiguration cConfig, OperationExecutor opex,
                                    QueueClientFactory queueClientFactory) {
    this.flushTimer = new Timer("stream-rest-flush-thread", true);

    int maxCachedEventsPerStream = cConfig.getInt(GatewayConstants.ConfigKeys.MAX_CACHED_EVENTS_PER_STREAM_NUM,
                                                  GatewayConstants.DEFAULT_MAX_CACHED_EVENTS_PER_STREAM_NUM);
    int maxCachedEvents = cConfig.getInt(GatewayConstants.ConfigKeys.MAX_CACHED_STREAM_EVENTS_NUM,
                                         GatewayConstants.DEFAULT_MAX_CACHED_STREAM_EVENTS_NUM);
    long maxCachedSizeBytes = cConfig.getLong(GatewayConstants.ConfigKeys.MAX_CACHED_STREAM_EVENTS_BYTES,
                                              GatewayConstants.DEFAULT_MAX_CACHED_STREAM_EVENTS_BYTES);
    this.flushIntervalMs = cConfig.getLong(GatewayConstants.ConfigKeys.STREAM_EVENTS_FLUSH_INTERVAL_MS,
                                           GatewayConstants.DEFAULT_STREAM_EVENTS_FLUSH_INTERVAL_MS);

    this.callbackExecutorService = Executors.newFixedThreadPool(5,
                                                                new ThreadFactoryBuilder()
                                                                  .setDaemon(true)
                                                                  .setNameFormat("stream-rest-callback-thread")
                                                                  .build()
    );

    this.cachedStreamEvents = new CachedStreamEvents(opex, queueClientFactory, callbackExecutorService,
                                                     maxCachedSizeBytes, maxCachedEvents,
                                                     maxCachedEventsPerStream);
  }

  @Override
  protected void startUp() throws Exception {
    LOG.info("Starting up {}", this.getClass().getSimpleName());
    flushTimer.scheduleAtFixedRate(
      new TimerTask() {
        @Override
        public void run() {
          LOG.debug("Running flush from timer task.");
          cachedStreamEvents.flush(false);
          LOG.debug("Done running flush from timer task.");
        }
      },
      flushIntervalMs, flushIntervalMs
    );
  }

  @Override
  protected void shutDown() throws Exception {
    LOG.info("Shutting down {}", this.getClass().getSimpleName());
    flushTimer.cancel();
    cachedStreamEvents.flush(true);
    callbackExecutorService.shutdown();
  }

  /**
   * Used to enqueue a StreamEvent.
   * @param event StreamEvent to enqueue.
   * @param accountId accountId of the entity making the call.
   * @param callback Callback to be called after enqueuing the event
   */
  public void collect(StreamEvent event, String accountId, FutureCallback<Void> callback) {
    try {
      byte[] bytes = serializer.encodePayload(event);
      if (bytes == null) {
        throw new Exception(String.format("Could not serialize event: %s", event));
      }

      String destination = event.getHeaders().get(Constants.HEADER_DESTINATION_STREAM);
      if (destination == null) {
        LOG.trace("Enqueuing an event that has no destination. Using 'default' instead.");
        destination = "default";
      }

      QueueName queueName = QueueName.fromStream(accountId, destination);
      cachedStreamEvents.put(queueName, new QueueEntry(bytes), callback);
    } catch (Exception e) {
      setCallbackException(callback, e);
    }
  }

  private void setCallbackException(FutureCallback<Void> callback, Exception e) {
    SettableFuture<Void> future = SettableFuture.create();
    Futures.addCallback(future, callback);
    future.setException(e);
  }
}
