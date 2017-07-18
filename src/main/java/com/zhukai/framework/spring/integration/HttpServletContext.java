package com.zhukai.framework.spring.integration;

import com.zhukai.framework.spring.integration.http.HttpParser;
import com.zhukai.framework.spring.integration.http.Session;
import com.zhukai.framework.spring.integration.util.Resources;
import org.apache.log4j.Logger;

import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.util.*;

public class HttpServletContext implements ServletContext {
    private static final Logger logger = Logger.getLogger(HttpServletContext.class);
    private static HttpServletContext instance = new HttpServletContext();

    private Map<String, Object> attributes = Collections.synchronizedMap(new HashMap<>());
    private ThreadLocal<Connection> transaction = new ThreadLocal<>();
    private Map<String, Session> sessions = Collections.synchronizedMap(new HashMap<>());

    public static HttpServletContext getInstance() {
        return instance;
    }

    private HttpServletContext() {

    }

    @Override
    public String getMimeType(String s) {
        return HttpParser.getContentType(s);
    }

    @Override
    public URL getResource(String s) throws MalformedURLException {
        return SpringIntegration.getRunClass().getResource(s);
    }

    @Override
    public InputStream getResourceAsStream(String s) {
        return Resources.getResourceAsStream(s);
    }

    @Override
    public void log(String s) {
        logger.info(s);
    }

    @Override
    public void log(String s, Throwable throwable) {
        logger.error(s, throwable);
    }

    @Override
    public Object getAttribute(String s) {
        return attributes.get(s);
    }

    @Override
    public void setAttribute(String s, Object o) {
        attributes.put(s, o);
    }

    @Override
    public void removeAttribute(String s) {
        attributes.remove(s);
    }

    public Connection getTransaction() {
        return transaction.get();
    }

    public void setTransaction(Connection connection) {
        transaction.set(connection);
    }

    public Session getSession(String sessionId) {
        return sessions.computeIfAbsent(sessionId, Session::new);
    }

    public Map<String, Session> getSessions() {
        return sessions;
    }

    public void refreshSession(String sessionId) {
        if (sessions.get(sessionId) != null) {
            sessions.get(sessionId).setLastAccessedTime(System.currentTimeMillis());
        }
    }

    @Override
    public Enumeration getAttributeNames() {
        Set<String> names = new HashSet<>();
        names.addAll(this.attributes.keySet());
        return Collections.enumeration(names);
    }

    @Override
    @Deprecated
    public String getServletContextName() {
        return null;
    }

    @Override
    @Deprecated
    public String getRealPath(String s) {
        return null;
    }

    @Override
    @Deprecated
    public String getServerInfo() {
        return null;
    }

    @Override
    @Deprecated
    public int getMajorVersion() {
        return 0;
    }

    @Override
    @Deprecated
    public int getMinorVersion() {
        return 0;
    }

    @Override
    @Deprecated
    public ServletContext getContext(String s) {
        return null;
    }

    @Override
    @Deprecated
    public Servlet getServlet(String s) throws ServletException {
        return null;
    }

    @Override
    @Deprecated
    public String getInitParameter(String s) {
        return null;
    }

    @Override
    @Deprecated
    public Set getResourcePaths(String s) {
        return null;
    }

    @Override
    @Deprecated
    public RequestDispatcher getRequestDispatcher(String s) {
        return null;
    }

    @Override
    @Deprecated
    public RequestDispatcher getNamedDispatcher(String s) {
        return null;
    }

    @Override
    @Deprecated
    public Enumeration getServlets() {
        return null;
    }

    @Override
    @Deprecated
    public Enumeration getServletNames() {
        return null;
    }

    @Override
    @Deprecated
    public Enumeration getInitParameterNames() {
        return null;
    }

    @Override
    @Deprecated
    public void log(Exception e, String s) {

    }
}