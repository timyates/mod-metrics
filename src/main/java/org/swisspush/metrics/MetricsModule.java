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

package org.swisspush.metrics;

import com.codahale.metrics.*;
import com.codahale.metrics.Timer.Context;
import com.codahale.metrics.jmx.JmxReporter;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
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
    public void start(Promise<Void> startPromise) {
        logger.info("Starting MetricsModule");
        config = config();
        address = getOptionalStringConfig( "address", "org.swisspush.metrics" ) ;
        metrics = new MetricRegistry() ;
        timers = new HashMap<>() ;
        gauges = new ConcurrentHashMap<>() ;
        JmxReporter.forRegistry( metrics ).build().start() ;

        logger.info("Register consumer for event bus address '"+address+"'");
        vertx.eventBus().consumer( address, this ) ;

        startPromise.complete();
    }

    private static Integer getOptionalInteger( JsonObject obj, String name, Integer def ) {
        Integer result = obj.getInteger( name ) ;
        return result == null ? def : result ;
    }

    public void handle( final Message<JsonObject> message ) {
        if(message.body() == null){
            sendError( message, "message body must be specified" ) ;
            return;
        }

        final JsonObject body = message.body() ;
        final String action   = body.getString( "action" ) ;
        final String name     = body.getString( "name" ) ;

        logger.debug("Handling message with action '"+action+"' and name '"+name+"'");

        if( action == null ) {
            sendError( message, "action must be specified" ) ;
            return;
        }

        switch( action ) {
            // set a gauge
            case "set" :
                setGauge(name, body, message);
                break ;

            // increment a counter
            case "inc" :
                incrementCounter(name, body, message);
                break ;

            // decrement a counter
            case "dec" :
                decrementCounter(name, body, message);
                break ;

            // Mark a meter
            case "mark" :
                markMeter(name, message);
                break ;

            // Update a histogram
            case "update" :
                updateHistogram(name, body, message);
                break ;

            // Start a timer
            case "start" :
                startTimer(name, message);
                break ;

            // Stop a timer
            case "stop" :
                stopTimer(name, message);
                break ;

            // Remove a metric if it exists
            case "remove" :
                removeMetric(name, message);
                break ;

            case "gauges" :
                collectGauges(message);
                break ;

            case "counters" :
                collectCounters(message);
                break ;

            case "histograms" :
                collectHistograms(message);
                break ;

            case "meters" :
                collectMeters(message);
                break ;

            case "timers" :
                collectTimers(message);
                break ;

            default:
                sendError( message, "Invalid action : " + action ) ;
        }
    }

    private void setGauge(String name, JsonObject body, Message<JsonObject> message){
        Integer n = body.getInteger( "n" ) ;
        logger.debug("setting gauge with name '"+name+"' and value " + n);
        gauges.put( name, n ) ;
        if( metrics.getMetrics().get( name ) == null ) {
            metrics.register( name, (Gauge<Integer>) () -> gauges.get( name )) ;
        }
        sendOK( message ) ;
    }

    private void incrementCounter(String name, JsonObject body, Message<JsonObject> message){
        Integer value = getOptionalInteger( body, "n", 1 );
        logger.debug("incrementing counter with name '"+name+"' by " + value);
        metrics.counter( name ).inc( value ) ;
        sendOK( message ) ;
    }

    private void decrementCounter(String name, JsonObject body, Message<JsonObject> message){
        Integer value = getOptionalInteger( body, "n", 1 );
        logger.debug("decrementing counter with name '"+name+"' by " + value);
        metrics.counter( name ).dec( value ); ;
        sendOK( message ) ;
    }

    private void markMeter(String name, Message<JsonObject> message){
        metrics.meter( name ).mark() ;
        logger.debug("marking meter with name '"+name+"'");
        sendOK( message ) ;
    }

    private void updateHistogram(String name, JsonObject body, Message<JsonObject> message){
        Integer value = body.getInteger("n");
        logger.debug("updating histogram with name '"+name+"' and value " + value);
        metrics.histogram( name ).update( value ) ;
        sendOK( message ) ;
    }

    private void startTimer(String name, Message<JsonObject> message){
        logger.debug("starting timer with name '"+name+"'");
        timers.put( name, metrics.timer( name ).time() ) ;
        sendOK( message ) ;
    }

    private void stopTimer(String name, Message<JsonObject> message){
        logger.debug("stopping timer with name '"+name+"'");
        Context c = timers.remove( name ) ;
        if( c != null ) {
            c.stop() ;
        }
        sendOK( message ) ;
    }

    private void removeMetric(String name, Message<JsonObject> message){
        logger.debug("removing metric with name '"+name+"'");
        metrics.remove( name ) ;
        gauges.remove( name ) ;
        sendOK( message ) ;
    }

    private void collectGauges(Message<JsonObject> message){
        JsonObject reply = new JsonObject() ;
        for( Entry<String,Gauge> entry : metrics.getGauges().entrySet() ) {
            reply.put( entry.getKey(),
                    serialiseGauge( entry.getValue(), new JsonObject() ) ) ;
        }
        logger.debug("getting values for gauges. reply with " + reply.encode());
        sendOK( message, reply ) ;
    }

    private void collectCounters(Message<JsonObject> message){
        JsonObject reply = new JsonObject() ;
        for( Entry<String,Counter> entry : metrics.getCounters().entrySet() ) {
            reply.put( entry.getKey(),
                    serialiseCounting( entry.getValue(),
                            new JsonObject() ) ) ;
        }
        logger.debug("getting values for counters. reply with " + reply.encode());
        sendOK( message, reply ) ;
    }

    private void collectHistograms(Message<JsonObject> message){
        JsonObject reply = new JsonObject() ;
        for( Entry<String,Histogram> entry : metrics.getHistograms().entrySet() ) {
            reply.put( entry.getKey(),
                    serialiseSampling( entry.getValue(),
                            serialiseCounting( entry.getValue(),
                                    new JsonObject() ) ) ) ;
        }
        logger.debug("getting values for histograms. reply with " + reply.encode());
        sendOK( message, reply ) ;
    }

    private void collectMeters(Message<JsonObject> message){
        JsonObject reply = new JsonObject() ;
        for( Entry<String,Meter> entry : metrics.getMeters().entrySet() ) {
            reply.put( entry.getKey(),
                    serialiseMetered( entry.getValue(),
                            new JsonObject() ) ) ;
        }
        logger.debug("getting values for meters. reply with " + reply.encode());
        sendOK( message, reply ) ;
    }

    private void collectTimers(Message<JsonObject> message){
        JsonObject reply = new JsonObject() ;
        for( Entry<String,Timer> entry : metrics.getTimers().entrySet() ) {
            reply.put( entry.getKey(),
                    serialiseSampling( entry.getValue(),
                            serialiseMetered( entry.getValue(),
                                    new JsonObject() ) ) ) ;
        }
        logger.debug("getting values for timers. reply with " + reply.encode());
        sendOK( message, reply ) ;
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
        if(message.replyAddress() != null) {
            logger.debug("replying message with status " + status);
        }
        message.reply(json);
    }

    private String getOptionalStringConfig(String fieldName, String defaultValue) {
        String s = config.getString(fieldName);
        return s == null ? defaultValue : s;
    }
}