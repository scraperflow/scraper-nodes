import scraper.api.node.type.Node;
import scraper.nodes.server.SocketNode;

open module scraper.nodes.server {
    requires scraper.core;

    requires java.net.http;
    requires com.fasterxml.jackson.databind;
    requires org.eclipse.jetty.server;
    requires org.eclipse.jetty.servlet;
    requires org.eclipse.jetty.util;
    requires org.eclipse.jetty.security;
    requires javax.servlet.api;
    requires org.apache.commons.io;
    requires org.apache.httpcomponents.httpcore;
    requires org.apache.httpcomponents.httpclient;
    requires org.eclipse.jetty.rewrite;

    // FIXME why is this needed so that reflections can find all nodes?
    provides Node with SocketNode;
}
