## mod-metrics

A vert.x mod to try and expose stats via metrix and JMX.

Default config:

    {
      address  : "com.bloidonia.metrics"
    }

Deploy with:

    vertx.deployModule( 'com.bloidonia~mod-metrics~0.0.1-SNAPSHOT', config, 1, function() {} ) ;

Then accepts the following messages (if a component with the specified name does
not exist, then a component of the related type is created):

## Counters (see [here](http://metrics.codahale.com/getting-started/#counters))

### incrementing

    {
        name   : "counter.name",
        action : "inc",
        n      : 1        // Optional, defaults to 1
    }

### decrementing

    {
        name   : "counter.name",
        action: "dec",
        n     : 1        // Optional, defaults to 1
    }

## Meters (see [here](http://metrics.codahale.com/getting-started/#meters))

### mark

    {
        name   : "meter.name",
        action: "mark"
    }

## Histograms (see [here](http://metrics.codahale.com/getting-started/#histograms))

### update

    {
        name   : "histogram.name",
        action: "update"
    }

## Timers (see [here](http://metrics.codahale.com/getting-started/#timers))

If you start a timer, then the `Context` for that timer is stored in a `Map`. Not
stopping the timer will cause this Context to persist in-perpetuity.

### start

    {
        name   : "timer.name",
        action: "start"
    }

### stop

    {
        name   : "timer.name",
        action: "stop"
    }