package org.touchhome.app.utils;

import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.client.apache.ApacheHttpClient;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;
import java.util.List;

/**
 * This class performs direct GET, POST, PUT, DELETE RESTful methods calls
 */
// TODO: refacotr or remove at all
public class RestClient {
    private static final MediaType[] MEDIA_TYPES = new MediaType[]{MediaType.APPLICATION_JSON_TYPE,
            MediaType.APPLICATION_XML_TYPE};
    private final String serverUrl;
    private ApacheHttpClient client;
    private WebResource webResource;

    public RestClient(String serverUrl, final String[] credentials) {
        this.serverUrl = serverUrl;
        client = ClientHelper.createApacheClient();
        setCredentials(credentials);
    }

    /**
     * Performs GET method call
     *
     * @param path
     * @param queryData to be used as query params
     * @param cookies
     * @param clazz
     * @param <T>
     * @return
     */
    public <T> T get(String path, MultivaluedMap<String, String> queryData, List<NewCookie> cookies, Class<T> clazz) {
        WebResource.Builder builder = webResource.path(path).queryParams(queryData).accept(MEDIA_TYPES);
        for (NewCookie cookie : cookies) {
            builder.cookie(cookie);
        }
        return builder.get(clazz);
    }

    /**
     * Performs POST method call
     *
     * @param path
     * @param queryData to be used as query params
     * @param bodyData  to be used as body data
     * @param cookies
     * @param clazz
     * @param <T>
     * @return
     */
    public <T> T post(String path, MultivaluedMap<String, String> queryData, Object bodyData, MediaType mediaType,
                      List<NewCookie> cookies, Class<T> clazz) {
        WebResource.Builder builder = webResource.path(path).queryParams(queryData).type(mediaType).accept(MEDIA_TYPES);
        for (NewCookie cookie : cookies) {
            builder.cookie(cookie);
        }
        try {
            return builder.post(clazz, bodyData);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }


    /**
     * Performs PUT method call
     *
     * @param path
     * @param queryData to be used as query params
     * @param bodyData  to be used as body data
     * @param cookies
     * @param clazz
     * @param <T>
     * @return
     */
    public <T> T put(String path, MultivaluedMap<String, String> queryData, Object bodyData, MediaType mediaType,
                     List<NewCookie> cookies, Class<T> clazz) {
        WebResource.Builder builder = webResource.path(path).queryParams(queryData).type(mediaType).accept(MEDIA_TYPES);
        for (NewCookie cookie : cookies) {
            builder.cookie(cookie);
        }
        return builder.put(clazz, bodyData);
    }

    /**
     * Performs DELETE method call
     *
     * @param path
     * @param queryData to be used as query params
     * @param bodyData  to be used as body data
     * @param cookies
     * @param clazz
     * @param <T>
     * @return
     */
    public <T> T delete(String path, MultivaluedMap<String, String> queryData, Object bodyData, MediaType mediaType,
                        List<NewCookie> cookies, Class<T> clazz) {
        WebResource.Builder builder = webResource.path(path).queryParams(queryData).type(mediaType).accept(MEDIA_TYPES);
        for (NewCookie cookie : cookies) {
            builder.cookie(cookie);
        }
        return builder.delete(clazz, bodyData);
    }

    /**
     * Should be called before any of {@link #get(String, javax.ws.rs.core.MultivaluedMap, java.util.List, Class)},
     * {@link #post(String, javax.ws.rs.core.MultivaluedMap, Object, javax.ws.rs.core.MediaType, java.util.List, Class)},
     * {@link #put(String, javax.ws.rs.core.MultivaluedMap, Object, javax.ws.rs.core.MediaType, java.util.List, Class)},
     * {@link #delete(String, javax.ws.rs.core.MultivaluedMap, Object, javax.ws.rs.core.MediaType, java.util.List, Class)} methods are called.
     *
     * @param credentials if empty, assume anonymous session, so preemptive auth will not be set (false by default). Otherwise
     *                    credentials will be set and preemptive auth will be set to true.
     */
    private void setCredentials(final String[] credentials) {
        if (credentials == null || credentials.length == 0) {
            ClientHelper.setAuthPreemptive(client, false);
            webResource = client.resource(serverUrl);
            return;
        }
        ClientHelper.setAuthPreemptive(client, true);
        webResource = client.resource(serverUrl);
        webResource.addFilter(new HTTPBasicAuthFilter(credentials[0], credentials[1]));
    }

}
