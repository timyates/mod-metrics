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
 * This represents TODO.
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

    @Before
    public void setUp(TestContext context) {
        vertx = Vertx.vertx();
        vertx.deployVerticle("com.bloidonia.vertx.metrics.MetricsModule", context.asyncAssertSuccess());
    }

    @Test
    public void testCounterInc(TestContext context){
        Async async = context.async();
        eventBusSend(buildOperation("test.counter", "inc"), event -> {
            eventBusSend(buildOperation("counters"), event1 -> {
                context.assertEquals("ok", event1.result().body().getString("status"));
                context.assertEquals(1, event1.result().body().getJsonObject("test.counter").getInteger("count"));
                eventBusSend(buildOperation("test.counter", "dec"), event2 -> {
                    async.complete();
                });
            });
        });
    }

    private JsonObject buildOperation(String name, String action){
        JsonObject op = new JsonObject();
        op.put("name", name);
        op.put("action", action);
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
