/**
 * Copyright 2005-2009 Noelios Technologies.
 * 
 * The contents of this file are subject to the terms of one of the following
 * open source licenses: LGPL 3.0 or LGPL 2.1 or CDDL 1.0 or EPL 1.0 (the
 * "Licenses"). You can select the license that you prefer but you may not use
 * this file except in compliance with one of these Licenses.
 * 
 * You can obtain a copy of the LGPL 3.0 license at
 * http://www.opensource.org/licenses/lgpl-3.0.html
 * 
 * You can obtain a copy of the LGPL 2.1 license at
 * http://www.opensource.org/licenses/lgpl-2.1.php
 * 
 * You can obtain a copy of the CDDL 1.0 license at
 * http://www.opensource.org/licenses/cddl1.php
 * 
 * You can obtain a copy of the EPL 1.0 license at
 * http://www.opensource.org/licenses/eclipse-1.0.php
 * 
 * See the Licenses for the specific language governing permissions and
 * limitations under the Licenses.
 * 
 * Alternatively, you can obtain a royalty free commercial license with less
 * limitations, transferable or non-transferable, directly at
 * http://www.noelios.com/products/restlet-engine
 * 
 * Restlet is a registered trademark of Noelios Technologies.
 */

package org.restlet.ext.httpclient;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.restlet.Client;
import org.restlet.Request;
import org.restlet.data.Protocol;
import org.restlet.engine.Engine;
import org.restlet.engine.http.HttpClientCall;
import org.restlet.ext.httpclient.internal.HttpMethodCall;

/**
 * HTTP client connector using the HttpMethodCall and Apache HTTP Client
 * project. Note that the response must be fully read in all cases in order to
 * surely release the underlying connection. Not doing so may cause future
 * requests to block.<br>
 * <br>
 * Here is the list of parameters that are supported. They should be set in the
 * Client's context before it is started:
 * <table>
 * <tr>
 * <th>Parameter name</th>
 * <th>Value type</th>
 * <th>Default value</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>followRedirects</td>
 * <td>boolean</td>
 * <td>false</td>
 * <td>If true, the protocol will automatically follow redirects. If false, the
 * protocol will not automatically follow redirects.</td>
 * </tr>
 * <tr>
 * <td>maxConnectionsPerHost</td>
 * <td>int</td>
 * <td>2 (uses HttpClient's default)</td>
 * <td>The maximum number of connections that will be created for any particular
 * host.</td>
 * </tr>
 * <tr>
 * <td>maxTotalConnections</td>
 * <td>int</td>
 * <td>20 (uses HttpClient's default)</td>
 * <td>The maximum number of active connections.</td>
 * </tr>
 * <tr>
 * <td>connectionTimeout</td>
 * <td>int</td>
 * <td>0</td>
 * <td>The timeout in milliseconds used when establishing an HTTP connection.</td>
 * </tr>
 * <tr>
 * <td>stopIdleTimeout</td>
 * <td>int</td>
 * <td>1000</td>
 * <td>The minimum idle time, in milliseconds, for connections to be closed when
 * stopping the connector.</td>
 * </tr>
 * <tr>
 * <td>socketTimeout</td>
 * <td>int</td>
 * <td>0</td>
 * <td>Sets the socket timeout to a specified timeout, in milliseconds. A
 * timeout of zero is interpreted as an infinite timeout.</td>
 * </tr>
 * <tr>
 * <td>retryHandler</td>
 * <td>String</td>
 * <td>null</td>
 * <td>Class name of the retry handler to use instead of HTTP Client default
 * behavior. The given class name must extend the
 * org.apache.http.client.HttpRequestRetryHandler class and have a default
 * constructor</td>
 * </tr>
 * <tr>
 * <td>tcpNoDelay</td>
 * <td>boolean</td>
 * <td>false</td>
 * <td>Indicate if Nagle's TCP_NODELAY algorithm should be used.</td>
 * </tr>
 * </table>
 * 
 * @see <a href=
 *      "http://jakarta.apache.org/httpcomponents/httpclient-3.x/tutorial.html"
 *      >Apache HTTP Client tutorial</a>
 * @see <a
 *      href="http://java.sun.com/j2se/1.5.0/docs/guide/net/index.html">Networking
 *      Features</a>
 * @author Jerome Louvel
 */
public class HttpClientHelper extends org.restlet.engine.http.HttpClientHelper {
    private volatile DefaultHttpClient httpClient;

    /**
     * Constructor.
     * 
     * @param client
     *            The client to help.
     */
    public HttpClientHelper(Client client) {
        super(client);
        this.httpClient = null;
        getProtocols().add(Protocol.HTTP);
        getProtocols().add(Protocol.HTTPS);
    }

    /**
     * Configures the HTTP client. By default, it try to set the retry handler.
     * 
     * @param httpClient
     *            The HTTP client to configure.
     */
    protected void configure(DefaultHttpClient httpClient) {
        if (getRetryHandler() != null) {
            try {
                HttpRequestRetryHandler retryHandler = (HttpRequestRetryHandler) Engine
                        .loadClass(getRetryHandler()).newInstance();
                this.httpClient.setHttpRequestRetryHandler(retryHandler);
            } catch (Exception e) {
                getLogger()
                        .log(
                                Level.WARNING,
                                "An error occurred during the instantiation of the retry handler.",
                                e);
            }
        }
    }

    /**
     * Configures the various parameters of the connection manager and the HTTP
     * client.
     * 
     * @param params
     *            The parameter list to update.
     */
    protected void configure(HttpParams params) {
        ConnManagerParams.setMaxTotalConnections(params,
                getMaxTotalConnections());
        ConnManagerParams.setMaxConnectionsPerRoute(params,
                new ConnPerRouteBean(getMaxConnectionsPerHost()));

        // Configure other parameters
        HttpClientParams.setAuthenticating(params, false);
        HttpClientParams.setRedirecting(params, isFollowRedirects());
        HttpClientParams.setCookiePolicy(params,
                CookiePolicy.BROWSER_COMPATIBILITY);
        HttpConnectionParams.setTcpNoDelay(params, getTcpNoDelay());
        HttpConnectionParams.setConnectionTimeout(params,
                getConnectionTimeout());
        HttpConnectionParams.setSoTimeout(params, getSocketTimeout());
    }

    /**
     * Configures the scheme registry. By default, it registers the HTTP and the
     * HTTPS schemes.
     * 
     * @param schemeRegistry
     *            The scheme registry to configure.
     */
    protected void configure(SchemeRegistry schemeRegistry) {
        schemeRegistry.register(new Scheme("http", PlainSocketFactory
                .getSocketFactory(), 80));
        schemeRegistry.register(new Scheme("https", SSLSocketFactory
                .getSocketFactory(), 443));
    }

    /**
     * Creates a low-level HTTP client call from a high-level uniform call.
     * 
     * @param request
     *            The high-level request.
     * @return A low-level HTTP client call.
     */
    @Override
    public HttpClientCall create(Request request) {
        HttpClientCall result = null;

        try {
            result = new HttpMethodCall(this, request.getMethod().toString(),
                    request.getResourceRef().toString(), request
                            .isEntityAvailable());
        } catch (IOException ioe) {
            getLogger().log(Level.WARNING,
                    "Unable to create the HTTP client call", ioe);
        }

        return result;
    }

    /**
     * Creates the connection manager. By default, it creates a thread safe
     * connection manager.
     * 
     * @param params
     *            The configuration parameters.
     * @param schemeRegistry
     *            The scheme registry to use.
     * @return The created connection manager.
     */
    protected ClientConnectionManager createClientConnectionManager(
            HttpParams params, SchemeRegistry schemeRegistry) {
        return new ThreadSafeClientConnManager(params, schemeRegistry);
    }

    /**
     * Returns the timeout in milliseconds used when establishing an HTTP
     * connection.
     * 
     * @return The timeout in milliseconds used when retrieving an HTTP
     *         connection from the HTTP connection manager.
     */
    public int getConnectionTimeout() {
        return Integer.parseInt(getHelpedParameters().getFirstValue(
                "connectionTimeout", "0"));
    }

    public HttpClient getHttpClient() {
        return this.httpClient;
    }

    /**
     * Returns the maximum number of connections that will be created for any
     * particular host.
     * 
     * @return The maximum number of connections that will be created for any
     *         particular host.
     */
    public int getMaxConnectionsPerHost() {
        return Integer.parseInt(getHelpedParameters().getFirstValue(
                "maxConnectionsPerHost", "2"));
    }

    /**
     * Returns the maximum number of active connections.
     * 
     * @return The maximum number of active connections.
     */
    public int getMaxTotalConnections() {
        return Integer.parseInt(getHelpedParameters().getFirstValue(
                "maxTotalConnections", "20"));
    }

    /**
     * Returns the class name of the retry handler to use instead of HTTP Client
     * default behavior. The given class name must implement the
     * org.apache.commons.httpclient.HttpMethodRetryHandler interface and have a
     * default constructor.
     * 
     * @return The class name of the retry handler.
     */
    public String getRetryHandler() {
        return getHelpedParameters().getFirstValue("retryHandler", null);
    }

    /**
     * Returns the socket timeout value. A timeout of zero is interpreted as an
     * infinite timeout.
     * 
     * @return The read timeout value.
     */
    public int getSocketTimeout() {
        return Integer.parseInt(getHelpedParameters().getFirstValue(
                "socketTimeout", "0"));
    }

    /**
     * Returns the minimum idle time, in milliseconds, for connections to be
     * closed when stopping the connector.
     * 
     * @return The minimum idle time, in milliseconds, for connections to be
     *         closed when stopping the connector.
     */
    public int getStopIdleTimeout() {
        return Integer.parseInt(getHelpedParameters().getFirstValue(
                "stopIdleTimeout", "1000"));
    }

    /**
     * Indicates if the protocol will use Nagle's algorithm
     * 
     * @return True to enable TCP_NODELAY, false to disable.
     * @see java.net.Socket#setTcpNoDelay(boolean)
     */
    public boolean getTcpNoDelay() {
        return Boolean.parseBoolean(getHelpedParameters().getFirstValue(
                "tcpNoDelay", "false"));
    }

    /**
     * Indicates if the protocol will automatically follow redirects.
     * 
     * @return True if the protocol will automatically follow redirects.
     */
    public boolean isFollowRedirects() {
        return Boolean.parseBoolean(getHelpedParameters().getFirstValue(
                "followRedirects", "false"));
    }

    @Override
    public void start() throws Exception {
        super.start();

        // Define configuration parameters
        HttpParams params = new BasicHttpParams();
        configure(params);

        // Set-up the scheme registry
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        configure(schemeRegistry);

        // Create the connection manager
        ClientConnectionManager connectionManager = createClientConnectionManager(
                params, schemeRegistry);

        // Create and configure the HTTP client
        this.httpClient = new DefaultHttpClient(connectionManager, params);
        configure(this.httpClient);

        getLogger().info("Starting the HTTP client");
    }

    @Override
    public void stop() throws Exception {
        getHttpClient().getConnectionManager().closeExpiredConnections();
        getHttpClient().getConnectionManager().closeIdleConnections(
                getStopIdleTimeout(), TimeUnit.MILLISECONDS);
        getHttpClient().getConnectionManager().shutdown();
        getLogger().info("Stopping the HTTP client");
    }

}