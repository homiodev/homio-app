package org.touchhome.bundle.cloud.netty.impl;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.util.ObjectUtils;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.HandlerAdapter;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Log4j2
public class DispatcherServletService {

    private List<HandlerMapping> handlerMappings;
    private List<HandlerAdapter> handlerAdapters;

    public DispatcherServletService(ApplicationContext context) {
        super();
        initHandlerMappings(context);
        initHandlerAdapters(context);
    }

    SocketRestResponseModel doService(SocketRestRequestModel socketRestRequestModel) {
        HttpServletRequestAdapter request = new HttpServletRequestAdapter(socketRestRequestModel);
        HttpServletResponseAdapter response = new HttpServletResponseAdapter();

        dispatch(request, response);

        return SocketRestResponseModel.ofServletResponse(socketRestRequestModel.getRequestId(), response);
    }

    private void dispatch(HttpServletRequestAdapter request, HttpServletResponse response) {
        HandlerExecutionChain mappedHandler;
        try {
            // Determine handler for the current request.
            mappedHandler = getHandler(request);
            if (mappedHandler == null) {
                throw new RuntimeException("No handler found");
            }

            // Determine handler adapter for the current request.
            HandlerAdapter ha = getHandlerAdapter(mappedHandler.getHandler());

            // Process last-modified header, if supported by the handler.
            String method = request.getMethod();
            boolean isGet = "GET".equals(method);
            if (isGet || "HEAD".equals(method)) {
                long lastModified = ha.getLastModified(request, mappedHandler.getHandler());
                if (new ServletWebRequest(request, response).checkNotModified(lastModified) && isGet) {
                    return;
                }
            }

            if (!applyPreHandle(request, response, mappedHandler)) {
                return;
            }

            // Actually invoke the handler.
            ha.handle(request, response, mappedHandler.getHandler());

        } catch (Exception ex) {
            //dispatchException = ex;
            log.error("error: " + ex.getMessage(), ex);
        }
    }

    private boolean applyPreHandle(HttpServletRequest request, HttpServletResponse response, HandlerExecutionChain mappedHandler) throws Exception {
        HandlerInterceptor[] interceptors = mappedHandler.getInterceptors();
        int interceptorIndex = 0;
        if (!ObjectUtils.isEmpty(interceptors)) {
            for (int i = 0; i < interceptors.length; i++) {
                HandlerInterceptor interceptor = interceptors[i];
                if (!interceptor.preHandle(request, response, mappedHandler.getHandler())) {
                    triggerAfterCompletion(request, response, null, mappedHandler, interceptorIndex);
                    return false;
                }
                interceptorIndex = i;
            }
        }
        return true;
    }

    private void triggerAfterCompletion(HttpServletRequest request, HttpServletResponse response, Exception ex, HandlerExecutionChain mappedHandler, int interceptorIndex) {
        HandlerInterceptor[] interceptors = mappedHandler.getInterceptors();
        if (!ObjectUtils.isEmpty(interceptors)) {
            for (int i = interceptorIndex; i >= 0; i--) {
                HandlerInterceptor interceptor = interceptors[i];
                try {
                    interceptor.afterCompletion(request, response, mappedHandler.getHandler(), ex);
                } catch (Throwable ex2) {
                    log.error("HandlerInterceptor.afterCompletion threw exception", ex2);
                }
            }
        }
    }

    private HandlerAdapter getHandlerAdapter(Object handler) throws ServletException {
        if (this.handlerAdapters != null) {
            for (HandlerAdapter adapter : this.handlerAdapters) {
                if (adapter.supports(handler)) {
                    return adapter;
                }
            }
        }
        throw new ServletException("No adapter for handler [" + handler +
                "]: The DispatcherServlet configuration needs to include a HandlerAdapter that supports this handler");
    }

    private HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception {
        if (this.handlerMappings != null) {
            for (HandlerMapping mapping : this.handlerMappings) {
                HandlerExecutionChain handler = mapping.getHandler(request);
                if (handler != null) {
                    return handler;
                }
            }
        }
        return null;
    }

    private void initHandlerMappings(ApplicationContext context) {
        // Find all HandlerMappings in the ApplicationContext, including ancestor contexts.
        Map<String, HandlerMapping> matchingBeans =
                BeanFactoryUtils.beansOfTypeIncludingAncestors(context, HandlerMapping.class, true, false);
        if (!matchingBeans.isEmpty()) {
            this.handlerMappings = new ArrayList<>(matchingBeans.values());
            // We keep HandlerMappings in sorted order.
            AnnotationAwareOrderComparator.sort(this.handlerMappings);
        }
    }

    private void initHandlerAdapters(ApplicationContext context) {
        // Find all HandlerAdapters in the ApplicationContext, including ancestor contexts.
        Map<String, HandlerAdapter> matchingBeans =
                BeanFactoryUtils.beansOfTypeIncludingAncestors(context, HandlerAdapter.class, true, false);
        if (!matchingBeans.isEmpty()) {
            this.handlerAdapters = new ArrayList<>(matchingBeans.values());
            // We keep HandlerAdapters in sorted order.
            AnnotationAwareOrderComparator.sort(this.handlerAdapters);
        }
    }
}
