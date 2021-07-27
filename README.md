## mod-metrics

[![Build Status](https://travis-ci.org/swisspush/mod-metrics.svg?branch=master)](https://travis-ci.org/swisspush/mod-metrics)
[![codecov](https://codecov.io/gh/swisspush/mod-metrics/branch/master/graph/badge.svg)](https://codecov.io/gh/swisspush/mod-metrics)
[![Maven Central](https://img.shields.io/maven-central/v/org.swisspush/mod-metrics.svg)]()
[![GitHub contributors](https://img.shields.io/github/contributors/swisspush/mod-metrics.svg)](https://github.com/swisspush/mod-metrics/graphs/contributors)

A vert.x mod to try and expose stats over JMX using the [Metrics](https://metrics.dropwizard.io/4.0.0/)
library.

Default config:

    {
      address  : "org.swisspush.metrics"
    }

Deploy with:

    vertx.deployVerticle("org.swisspush.metrics.MetricsModule", deploymentOptions, handler) ;

You should then be able to point jconsole (or jvisualvm with the jmx plugin) at the
machine running this module and see stats appear as they are populated.

The mod accepts the messages below.

### Notes

- if a metric with the specified name does not exist, then a metric of the related
  type is created.
- if you try to call an invalid method on an already existing metric (ie: call
  `set` on a metric already constructed with `inc`) then I suspect things will
  blow up in (not so) interesting ways.

## Gauges (see [here](https://metrics.dropwizard.io/4.0.0/manual/core.html#gauges))

NB: Only accepts Integer values

### setting

    {
        name   : "gauge.name",
        action : "set",
        n      : 128
    }

## Counters (see [here](https://metrics.dropwizard.io/4.0.0/manual/core.html#counters))

### incrementing

    {
        name   : "counter.name",
        action : "inc",
        n      : 1        // Optional, defaults to 1
    }

### decrementing

    {
        name   : "counter.name",
        action : "dec",
        n      : 1        // Optional, defaults to 1
    }

## Meters (see [here](https://metrics.dropwizard.io/4.0.0/manual/core.html#meters))

### mark

    {
        name   : "meter.name",
        action : "mark"
    }

## Histograms (see [here](https://metrics.dropwizard.io/4.0.0/manual/core.html#histograms))

### update

    {
        name   : "histogram.name",
        action : "update",
        n      : 10
    }

## Timers (see [here](https://metrics.dropwizard.io/4.0.0/manual/core.html#timers))

If you start a timer, then the `Context` for that timer is stored in a `Map`. Not
stopping the timer will cause this Context to persist in-perpetuity.

> Timers are also of questionable use as you are also going to be timing the event bus propagation time and any Vert.x internals between your sending the message and it being processed by this module.  However if you take that to be a constant, you could say that they will show you *something*. It just arguable whether that *something* is trustworthy or consistent ;-)

### start

    {
        name   : "timer.name",
        action : "start"
    }

### stop

    {
        name   : "timer.name",
        action : "stop"
    }

## Removal

If you want to remove a metric from the system, just send the message:

    {
        name   : "metric.name",
        action : "remove"
    }

## Reading values over the EventBus

### counters

    {
        action : "counters"
    }

Example response:

    {
        status : "ok"
        countername : {
            count: 10
        },
        countername2 : {
            count: 15
        }
    }

### gauges

    {
        action : "gauges"
    }

Example response:

    {
        status : "ok"
        gaugename : {
            value: 4
        },
        gaugename2 : {
            value: 15
        }
    }

### histograms

    {
        action : "histograms"
    }

Example response:

    {
        status : "ok"
        hist1 : {
            count  : 10,
            min    : 3,
            max    : 100,
            median : 23.5,
            mean   : 32.4,
            stddev : 2.8,
            size   : 23,
            75th   : 34.5,
            95th   : 24.5,
            98th   : 14.5,
            99th   : 11.5,
            999th  : 10.2
        }
    }

### histograms

    {
        action : "meters"
    }

Example response:

    {
        status : "ok"
        meter1 : {
            1m    : 2.4,
            5m    : 3.8,
            15m   : 4.2,
            count : 120,
            mean  : 3.2
        }
    }

### timers

    {
        action : "timers"
    }

Example response:

    {
        status : "ok"
        timer1 : {
            1m     : 2.4,
            5m     : 3.8,
            15m    : 4.2,
            count  : 120,
            mean   : 3.2,
            count  : 10,
            min    : 3,
            max    : 100,
            median : 23.5,
            mean   : 32.4,
            stddev : 2.8,
            size   : 10,
            75th   : 34.5,
            95th   : 24.5,
            98th   : 14.5,
            99th   : 11.5,
            999th  : 10.2
        }
    }

## Dependencies

- Starting from version 2.2.x **Java 11** is required.
