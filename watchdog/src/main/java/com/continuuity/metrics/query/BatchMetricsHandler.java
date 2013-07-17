/*
 * Copyright 2012-2013 Continuuity,Inc. All Rights Reserved.
 */
package com.continuuity.metrics.query;

import com.continuuity.common.http.core.AbstractHttpHandler;
import com.continuuity.common.http.core.HttpResponder;
import com.continuuity.metrics.data.AggregatesScanner;
import com.continuuity.metrics.data.AggregatesTable;
import com.continuuity.metrics.data.MetricsScanQuery;
import com.continuuity.metrics.data.MetricsScanQueryBuilder;
import com.continuuity.metrics.data.MetricsScanner;
import com.continuuity.metrics.data.MetricsTableFactory;
import com.continuuity.metrics.data.TimeSeriesTable;
import com.continuuity.metrics.data.TimeValue;
import com.continuuity.metrics.data.TimeValueAggregator;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.PeekingIterator;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.util.Iterator;
import java.util.List;

/**
 * Class for handling batch requests for time series data.
 */
@Path("/metrics")
public final class BatchMetricsHandler extends AbstractHttpHandler {

  private static final String CONTENT_TYPE_JSON = "application/json";
  private static final Gson GSON = new Gson();

  // Function to map URI into MetricRequest by parsing the URI.
  private static final Function<URI, MetricsRequest> URI_TO_METRIC_REQUEST = new Function<URI, MetricsRequest>() {
    @Override
    public MetricsRequest apply(URI input) {
      return MetricsRequestParser.parse(input);
    }
  };

  // It's a cache from metric table resolution to MetricsTable
  private final LoadingCache<Integer, TimeSeriesTable> metricsTableCache;
  private final AggregatesTable aggregatesTable;

  @Inject
  public BatchMetricsHandler(final MetricsTableFactory metricsTableFactory) {
    this.metricsTableCache = CacheBuilder.newBuilder().build(new CacheLoader<Integer, TimeSeriesTable>() {
      @Override
      public TimeSeriesTable load(Integer key) throws Exception {
        return metricsTableFactory.createTimeSeries(key);
      }
    });
    this.aggregatesTable = metricsTableFactory.createAggregates();
  }

  @POST
  public void handleBatch(HttpRequest request, HttpResponder responder) throws IOException {
    if (!CONTENT_TYPE_JSON.equals(request.getHeader(HttpHeaders.Names.CONTENT_TYPE))) {
      responder.sendError(HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE, "Only " + CONTENT_TYPE_JSON + " is supported.");
      return;
    }

    List<MetricsRequest> metricsRequests;
    try {
      metricsRequests = decodeRequests(request.getContent());
    } catch (JsonSyntaxException e) {
      responder.sendError(HttpResponseStatus.BAD_REQUEST, "Invalid json request: " + e.getMessage());
      return;
    }

    JsonArray output = new JsonArray();
    for (MetricsRequest metricsRequest : metricsRequests) {
      Object resultObj = null;
      if (metricsRequest.getType() == MetricsRequest.Type.TIME_SERIES) {
        TimeSeriesResponse.Builder builder = TimeSeriesResponse.builder(metricsRequest.getStartTime(),
                                                                        metricsRequest.getEndTime());
        // Busyness is a special case that computes from multiple timeseries.
        if ("busyness".equals(metricsRequest.getMetricPrefix())) {
          MetricsScanQuery scanQuery = new MetricsScanQueryBuilder()
            .setContext(metricsRequest.getContextPrefix())
            .setMetric("tuples.read")
            .build(metricsRequest.getStartTime(), metricsRequest.getEndTime());

          PeekingIterator<TimeValue> tuplesReadItor = Iterators.peekingIterator(queryTimeSeries(scanQuery));

          scanQuery = new MetricsScanQueryBuilder()
            .setContext(metricsRequest.getContextPrefix())
            .setMetric("events.processed")
            .build(metricsRequest.getStartTime(), metricsRequest.getEndTime());

          PeekingIterator<TimeValue> eventsProcessedItor = Iterators.peekingIterator(queryTimeSeries(scanQuery));

          for (int i = 0; i < metricsRequest.getCount(); i++) {
            long resultTime = metricsRequest.getStartTime() + i;
            int tupleRead = 0;
            int eventProcessed = 0;
            if (tuplesReadItor.hasNext() && tuplesReadItor.peek().getTime() == resultTime) {
              tupleRead = tuplesReadItor.next().getValue();
            }
            if (eventsProcessedItor.hasNext() && eventsProcessedItor.peek().getTime() == resultTime) {
              eventProcessed = eventsProcessedItor.next().getValue();
            }
            if (eventProcessed != 0) {
              builder.addData(resultTime, (int) ((float) tupleRead / eventProcessed * 100));
            } else {
              builder.addData(resultTime, 0);
            }
          }
        } else {
          MetricsScanQuery scanQuery = new MetricsScanQueryBuilder()
            .setContext(metricsRequest.getContextPrefix())
            .setMetric(metricsRequest.getMetricPrefix())
            .setTag(metricsRequest.getTagPrefix())
            .build(metricsRequest.getStartTime(), metricsRequest.getEndTime());

          PeekingIterator<TimeValue> timeValueItor = Iterators.peekingIterator(queryTimeSeries(scanQuery));

          for (int i = 0; i < metricsRequest.getCount(); i++) {
            long resultTime = metricsRequest.getStartTime() + i;

            if (timeValueItor.hasNext() && timeValueItor.peek().getTime() == resultTime) {
              builder.addData(resultTime, timeValueItor.next().getValue());
              continue;
            }
            builder.addData(resultTime, 0);
          }
        }
        resultObj = builder.build();

      } else if (metricsRequest.getType() == MetricsRequest.Type.AGGREGATE) {
        resultObj = getAggregates(metricsRequest);
      }

      JsonObject json = new JsonObject();
      json.addProperty("path", metricsRequest.getRequestURI().toString());
      json.add("result", GSON.toJsonTree(resultObj));
      json.add("error", JsonNull.INSTANCE);

      output.add(json);
    }

    responder.sendJson(HttpResponseStatus.OK, output);
  }

  private Iterator<TimeValue> queryTimeSeries(MetricsScanQuery scanQuery) {
    List<Iterable<TimeValue>> timeValues = Lists.newArrayList();
    MetricsScanner scanner = metricsTableCache.getUnchecked(1).scan(scanQuery);
    while (scanner.hasNext()) {
      timeValues.add(scanner.next());
    }

    return new TimeValueAggregator(timeValues).iterator();
  }

  /**
   * Decodes the batch request
   *
   * @return a List of String containing all requests from the batch.
   */
  private List<MetricsRequest> decodeRequests(ChannelBuffer content) throws IOException {
    Reader reader = new InputStreamReader(new ChannelBufferInputStream(content), Charsets.UTF_8);
    try {
      List<URI> uris = GSON.fromJson(reader, new TypeToken<List<URI>>() {}.getType());
      System.out.println(uris);
      return ImmutableList.copyOf(Iterables.transform(uris, URI_TO_METRIC_REQUEST));
    } finally {
      reader.close();
    }
  }

  private AggregateResponse getAggregates(MetricsRequest request) {
    AggregatesScanner scanner = aggregatesTable.scan(request.getContextPrefix(), request.getMetricPrefix(),
                                                     request.getRunId(), request.getTagPrefix());
    long value = 0;
    while (scanner.hasNext()) {
      value += scanner.next().getValue();
    }
    return new AggregateResponse(value);
  }
}
