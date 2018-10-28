package org.gwtproject.resources.client.impl;

import elemental2.core.JsArray;
import elemental2.core.JsObject;
import jsinterop.base.Js;
import org.gwtproject.http.client.Request;
import org.gwtproject.http.client.RequestBuilder;
import org.gwtproject.http.client.RequestCallback;
import org.gwtproject.http.client.RequestException;
import org.gwtproject.http.client.Response;
import org.gwtproject.jsonp.client.JsonpRequestBuilder;
import org.gwtproject.resources.client.ExternalTextResource;
import org.gwtproject.resources.client.JSON;
import org.gwtproject.resources.client.ResourceCallback;
import org.gwtproject.resources.client.ResourceException;
import org.gwtproject.resources.client.TextResource;
import org.gwtproject.safehtml.shared.SafeUri;
import org.gwtproject.safehtml.shared.annotations.SuppressIsTrustedResourceUriCastCheck;
import org.gwtproject.user.client.rpc.AsyncCallback;

/**
 * @author Dmitrii Tikhomirov
 * Created by treblereel on 10/16/18.
 */
public class ExternalTextResourcePrototype implements ExternalTextResource {

    /**
     * Maps the HTTP callback onto the ResourceCallback.
     */
    private class ETRCallback implements RequestCallback, AsyncCallback<JsObject> {
        final ResourceCallback<TextResource> callback;

        public ETRCallback(ResourceCallback<TextResource> callback) {
            this.callback = callback;
        }

        // For RequestCallback
        public void onError(Request request, Throwable exception) {
            onFailure(exception);
        }

        // For AsyncCallback
        public void onFailure(Throwable exception) {
            callback.onError(new ResourceException(ExternalTextResourcePrototype.this,
                    "Unable to retrieve external resource", exception));
        }

        // For RequestCallback
        public void onResponseReceived(Request request, final Response response) {
            String responseText = response.getText();
            // Call eval() on the object.
            JsObject jso = evalObject(responseText);
            onSuccess(jso);
        }

        // For AsyncCallback
        public void onSuccess(JsObject jso) {
            if (jso == null) {
                callback.onError(new ResourceException(ExternalTextResourcePrototype.this, "eval() returned null"));
                return;
            }

            // Populate the TextResponse cache array
            final String resourceText = extractString(jso, index);
            cache[index] = new TextResource() {

                public String getName() {
                    return name;
                }

                public String getText() {
                    return resourceText;
                }

            };

            // Finish by invoking the callback
            callback.onSuccess(cache[index]);
        }
    }

    /**
     * Evaluate the JSON payload. The regular expression to validate the safety of
     * the payload is taken from RFC 4627 (D. Crockford).
     *
     * @param data the raw JSON-encapsulated string bundle
     * @return the evaluated JSON object, or <code>null</code> if there is an
     * error.
     */
    private static JsObject evalObject(String data) {
        return JSON.parse(data);
    }


    /**
     * Evaluate the JSON payload. The regular expression to validate the safety of
     * the payload is taken from RFC 4627 (D. Crockford).
     *
     * @param data the raw JSON-encapsulated string bundle
     * @return the evaluated JSON object, or <code>null</code> if there is an
     *         error.
     */

    /**
     * Extract the specified String from a JavaScriptObject that is array-like.
     *
     * @param jso   the JavaScriptObject returned from {@link #evalObject(String)}
     * @param index the index of the string to extract
     * @return the requested string, or <code>null</code> if it does not exist.
     */
    private static String extractString(JsObject jso, int index) {
        JsArray<String> array = Js.cast(jso);
        if (array.length > index) {
            return array.getAt(index);
        }
        return null;
    }

    /**
     * This is a reference to an array nominally created in the IRB that contains
     * the ExternalTextResource. It is intended to be shared between all instances
     * of the ETR that have a common parent IRB.
     */
    private final TextResource[] cache;
    private final int index;
    private final String md5Hash;
    private final String name;
    private final SafeUri url;

    public ExternalTextResourcePrototype(String name, SafeUri url, TextResource[] cache, int index) {
        this.name = name;
        this.url = url;
        this.cache = cache;
        this.index = index;
        this.md5Hash = null;
    }

    public ExternalTextResourcePrototype(String name, SafeUri url, TextResource[] cache, int index, String md5Hash) {
        this.name = name;
        this.url = url;
        this.cache = cache;
        this.index = index;
        this.md5Hash = md5Hash;
    }

    public String getName() {
        return name;
    }

    /**
     * Possibly fire off an HTTPRequest for the text resource.
     */
    @SuppressIsTrustedResourceUriCastCheck
    public void getText(ResourceCallback<TextResource> callback) throws ResourceException {

        // If we've already parsed the JSON bundle, short-circuit.
        if (cache[index] != null) {
            callback.onSuccess(cache[index]);
            return;
        }

        if (md5Hash != null) {
            // If we have an md5Hash, we should be using JSONP
            JsonpRequestBuilder rb = new JsonpRequestBuilder();
            rb.setPredeterminedId(md5Hash);
            rb.requestObject(url.asString(), new ExternalTextResourcePrototype.ETRCallback(callback));
        } else {
            RequestBuilder rb = new RequestBuilder(RequestBuilder.GET, url.asString());
            try {
                rb.sendRequest("", new ExternalTextResourcePrototype.ETRCallback(callback));
            } catch (RequestException e) {
                throw new ResourceException(this, "Unable to initiate request for external resource", e);
            }
        }
    }
}
