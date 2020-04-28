package org.touchhome.bundle.cloud.impl;

import lombok.Getter;
import org.springframework.http.HttpMethod;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.BufferedReader;
import java.security.Principal;
import java.util.*;

import static org.springframework.http.HttpHeaders.CONTENT_LENGTH;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

public class HttpServletRequestAdapter implements HttpServletRequest {

    private final Map<String, Object> header = new HashMap<>();
    private final SocketRestRequestModel socketRestRequestModel;
    private final Map<String, Object> attributes = new HashMap<>();
    @Getter
    private ServletInputStream inputStream;
    private Map<String, String[]> parameters;
    private String _characterEncoding = "UTF-8";

    HttpServletRequestAdapter(SocketRestRequestModel socketRestRequestModel) {
        this.socketRestRequestModel = socketRestRequestModel;
        this.parameters = socketRestRequestModel.getParameters();
        if (this.parameters == null) {
            this.parameters = new HashMap<>();
        }
        if (this.socketRestRequestModel.getHttpMethod() != HttpMethod.GET) {
            this.header.put(CONTENT_LENGTH, this.socketRestRequestModel.getContentLength());
            this.header.put(CONTENT_TYPE, this.socketRestRequestModel.getContentType().rawValue);
        }
        if (this.socketRestRequestModel.getHttpMethod() == HttpMethod.POST) {
            this.inputStream = new ServletInputStream() {
                private byte[] content = socketRestRequestModel.getRequest();
                private int alreadyRead = 0;

                @Override
                public int read(byte[] b, int off, int len) {
                    int index = off;
                    int started = this.alreadyRead;
                    for (int i = alreadyRead; i < Math.min(len, content.length); i++) {
                        b[index++] = content[i];
                    }
                    this.alreadyRead += index - off;
                    return this.alreadyRead - started - 1;
                }

                @Override
                public int read() {
                    return alreadyRead == content.length - 1 ? -1 : content[alreadyRead++];
                }

                @Override
                public boolean isFinished() {
                    return alreadyRead == content.length - 1;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {

                }
            };
        }
    }

    @Override
    public String getAuthType() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Cookie[] getCookies() {
        return new Cookie[0];
    }

    @Override
    public long getDateHeader(String s) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String getHeader(String key) {
        return (String) header.get(key);
    }

    @Override
    public Enumeration<String> getHeaders(String key) {
        Object value = this.header.get(key);
        if (value != null) {
            return Collections.enumeration(Collections.singleton(value.toString()));
        }
        return Collections.enumeration(Collections.emptyList());
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return Collections.enumeration(this.header.keySet());
    }

    @Override
    public int getIntHeader(String key) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String getMethod() {
        return socketRestRequestModel.getHttpMethod().name();
    }

    @Override
    public String getPathInfo() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String getPathTranslated() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String getContextPath() {
        return "";
    }

    @Override
    public String getQueryString() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String getRemoteUser() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isUserInRole(String s) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Principal getUserPrincipal() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String getRequestedSessionId() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String getRequestURI() {
        return this.socketRestRequestModel.getPath();
    }

    @Override
    public StringBuffer getRequestURL() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String getServletPath() {
        return this.socketRestRequestModel.getPath();
    }

    @Override
    public HttpSession getSession(boolean b) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public HttpSession getSession() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String changeSessionId() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isRequestedSessionIdFromUrl() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean authenticate(HttpServletResponse httpServletResponse) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void login(String s, String s1) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void logout() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Collection<Part> getParts() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Part getPart(String s) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> aClass) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Object getAttribute(String key) {
        return this.attributes.get(key);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(this.attributes.keySet());
    }

    @Override
    public String getCharacterEncoding() {
        return _characterEncoding;
    }

    @Override
    public void setCharacterEncoding(String key) {
        this._characterEncoding = key;
    }

    @Override
    public int getContentLength() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public long getContentLengthLong() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String getContentType() {
        return (String) this.header.get(CONTENT_TYPE);
    }

    @Override
    public String getParameter(String s) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Enumeration<String> getParameterNames() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String[] getParameterValues(String s) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String getProtocol() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String getScheme() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String getServerName() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public int getServerPort() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public BufferedReader getReader() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String getRemoteAddr() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String getRemoteHost() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void setAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }

    @Override
    public void removeAttribute(String key) {
        this.attributes.remove(key);
    }

    @Override
    public Locale getLocale() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Enumeration<Locale> getLocales() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isSecure() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String s) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String getRealPath(String s) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public int getRemotePort() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String getLocalName() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String getLocalAddr() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public int getLocalPort() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public ServletContext getServletContext() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public AsyncContext startAsync() throws IllegalStateException {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isAsyncStarted() {
        return false;
    }

    @Override
    public boolean isAsyncSupported() {
        return false;
    }

    @Override
    public AsyncContext getAsyncContext() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public DispatcherType getDispatcherType() {
        return DispatcherType.REQUEST;
    }
}
