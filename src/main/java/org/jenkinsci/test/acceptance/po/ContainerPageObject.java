package org.jenkinsci.test.acceptance.po;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Injector;
import groovy.lang.Closure;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Callable;

import static org.hamcrest.CoreMatchers.*;
import static org.jenkinsci.test.acceptance.Matchers.*;

/**
 * {@link PageObject} that represents a model that has multiple views underneath.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class ContainerPageObject extends PageObject {
    protected ContainerPageObject(Injector injector, URL url) {
        super(injector, url);
        if (!url.toExternalForm().endsWith("/"))
            throw new IllegalArgumentException("URL should end with '/': "+url);
    }

    protected ContainerPageObject(PageObject context, URL url) {
        this(context.injector,url);
    }

    public void configure(Closure body) {
        configure();
        body.call(this);
        save();
    }

    public <T> T configure(Callable<T> body) {
        try {
            configure();
            T v = body.call();
            save();
            return v;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Open configuration page if not yet opened.
     *
     * @see #getConfigUrl()
     */
    public void configure() {
        if(driver.getCurrentUrl().equals(getConfigUrl().toExternalForm())) return;
        visit(getConfigUrl());
    }

    /**
     * Makes sure that the browser is currently opening the configuration page.
     */
    public void ensureConfigPage() {
        assertThat(driver.getCurrentUrl(), is(getConfigUrl().toExternalForm()));
    }

    public URL getConfigUrl() {
        return url("configure");
    }

    public void save() {
        clickButton("Save");
        assertThat(driver, not(hasContent("This page expects a form submission")));
    }

    public URL getJsonApiUrl() {
        return url("api/json");
    }

    /**
     * Makes the API call and obtains JSON representation.
     */
    public JsonNode getJson() {
        return getJson(null);
    }

    /**
     * @param queryString
     *      Additional query string to narrow down the data retrieval, like "tree=..." or "depth=..."
     */
    public JsonNode getJson(String queryString) {

        URL url = getJsonApiUrl();
        try {
            if (queryString!=null)
                url = new URL(url+"?"+queryString);

            // Pass in all the cookies (in particular the session cookie.)
            // This ensures that the API call sees what the current user sees.
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestProperty("Cookie", StringUtils.join(driver.manage().getCookies(),";"));

            return jsonParser.readTree(con.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read from "+ url,e);
        }
    }

    /**
     * Create action of this page object.
     *
     * @param type Action type to create.
     * @see {@link Action}, {@link ActionPageObject}
     */
    public <T extends Action> T action(Class<T> type) {
        final String path = type.getAnnotation(ActionPageObject.class).value();
        return action(type, path);
    }

    public <T extends Action> T action(Class<T> type, String path) {

        T instance = newInstance(type, this, path);

        if (!instance.isApplicable(this)) throw new AssertionError(
                "Action can not be attached to " + getClass().getCanonicalName()
        );

        return instance;
    }
}
