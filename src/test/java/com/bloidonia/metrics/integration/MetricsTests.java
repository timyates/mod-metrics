/*
 * ------------------------------------------------------------------------------------------------
 * Copyright 2014 by Swiss Post, Information Technology Services
 * ------------------------------------------------------------------------------------------------
 * $Id$
 * ------------------------------------------------------------------------------------------------
 */

package com.bloidonia.metrics.integration;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Class MetricsTests.
 *
 * @author webermarca
 * @version $$Revision$$
 * @see <script>links('$$HeadURL$$');</script>
 */
@RunWith(VertxUnitRunner.class)
public class MetricsTests {

    Vertx vertx;
    Logger log = LoggerFactory.getLogger(MetricsTests.class);
    String address = "com.bloidonia.metrics";

    private final String COUNTER = "test.counter";
    private final String GAUGE = "test.gauge";
    private final String OK = "ok";

    @Before
    public void setUp(TestContext context) {
        vertx = Vertx.vertx();
        vertx.deployVerticle("com.bloidonia.vertx.metrics.MetricsModule", context.asyncAssertSuccess());
    }

    @Test
    public void testCounterInc(TestContext context){
        Async async = context.async();
        eventBusSend(incOperation(COUNTER), event -> {
            eventBusSend(countersOperation(), event1 -> {
                context.assertEquals(OK, extractStatus(event1));
                context.assertEquals(1, event1.result().body().getJsonObject(COUNTER).getInteger("count"));

                // Reset to 0
                eventBusSend(decOperation(COUNTER), event2 -> {
                    async.complete();
                });
            });
        });
    }

    @Test
    public void testCounterInc4(TestContext context){
        Async async = context.async();
        eventBusSend(incOperation(COUNTER, 4), event -> {
           eventBusSend(countersOperation(), reply -> {
               context.assertEquals(OK, extractStatus(reply));
               context.assertEquals(4, reply.result().body().getJsonObject(COUNTER).getInteger("count"));

               // Reset to 0
               eventBusSend(decOperation(COUNTER, 4), event2 -> {
                   async.complete();
               });
           });
        });
    }

    @Test
    public void testCounterDec(TestContext context) {
        Async async = context.async();
        eventBusSend(decOperation(COUNTER), event -> {
            eventBusSend(countersOperation(), reply -> {
                context.assertEquals(OK, extractStatus(reply));
                context.assertEquals(-1, reply.result().body().getJsonObject(COUNTER).getInteger("count"));

                // Reset to 0
                eventBusSend(incOperation(COUNTER), event1 -> {
                    async.complete();
                });
            });
        });
    }

    @Test
    public void testCounterDec4(TestContext context) {
        Async async = context.async();
        eventBusSend(decOperation(COUNTER, 4), event -> {
            eventBusSend(countersOperation(), reply -> {
                context.assertEquals(OK, extractStatus(reply));
                context.assertEquals(-4, reply.result().body().getJsonObject(COUNTER).getInteger("count"));

                // Reset to 0
                eventBusSend(incOperation(COUNTER, 4), event1 -> {
                    async.complete();
                });
            });
        });
    }

    @Test
    public void testGauges(TestContext context) {
        Async async = context.async();
        eventBusSend(setOperation(GAUGE, 1234), event -> {
            eventBusSend(gaugesOperation(), reply -> {
                context.assertEquals(OK, extractStatus(reply));
                context.assertEquals(1234, reply.result().body().getJsonObject(GAUGE).getInteger("value"));

                eventBusSend(setOperation(GAUGE, 5678), event1 -> {
                    eventBusSend(gaugesOperation(), reply2 -> {
                        context.assertEquals(OK, extractStatus(reply2));
                        context.assertEquals(5678, reply2.result().body().getJsonObject(GAUGE).getInteger("value"));

                        eventBusSend(removeOperation(GAUGE), event2 -> {
                            async.complete();
                        });
                    });
                });
            });
        });
    }

    private String extractStatus(AsyncResult<Message<JsonObject>> reply){
        return reply.result().body().getString("status");
    }

    private JsonObject countersOperation(){
        return buildOperation("counters");
    }

    private JsonObject gaugesOperation(){
        return buildOperation("gauges");
    }

    private JsonObject incOperation(String name){
        return buildOperation(name, "inc");
    }

    private JsonObject incOperation(String name, int n){
        JsonObject op = incOperation(name);
        op.put("n", n);
        return op;
    }

    private JsonObject removeOperation(String name){
        return buildOperation(name, "remove");
    }

    private JsonObject setOperation(String name){
        return buildOperation(name, "set");
    }

    private JsonObject setOperation(String name, int n){
        JsonObject op = setOperation(name);
        op.put("n", n);
        return op;
    }

    private JsonObject decOperation(String name){
        return buildOperation(name, "dec");
    }

    private JsonObject decOperation(String name, int n){
        JsonObject op = decOperation(name);
        op.put("n", n);
        return op;
    }

    private JsonObject buildOperation(String name, String action){
        JsonObject op = buildOperation(action);
        op.put("name", name);
        return op;
    }

    private JsonObject buildOperation(String action){
        JsonObject op = new JsonObject();
        op.put("action", action);
        return op;
    }

    private void eventBusSend(JsonObject operation, Handler<AsyncResult<Message<JsonObject>>> handler){
        vertx.eventBus().send(address, operation, handler);
    }
}
