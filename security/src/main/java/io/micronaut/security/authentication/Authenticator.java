/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.security.authentication;

import io.micronaut.http.HttpRequest;
import io.micronaut.security.config.AuthenticationStrategy;
import io.micronaut.security.config.SecurityConfiguration;
import io.reactivex.Flowable;
import io.reactivex.exceptions.CompositeException;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * An Authenticator operates on several {@link AuthenticationProvider} instances returning the first
 * authenticated {@link AuthenticationResponse}.
 *
 * @author Sergio del Amo
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class Authenticator {
    private static final Logger LOG = LoggerFactory.getLogger(Authenticator.class);

    protected final Collection<AuthenticationProvider> authenticationProviders;
    private final SecurityConfiguration securityConfiguration;

    /**
     * @param authenticationProviders A list of available authentication providers
     */
    @Deprecated
    public Authenticator(Collection<AuthenticationProvider> authenticationProviders) {
        this.authenticationProviders = authenticationProviders;
        this.securityConfiguration = null;
    }

    /**
     * @param authenticationProviders A list of available authentication providers
     * @param securityConfiguration The security configuration
     */
    @Inject
    public Authenticator(Collection<AuthenticationProvider> authenticationProviders,
                         SecurityConfiguration securityConfiguration) {
        this.authenticationProviders = authenticationProviders;
        this.securityConfiguration = securityConfiguration;
    }

    /**
     * Authenticates the user with the provided credentials.
     *
     * @param authenticationRequest Represents a request to authenticate.
     * @return A publisher that emits {@link AuthenticationResponse} objects
     * @deprecated Use {@link #authenticate(HttpRequest, AuthenticationRequest)} instead.
     */
    @Deprecated
    public Publisher<AuthenticationResponse> authenticate(AuthenticationRequest authenticationRequest) {
        return authenticate(null, authenticationRequest);
    }

    /**
     * Authenticates the user with the provided credentials.
     *
     * @param request The HTTP request
     * @param authenticationRequest Represents a request to authenticate.
     * @return A publisher that emits {@link AuthenticationResponse} objects
     */
    public Publisher<AuthenticationResponse> authenticate(HttpRequest<?> request, AuthenticationRequest<?, ?> authenticationRequest) {
        if (this.authenticationProviders == null) {
            return Flowable.empty();
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug(authenticationProviders.stream().map(AuthenticationProvider::getClass).map(Class::getName).collect(Collectors.joining()));
        }
        if (securityConfiguration != null && securityConfiguration.getAuthenticationStrategy() == AuthenticationStrategy.ALL) {
            return Flowable.mergeDelayError(
                    authenticationProviders.stream()
                            .map(provider -> {
                                return Flowable.fromPublisher(provider.authenticate(request, authenticationRequest))
                                        .switchMap(response -> {
                                            if (response.isAuthenticated()) {
                                                return Flowable.just(response);
                                            } else {
                                                return Flowable.error(() -> new AuthenticationException(response));
                                            }
                                        })
                                        .switchIfEmpty(Flowable.error(() -> new AuthenticationException("Provider did not respond. Authentication rejected")));
                            })
                            .collect(Collectors.toList()))
                    .lastOrError()
                    .onErrorReturn((t) -> {
                        if (t instanceof CompositeException) {
                            List<Throwable> exceptions = ((CompositeException) t).getExceptions();
                            return new AuthenticationFailed(exceptions.get(exceptions.size() - 1).getMessage());
                        } else {
                            return new AuthenticationFailed(t.getMessage());
                        }
                    })
                    .toFlowable();
        } else {
            Iterator<AuthenticationProvider> providerIterator = authenticationProviders.iterator();
            if (providerIterator.hasNext()) {
                Flowable<AuthenticationProvider> providerFlowable = Flowable.just(providerIterator.next());
                AtomicReference<AuthenticationResponse> lastFailure = new AtomicReference<>();
                return attemptAuthenticationRequest(request, authenticationRequest, providerIterator, providerFlowable, lastFailure);
            } else {
                return Flowable.empty();
            }
        }
    }

    private Flowable<AuthenticationResponse> attemptAuthenticationRequest(
            HttpRequest<?> request,
        AuthenticationRequest authenticationRequest,
        Iterator<AuthenticationProvider> providerIterator,
        Flowable<AuthenticationProvider> providerFlowable, AtomicReference<AuthenticationResponse> lastFailure) {

        return providerFlowable.switchMap(authenticationProvider -> {
            Flowable<AuthenticationResponse> responseFlowable = Flowable.fromPublisher(authenticationProvider.authenticate(request, authenticationRequest));
            Flowable<AuthenticationResponse> authenticationAttemptFlowable = responseFlowable.switchMap(authenticationResponse -> {
                if (authenticationResponse.isAuthenticated()) {
                    return Flowable.just(authenticationResponse);
                } else if (providerIterator.hasNext()) {
                    lastFailure.set(authenticationResponse);
                    // recurse
                    return attemptAuthenticationRequest(
                            request,
                        authenticationRequest,
                        providerIterator,
                        Flowable.just(providerIterator.next()),
                        lastFailure);
                } else {
                    lastFailure.set(authenticationResponse);
                    return Flowable.just(authenticationResponse);
                }
            });
            return authenticationAttemptFlowable.onErrorResumeNext(throwable -> {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Authentication provider threw exception", throwable);
                }
                if (providerIterator.hasNext()) {
                    // recurse
                    return attemptAuthenticationRequest(
                            request,
                        authenticationRequest,
                        providerIterator,
                        Flowable.just(providerIterator.next()),
                        lastFailure);
                } else {
                    AuthenticationResponse lastFailureResponse = lastFailure.get();
                    if (lastFailureResponse != null) {
                        return Flowable.just(lastFailureResponse);
                    }
                    return Flowable.empty();
                }
            });
        });
    }
}
