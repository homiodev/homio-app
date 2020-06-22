package org.touchhome.bundle.cloud.netty.impl;

import lombok.Getter;
import lombok.Setter;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Getter
@Setter
public class HttpServletResponseAdapter implements HttpServletResponse {


    private String contentType;
    private int status;
    private Map<String, String> headers = new HashMap<>();
    private ServletOutputStreamAdapter outputStream = new ServletOutputStreamAdapter();

    @Override
    public void addCookie(Cookie cookie) {

    }

    @Override
    public boolean containsHeader(String name) {
        return false;
    }

    @Override
    public String encodeURL(String url) {
        return null;
    }

    @Override
    public String encodeRedirectURL(String url) {
        return null;
    }

    @Override
    public String encodeUrl(String url) {
        return null;
    }

    @Override
    public String encodeRedirectUrl(String url) {
        return null;
    }

    @Override
    public void sendError(int sc, String msg) {

    }

    @Override
    public void sendError(int sc) {
        this.status = sc;
    }

    @Override
    public void sendRedirect(String location) {

    }

    @Override
    public void setDateHeader(String name, long date) {

    }

    @Override
    public void addDateHeader(String name, long date) {

    }

    @Override
    public void setHeader(String name, String value) {
        headers.putIfAbsent(name, value);

    }

    @Override
    public void addHeader(String name, String value) {
        headers.putIfAbsent(name, value);
    }

    @Override
    public void setIntHeader(String name, int value) {
        headers.putIfAbsent(name, String.valueOf(value));
    }

    @Override
    public void addIntHeader(String name, int value) {
        headers.putIfAbsent(name, String.valueOf(value));
    }

    @Override
    public void setStatus(int sc, String sm) {
        status = sc;
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public void setStatus(int sc) {
        status = sc;
    }

    @Override
    public String getHeader(String name) {
        return headers.get(name);
    }

    @Override
    public Collection<String> getHeaders(String name) {
        return null;
    }

    @Override
    public Collection<String> getHeaderNames() {
        return headers.keySet();
    }

    @Override
    public String getCharacterEncoding() {
        return "UTF-8";
    }

    @Override
    public void setCharacterEncoding(String charset) {

    }

    @Override
    public PrintWriter getWriter() {
        return null;
    }

    @Override
    public void setContentLength(int len) {

    }

    @Override
    public void setContentLengthLong(long len) {

    }

    @Override
    public int getBufferSize() {
        return 0;
    }

    @Override
    public void setBufferSize(int size) {

    }

    @Override
    public void flushBuffer() {

    }

    @Override
    public void resetBuffer() {

    }

    @Override
    public boolean isCommitted() {
        return false;
    }

    @Override
    public void reset() {

    }

    @Override
    public Locale getLocale() {
        return null;
    }

    @Override
    public void setLocale(Locale loc) {

    }
}
