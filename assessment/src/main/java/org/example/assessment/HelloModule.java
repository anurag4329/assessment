package org.example.assessment;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

import org.onehippo.repository.jaxrs.RepositoryJaxrsEndpoint;
import org.onehippo.repository.jaxrs.RepositoryJaxrsService;
import org.onehippo.repository.modules.DaemonModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HelloModule implements DaemonModule {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public void initialize(final Session systemSession) throws RepositoryException {
        RepositoryJaxrsService.addEndpoint(
                new RepositoryJaxrsEndpoint("/hello")
                        .singleton(new JacksonJsonProvider())
                        .singleton(new HelloResource(systemSession)));
        log.debug("/hello endpoint added");
    }

    @Override
    public void shutdown() {
        RepositoryJaxrsService.removeEndpoint("/hello");
        log.debug("/hello endpoint removed");
    }

    public static class HelloResource {

        private final Session systemSession;

        public HelloResource(Session systemSession) {
            this.systemSession = systemSession;

        }

        @Path("/")
        @GET
        public String sayHello() {
            return "Hello "+ systemSession.getUserID()+"!";
        }
    }
}
