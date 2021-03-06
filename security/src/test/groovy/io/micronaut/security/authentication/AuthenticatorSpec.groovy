
package io.micronaut.security.authentication

import io.micronaut.security.config.AuthenticationStrategy
import io.micronaut.security.config.InterceptUrlMapPattern
import io.micronaut.security.config.SecurityConfiguration
import io.reactivex.Flowable
import spock.lang.Specification

class AuthenticatorSpec extends Specification {

    SecurityConfiguration ALL = new SecurityConfiguration() {
        @Override
        List<String> getIpPatterns() {
            return null
        }

        @Override
        List<InterceptUrlMapPattern> getInterceptUrlMap() {
            return null
        }

        @Override
        AuthenticationStrategy getAuthenticationStrategy() {
            return AuthenticationStrategy.ALL
        }

    }

    void "if no authentication providers return empty optional"() {
        given:
        Authenticator authenticator = new Authenticator([])

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        Flowable<AuthenticationResponse> rsp = Flowable.fromPublisher(authenticator.authenticate(creds))
        rsp.blockingFirst()

        then:
        thrown(NoSuchElementException)
    }

    void "if any authentication provider throws exception, continue with authentication"() {
        given:
        def authProviderExceptionRaiser = Stub(AuthenticationProvider) {
            authenticate(_, _) >> { Flowable.error( new Exception('Authentication provider raised exception') ) }
        }
        def authProviderOK = Stub(AuthenticationProvider) {
            authenticate(_, _) >> Flowable.just(new UserDetails('admin', []))
        }
        Authenticator authenticator = new Authenticator([authProviderExceptionRaiser, authProviderOK])

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        Flowable<AuthenticationResponse> rsp = authenticator.authenticate(creds)

        then:
        rsp.blockingFirst() instanceof UserDetails
    }

    void "if no authentication provider can authentication, the last error is sent back"() {
        given:
        def authProviderFailed = Stub(AuthenticationProvider) {
            authenticate(_, _) >> Flowable.just( new AuthenticationFailed() )
        }
        Authenticator authenticator = new Authenticator([authProviderFailed])

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        Flowable<AuthenticationResponse> rsp = Flowable.fromPublisher(authenticator.authenticate(creds))

        then:
        rsp.blockingFirst() instanceof AuthenticationFailed
    }

    void "test authentication strategy all with error and empty"() {
        given:
        def providers = [
                Stub(AuthenticationProvider) {
                    authenticate(_, _) >> Flowable.just( new AuthenticationFailed("failed") )
                },
                Stub(AuthenticationProvider) {
                    authenticate(_, _) >> Flowable.empty()
                },
                Stub(AuthenticationProvider) {
                    authenticate(_, _) >> Flowable.just( new UserDetails("a", []) )
                },
        ]
        Authenticator authenticator = new Authenticator(providers, ALL)

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        AuthenticationResponse rsp = Flowable.fromPublisher(authenticator.authenticate(creds)).blockingFirst()

        then: //The last error is returned
        rsp instanceof AuthenticationFailed
        rsp.message.get() == "Provider did not respond. Authentication rejected"
    }

    void "test authentication strategy all with error"() {
        given:
        def providers = [
                Stub(AuthenticationProvider) {
                    authenticate(_, _) >> Flowable.just( new AuthenticationFailed("failed") )
                },
                Stub(AuthenticationProvider) {
                    authenticate(_, _) >> Flowable.just( new UserDetails("a", []) )
                },
        ]
        Authenticator authenticator = new Authenticator(providers, ALL)

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        AuthenticationResponse rsp = Flowable.fromPublisher(authenticator.authenticate(creds)).blockingFirst()

        then: //The last error is returned
        rsp instanceof AuthenticationFailed
        rsp.message.get() == "failed"
    }

    void "test authentication strategy success first"() {
        given:
        def providers = [
                Stub(AuthenticationProvider) {
                    authenticate(_, _) >> Flowable.just( new UserDetails("a", []) )
                },
                Stub(AuthenticationProvider) {
                    authenticate(_, _) >> Flowable.just( new AuthenticationFailed("failed") )
                },
        ]
        Authenticator authenticator = new Authenticator(providers, ALL)

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        AuthenticationResponse rsp = Flowable.fromPublisher(authenticator.authenticate(creds)).blockingFirst()

        then: //The last error is returned
        rsp instanceof AuthenticationFailed
        rsp.message.get() == "failed"
    }

    void "test authentication strategy multiple successes"() {
        given:
        def providers = [
                Stub(AuthenticationProvider) {
                    authenticate(_, _) >> Flowable.just( new UserDetails("a", []) )
                },
                Stub(AuthenticationProvider) {
                    authenticate(_, _) >> Flowable.just( new UserDetails("b", []) )
                },
        ]
        Authenticator authenticator = new Authenticator(providers, ALL)

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        AuthenticationResponse rsp = Flowable.fromPublisher(authenticator.authenticate(creds)).blockingFirst()

        then: //The last error is returned
        rsp instanceof UserDetails
        ((UserDetails) rsp).username == "b"
    }
}
