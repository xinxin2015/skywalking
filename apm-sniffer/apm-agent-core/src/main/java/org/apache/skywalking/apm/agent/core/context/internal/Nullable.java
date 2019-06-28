package org.apache.skywalking.apm.agent.core.context.internal;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


/**
 * Libraries such as Guice and AutoValue will process any annotation named {@code Nullable}. This
 * avoids a dependency on one of the many jsr305 jars, causes problems in OSGi and Java 9 projects
 * (where a project is also using jax-ws).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface Nullable {
}
