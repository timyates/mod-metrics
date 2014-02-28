import org.vertx.groovy.testtools.VertxTests
import static org.vertx.testtools.VertxAssert.*

VertxTests.initialize(this)

def testCounterInc() {
    vertx.eventBus.send( "com.bloidonia.metrics", [ name:'test.counter', action:'inc' ] ) {
        vertx.eventBus.send( "com.bloidonia.metrics", [ action:'counters' ] ) { reply ->
            // Check ok and value is 1
            assertEquals( "ok", reply.body().status )
            assertEquals( 1, reply.body().'test.counter'.count )

            // Reset to 0
            vertx.eventBus.send( "com.bloidonia.metrics", [ name:'test.counter', action:'dec' ] )
            testComplete()
        }
    }
}

def testCounterInc4() {
    vertx.eventBus.send( "com.bloidonia.metrics", [ name:'test.counter', action:'inc', n:4 ] ) {
        vertx.eventBus.send( "com.bloidonia.metrics", [ action:'counters' ] ) { reply ->
            // Check ok and value is 4
            assertEquals( "ok", reply.body().status )
            assertEquals( 4, reply.body().'test.counter'.count )

            // Reset to 0
            vertx.eventBus.send( "com.bloidonia.metrics", [ name:'test.counter', action:'dec', n:4 ] )
            testComplete()
        }
    }
}

def testCounterDec() {
    vertx.eventBus.send( "com.bloidonia.metrics", [ name:'test.counter', action:'dec' ] ) {
        vertx.eventBus.send( "com.bloidonia.metrics", [ action:'counters' ] ) { reply ->
            // Check ok and value is 4
            assertEquals( "ok", reply.body().status )
            assertEquals( -1, reply.body().'test.counter'.count )

            // Reset to 0
            vertx.eventBus.send( "com.bloidonia.metrics", [ name:'test.counter', action:'inc' ] )
            testComplete()
        }
    }
}

def testCounterDec4() {
    vertx.eventBus.send( "com.bloidonia.metrics", [ name:'test.counter', action:'dec', n:4 ] ) {
        vertx.eventBus.send( "com.bloidonia.metrics", [ action:'counters' ] ) { reply ->
            // Check ok and value is 4
            assertEquals( "ok", reply.body().status )
            assertEquals( -4, reply.body().'test.counter'.count )

            // Reset to 0
            vertx.eventBus.send( "com.bloidonia.metrics", [ name:'test.counter', action:'inc', n:4 ] )
            testComplete()
        }
    }
}

def testGauges() {
    vertx.eventBus.send( "com.bloidonia.metrics", [ name:'test.gauge', action:'set', n:1234 ] ) {
        vertx.eventBus.send( "com.bloidonia.metrics", [ action:'gauges' ] ) {
            assertEquals( "ok", it.body().status )
            assertEquals( 1234, it.body().'test.gauge'.value )

            vertx.eventBus.send( "com.bloidonia.metrics", [ name:'test.gauge', action:'set', n:5678 ] ) {
                vertx.eventBus.send( "com.bloidonia.metrics", [ action:'gauges' ] ) {
                    assertEquals( "ok", it.body().status )
                    assertEquals( 5678, it.body().'test.gauge'.value )

                    vertx.eventBus.send( "com.bloidonia.metrics", [ name:'test.gauge', action:'remove' ] )
                    testComplete()
                }
            }
        }
    }
}

container.deployModule( System.getProperty( "vertx.modulename" ) ) { asyncResult ->
    // Deployment is asynchronous and this this handler will be called when it's complete (or failed)
    assertTrue( asyncResult.succeeded )
    assertNotNull( "deploymentID should not be null", asyncResult.result() )

    // If deployed correctly then start the tests!
    VertxTests.startTests(this)
}
