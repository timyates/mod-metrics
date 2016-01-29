/*
 * Copyright 2012-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bloidonia.vertx.metrics ;

import com.codahale.metrics.*;
import com.codahale.metrics.Timer.Context;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MetricsModule extends AbstractVerticle implements Handler<Message<JsonObject>> {

    private MetricRegistry metrics ;
    private String address ;
    private Map<String,Context> timers ;
    private ConcurrentMap<String,Integer> gauges ;
    private JsonObject config;

    private Logger logger = LoggerFactory.getLogger(MetricsModule.class);

    @Override
    public void start() {
        config = config();
        address = getOptionalStringConfig( "address", "com.bloidonia.metrics" ) ;
        metrics = new MetricRegistry() ;
        timers = new HashMap<>() ;
        gauges = new ConcurrentHashMap<>() ;
        JmxReporter.forRegistry( metrics ).build().start() ;

        vertx.eventBus().consumer( address, this ) ;
    }

    private static Integer getOptionalInteger( JsonObject obj, String name, Integer def ) {
        Integer result = obj.getInteger( name ) ;
        return result == null ? def : result ;
    }

    public void handle( final Message<JsonObject> message ) {
        final JsonObject body = message.body() ;
        final String action   = body.getString( "action" ) ;
        final String name     = body.getString( "name" ) ;

        if( action == null ) {
            sendError( message, "action must be specified" ) ;
        }
        switch( action ) {
            // set a gauge
            case "set" :
                final int n = body.getInteger( "n" ) ;
                gauges.put( name, n ) ;
                if( metrics.getMetrics().get( name ) == null ) {
                    metrics.register( name, (Gauge<Integer>) () -> gauges.get( name )) ;
                }
                sendOK( message ) ;
                break ;

            // increment a counter
            case "inc" :
                metrics.counter( name ).inc( getOptionalInteger( body, "n", 1 ) ) ;
                sendOK( message ) ;
                break ;

            // decrement a counter
            case "dec" :
                metrics.counter( name ).dec( getOptionalInteger( body, "n", 1 ) ) ;
                sendOK( message ) ;
                break ;

            // Mark a meter
            case "mark" :
                metrics.meter( name ).mark() ;
                sendOK( message ) ;
                break ;

            // Update a histogram
            case "update" :
                metrics.histogram( name ).update( body.getInteger( "n" ) ) ;
                sendOK( message ) ;
                break ;

            // Start a timer
            case "start" :
                timers.put( name, metrics.timer( name ).time() ) ;
                sendOK( message ) ;
                break ;

            // Stop a timer
            case "stop" :
                Context c = timers.remove( name ) ;
                if( c != null ) {
                    c.stop() ;
                }
                sendOK( message ) ;
                break ;

            // Remove a metric if it exists
            case "remove" :
                metrics.remove( name ) ;
                gauges.remove( name ) ;
                sendOK( message ) ;
                break ;

            case "gauges" : {
                JsonObject reply = new JsonObject() ;
                for( Entry<String,Gauge> entry : metrics.getGauges().entrySet() ) {
                    reply.put( entry.getKey(),
                                     serialiseGauge( entry.getValue(), new JsonObject() ) ) ;
                }
                sendOK( message, reply ) ;
                break ;
            }

            case "counters" : {
                JsonObject reply = new JsonObject() ;
                for( Entry<String,Counter> entry : metrics.getCounters().entrySet() ) {
                    reply.put( entry.getKey(),
                                     serialiseCounting( entry.getValue(),
                                                        new JsonObject() ) ) ;
                }
                sendOK( message, reply ) ;
                break ;
            }

            case "histograms" : {
                JsonObject reply = new JsonObject() ;
                for( Entry<String,Histogram> entry : metrics.getHistograms().entrySet() ) {
                    reply.put( entry.getKey(),
                                     serialiseSampling( entry.getValue(), 
                                                        serialiseCounting( entry.getValue(),
                                                                           new JsonObject() ) ) ) ;
                }
                sendOK( message, reply ) ;
                break ;
            }

            case "meters" : {
                JsonObject reply = new JsonObject() ;
                for( Entry<String,Meter> entry : metrics.getMeters().entrySet() ) {
                    reply.put( entry.getKey(),
                                     serialiseMetered( entry.getValue(),
                                                       new JsonObject() ) ) ;
                }
                sendOK( message, reply ) ;
                break ;
            }

            case "timers" : {
                JsonObject reply = new JsonObject() ;
                for( Entry<String,Timer> entry : metrics.getTimers().entrySet() ) {
                    reply.put( entry.getKey(),
                                     serialiseSampling( entry.getValue(),
                                                        serialiseMetered( entry.getValue(),
                                                                          new JsonObject() ) ) ) ;
                }
                sendOK( message, reply ) ;
                break ;
            }

            default:
                sendError( message, "Invalid action : " + action ) ;
        }
    }

    private JsonObject serialiseGauge( Gauge gauge, JsonObject ret ) {
        ret.put( "value", (Integer)gauge.getValue() ) ;
        return ret ;
    }

    private JsonObject serialiseCounting( Counting count, JsonObject ret ) {
        ret.put( "count", count.getCount() ) ;
        return ret ;
    }

    private JsonObject serialiseSampling( Sampling sample, JsonObject ret ) {
        Snapshot snap = sample.getSnapshot() ;
        ret.put( "min",    snap.getMin() ) ;
        ret.put( "max",    snap.getMax() ) ;
        ret.put( "median", snap.getMedian() ) ;
        ret.put( "mean",   snap.getMean() ) ;
        ret.put( "stddev", snap.getStdDev() ) ;
        ret.put( "size",   snap.size() ) ;
        ret.put( "75th",   snap.get75thPercentile() ) ;
        ret.put( "95th",   snap.get95thPercentile() ) ;
        ret.put( "98th",   snap.get98thPercentile() ) ;
        ret.put( "99th",   snap.get99thPercentile() ) ;
        ret.put( "999th",  snap.get999thPercentile() ) ;
        return ret ;
    }

    private JsonObject serialiseMetered( Metered meter, JsonObject ret ) {
        ret.put( "1m",    meter.getOneMinuteRate() ) ;
        ret.put( "5m",    meter.getFiveMinuteRate() ) ;
        ret.put( "15m",   meter.getFifteenMinuteRate() ) ;
        ret.put( "count", meter.getCount() ) ;
        ret.put( "mean",  meter.getMeanRate() ) ;
        return ret ;
    }

    private void sendError(Message<JsonObject> message, String error) {
        sendError(message, error, null);
    }

    private void sendError(Message<JsonObject> message, String error, Exception e) {
        logger.error(error, e);
        JsonObject json = new JsonObject().put("status", "error").put("message", error);
        message.reply(json);
    }

    private void sendOK(Message<JsonObject> message) {
        sendOK(message, null);
    }

    private void sendOK(Message<JsonObject> message, JsonObject json) {
        sendStatus("ok", message, json);
    }

    private void sendStatus(String status, Message<JsonObject> message, JsonObject json) {
        if (json == null) {
            json = new JsonObject();
        }
        json.put("status", status);
        message.reply(json);
    }

    private String getOptionalStringConfig(String fieldName, String defaultValue) {
        String s = config.getString(fieldName);
        return s == null ? defaultValue : s;
    }
}