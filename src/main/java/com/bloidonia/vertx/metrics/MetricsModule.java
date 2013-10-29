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

import com.codahale.metrics.* ;
import com.codahale.metrics.Timer.Context ;

import java.util.Map ;
import java.util.HashMap ;
import java.util.Map.Entry ;

import org.vertx.java.busmods.BusModBase ;
import org.vertx.java.core.Handler ;
import org.vertx.java.core.eventbus.Message ;
import org.vertx.java.core.json.JsonArray ;
import org.vertx.java.core.json.JsonObject ;

public class MetricsModule extends BusModBase implements Handler<Message<JsonObject>> {

    private MetricRegistry metrics ;
    private String address ;
    private Map<String,Context> timers ;

    public void start() {
        super.start() ;

        address = getOptionalStringConfig( "address", "com.bloidonia.metrics" ) ;
        metrics = new MetricRegistry() ;
        timers = new HashMap<>() ;
        JmxReporter.forRegistry( metrics ).build().start() ;

        eb.registerHandler( address, this ) ;
    }

    public void stop() {
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
                metrics.register( name, new Gauge<Integer>() {
                    @Override
                    public Integer getValue() {
                        return n ;
                    }
                } ) ;

            // increment a counter
            case "inc" :
                metrics.counter( name ).inc( getOptionalInteger( body, "n", 1 ) ) ;
                break ;

            // decrement a counter
            case "dec" :
                metrics.counter( name ).dec( getOptionalInteger( body, "n", 1 ) ) ;
                break ;

            // Mark a meter
            case "mark" :
                metrics.meter( name ).mark() ;
                break ;

            // Update a histogram
            case "update" :
                metrics.histogram( name ).update( body.getInteger( "n" ) ) ;
                break ;

            // Start a timer
            case "start" :
                timers.put( name, metrics.timer( name ).time() ) ;
                break ;

            // Stop a timer
            case "stop" :
                Context c = timers.remove( name ) ;
                if( c != null ) {
                    c.stop() ;
                }
                break ;

            // Remove a metric if it exists
            case "remove" :
                metrics.remove( name ) ;
                break ;

            case "gauges" : {
                JsonObject reply = new JsonObject() ;
                for( Entry<String,Gauge> entry : metrics.getGauges().entrySet() ) {
                    reply.putObject( entry.getKey(),
                                     serialiseGauge( entry.getValue(), new JsonObject() ) ) ;
                }
                sendOK( message, reply ) ;
                break ;
            }

            case "counters" : {
                JsonObject reply = new JsonObject() ;
                for( Entry<String,Counter> entry : metrics.getCounters().entrySet() ) {
                    reply.putObject( entry.getKey(),
                                     serialiseCounting( entry.getValue(),
                                                        new JsonObject() ) ) ;
                }
                sendOK( message, reply ) ;
                break ;
            }

            case "histograms" : {
                JsonObject reply = new JsonObject() ;
                for( Entry<String,Histogram> entry : metrics.getHistograms().entrySet() ) {
                    reply.putObject( entry.getKey(),
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
                    reply.putObject( entry.getKey(),
                                     serialiseMetered( entry.getValue(),
                                                       new JsonObject() ) ) ;
                }
                sendOK( message, reply ) ;
                break ;
            }

            case "timers" : {
                JsonObject reply = new JsonObject() ;
                for( Entry<String,Timer> entry : metrics.getTimers().entrySet() ) {
                    reply.putObject( entry.getKey(),
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

    private JsonObject serialiseGauge( Gauge<Integer> gauge, JsonObject ret ) {
        ret.putNumber( "value", gauge.getValue() ) ;
        return ret ;
    }

    private JsonObject serialiseCounting( Counting count, JsonObject ret ) {
        ret.putNumber( "count", count.getCount() ) ;
        return ret ;
    }

    private JsonObject serialiseSampling( Sampling sample, JsonObject ret ) {
        Snapshot snap = sample.getSnapshot() ;
        ret.putNumber( "min",    snap.getMin() ) ;
        ret.putNumber( "max",    snap.getMax() ) ;
        ret.putNumber( "median", snap.getMedian() ) ;
        ret.putNumber( "mean",   snap.getMean() ) ;
        ret.putNumber( "stddev", snap.getStdDev() ) ;
        ret.putNumber( "size",   snap.size() ) ;
        ret.putNumber( "75th",   snap.get75thPercentile() ) ;
        ret.putNumber( "95th",   snap.get95thPercentile() ) ;
        ret.putNumber( "98th",   snap.get98thPercentile() ) ;
        ret.putNumber( "99th",   snap.get99thPercentile() ) ;
        ret.putNumber( "999th",  snap.get999thPercentile() ) ;
        return ret ;
    }

    private JsonObject serialiseMetered( Metered meter, JsonObject ret ) {
        ret.putNumber( "1m",    meter.getOneMinuteRate() ) ;
        ret.putNumber( "5m",    meter.getFiveMinuteRate() ) ;
        ret.putNumber( "15m",   meter.getFifteenMinuteRate() ) ;
        ret.putNumber( "count", meter.getCount() ) ;
        ret.putNumber( "mean",  meter.getMeanRate() ) ;
        return ret ;
    }
}