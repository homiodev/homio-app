package org.homio.app.config.cacheControl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Provides an HTTP 1.1 cache control header annotation for Spring MVC controller methods.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheControl {

  /**
   * The <code>cache-control</code> policies to apply to the response.
   *
   * @see CachePolicy
   */
  CachePolicy[] policy() default {CachePolicy.NO_CACHE};

  /**
   * The maximum amount of time, in seconds, that this content will be considered fresh.
   */
  int maxAge() default 0;

  /**
   * The maximum amount of time, in seconds, that this content will be considered fresh only for shared caches (e.g., proxy) caches.
   */
  int sharedMaxAge() default -1;
}
