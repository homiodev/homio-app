package org.homio.app.config.cacheControl;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.http.HttpHeaders.EXPIRES;

/**
 * Provides a cache control handler interceptor to assign cache-control headers to HTTP responses.
 */
public class CacheControlHandlerInterceptor implements HandlerInterceptor {

  private boolean useExpiresHeader = true;

  /**
   * Creates a new cache control handler interceptor.
   */
  public CacheControlHandlerInterceptor() {
    super();
  }

  @Override
  public final boolean preHandle(
    final HttpServletRequest request,
    final HttpServletResponse response,
    final Object handler) throws Exception {

    this.assignCacheControlHeader(request, response, handler);

    return true;
  }

  /**
   * True to set an expires header when a {@link CacheControl} annotation is present on a handler; false otherwise.   Defaults to true.
   *
   * @param useExpiresHeader <code>true</code> to set an expires header when a
   *                         <code>CacheControl</code> annotation is present on a handler; <code>false</code> otherwise
   */
  public final void setUseExpiresHeader(final boolean useExpiresHeader) {
    this.useExpiresHeader = useExpiresHeader;
  }

  /**
   * Assigns a <code>CacheControl</code> header to the given <code>response</code>.
   *
   * @param request  the <code>HttpServletRequest</code>
   * @param response the <code>HttpServletResponse</code>
   * @param handler  the handler for the given <code>request</code>
   */
  protected final void assignCacheControlHeader(
    final HttpServletRequest request,
    final HttpServletResponse response,
    final Object handler) {

    final CacheControl cacheControl = this.getCacheControl(request, response, handler);
    final String cacheControlHeader = this.createCacheControlHeader(cacheControl);

    if (cacheControlHeader != null) {
      response.setHeader(CACHE_CONTROL, cacheControlHeader);
      if (useExpiresHeader) {
        response.setDateHeader(EXPIRES, createExpiresHeader(cacheControl));
      }
    }
  }

  /**
   * Returns cache control header value from the given {@link CacheControl} annotation.
   *
   * @param cacheControl the <code>CacheControl</code> annotation from which to create the returned cache control header value
   * @return the cache control header value
   */
  protected final String createCacheControlHeader(final CacheControl cacheControl) {

    final StringBuilder builder = new StringBuilder();

    if (cacheControl == null) {
      return null;
    }

    final CachePolicy[] policies = cacheControl.policy();

    if (cacheControl.maxAge() >= 0) {
      builder.append("max-age=").append(cacheControl.maxAge());
    }

    if (cacheControl.sharedMaxAge() >= 0) {
      if (builder.length() > 0) {
        builder.append(", ");
      }
      builder.append("s-maxage=").append(cacheControl.sharedMaxAge());
    }

    if (policies != null) {
      for (final CachePolicy policy : policies) {
        if (builder.length() > 0) {
          builder.append(", ");
        }
        builder.append(policy.policy());
      }
    }

    return (builder.length() > 0 ? builder.toString() : null);
  }

  /**
   * Returns an expires header value generated from the given {@link CacheControl} annotation.
   *
   * @param cacheControl the <code>CacheControl</code> annotation from which to create the returned expires header value
   * @return the expires header value
   */
  protected final long createExpiresHeader(final CacheControl cacheControl) {

    final Calendar expires = new GregorianCalendar(TimeZone.getTimeZone("GMT"));

    if (cacheControl.maxAge() >= 0) {
      expires.add(Calendar.SECOND, cacheControl.maxAge());
    }

    return expires.getTime().getTime();
  }

  /**
   * Returns the {@link CacheControl} annotation specified for the given request, response and handler.
   *
   * @param request  the current <code>HttpServletRequest</code>
   * @param response the current <code>HttpServletResponse</code>
   * @param handler  the current request handler
   * @return the <code>CacheControl</code> annotation specified by the given <code>handler</code> if present; <code>null</code> otherwise
   */
  protected final CacheControl getCacheControl(
    final HttpServletRequest request,
    final HttpServletResponse response,
    final Object handler) {

    if (handler == null || !(handler instanceof HandlerMethod handlerMethod)) {
      return null;
    }

    CacheControl cacheControl = handlerMethod.getMethodAnnotation(CacheControl.class);

    if (cacheControl == null) {
      return handlerMethod.getBeanType().getAnnotation(CacheControl.class);
    }

    return cacheControl;
  }
}
