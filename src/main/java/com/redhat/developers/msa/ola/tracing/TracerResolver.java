/*
 * Copyright 2016 Juraci Paixão Kröhling
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.redhat.developers.msa.ola.tracing;

import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.logging.Logger;

import io.opentracing.Tracer;

/**
 * This is a helper class, that serves as an example of what could be done as a microservices library
 * for detecting the appropriate Tracer based on the environment. On this case, we explicitly know about
 * two tracers, BraveTracer and APMTracer.
 *
 * For the Hello World MSA, we might move this to a common module in the future.
 *
 * @author Juraci Paixão Kröhling
 */
@SuppressWarnings("Duplicates")
public class TracerResolver {
    private static final Logger logger = Logger.getLogger(TracerResolver.class.getName());
    private static Class<? extends Tracer> CACHED = null;

    public static Tracer getTracer() {
        if (CACHED != null) {
            try {
                return CACHED.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                logger.warning(String.format("Failed to get instance from the cached class [%s]. Ignoring cache.", CACHED.getName()));
            }
        }

        // first: explicit configuration via -Dtracer.class=fully.classified.class.Name
        String tracerClassName = System.getProperty("tracer.class");
        if (null == tracerClassName || tracerClassName.isEmpty()) {
            // we didn't receive it as a sysprop, let's check the env
            tracerClassName = System.getenv("TRACER_CLASS");
        }

        if (null != tracerClassName && !tracerClassName.isEmpty()) {
            // we got a hit! let's try to load this class:
            Tracer tracer = attemptToLoad(tracerClassName);
            if (null != tracer) {
                return tracer;
            }
        }

        // now, we check if we have a service on the classpath:
        ServiceLoader<Tracer> serviceLoaderTracer = ServiceLoader.load(Tracer.class);
        Iterator<Tracer> iteratorTracer = serviceLoaderTracer.iterator();
        if (iteratorTracer.hasNext()) {
            Tracer tracer = iteratorTracer.next();
            if (null != tracer) {
                CACHED = tracer.getClass();
                return tracer;
            }
        }

        if (null != System.getenv("HAWKULAR_APM_SERVICE_HOST")) {
            Tracer tracer = attemptToLoad("org.hawkular.apm.client.opentracing.APMTracer");
            if (null != tracer) {
                return tracer;
            }
        }

        if (null != System.getenv("HAWKULAR_APM_URI")) {
            Tracer tracer = attemptToLoad("org.hawkular.apm.client.opentracing.APMTracer");
            if (null != tracer) {
                return tracer;
            }
        }

        if (null != System.getenv("ZIPKIN_SERVER_URL")) {
            Tracer tracer = attemptToLoad("io.opentracing.impl.BraveTracer");
            if (null != tracer) {
                return tracer;
            }
        }

        return null;
    }

    private static Tracer attemptToLoad(String tracerClassName) {
        try {
            Class<?> clazz = Class.forName(tracerClassName);
            try {
                Object instanceObject = clazz.newInstance();
                if (instanceObject instanceof Tracer) {
                    CACHED = ((Tracer) instanceObject).getClass();
                    return CACHED.newInstance();
                } else {
                    logger.warning("The specified class [%s] is not a io.opentracing.Tracer . Trying to come up with our next best guess.");
                }
            } catch (InstantiationException | IllegalAccessException e) {
                logger.warning("Failed to instantiate the specified class [%s]. Trying to come up with our next best guess.");
            }
        } catch (ClassNotFoundException e) {
            // nope, but we'll try to come up with our best guesses... perhaps this class isn't there for this env
            // but it's there for another env for this same application? like, test vs. prod?
            logger.warning(String.format("Could not load the specified class [%s] . Trying to come up with our next best guess.", tracerClassName));
        }
        return null;
    }
}
