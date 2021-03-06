
package io.micronaut.security.token.jwt.signature.rsa

import com.nimbusds.jwt.EncryptedJWT
import com.nimbusds.jwt.JWTParser
import com.nimbusds.jwt.SignedJWT
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.token.generator.TokenGenerator
import io.micronaut.security.token.jwt.AuthorizationUtils
import io.micronaut.security.token.jwt.encryption.EncryptionConfiguration
import io.micronaut.security.token.jwt.generator.JwtTokenGenerator
import io.micronaut.security.token.jwt.signature.SignatureConfiguration
import io.micronaut.security.token.jwt.signature.SignatureGeneratorConfiguration
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class SignRSANotEncrypSpec extends Specification implements AuthorizationUtils {

    @Shared
    File pemFile = new File('src/test/resources/rsa-2048bit-key-pair.pem')

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'signaturersa',
            'endpoints.beans.enabled': true,
            'endpoints.beans.sensitive': true,
            'micronaut.security.enabled': true,
            'micronaut.security.endpoints.login.enabled': true,
            'micronaut.security.token.enabled': true,
            'micronaut.security.token.jwt.enabled': true,
            'pem.path': pemFile.absolutePath,
    ], Environment.TEST)

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "test /beans is secured"() {
        when:
        get("/beans")

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
    }

    void "/beans can be accessed if authenticated"() {
        expect:
        embeddedServer.applicationContext.getBean(PS512RSASignatureConfiguration.class)
        embeddedServer.applicationContext.getBean(RSASignatureConfiguration.class)
        embeddedServer.applicationContext.getBean(RSASignatureConfiguration.class, Qualifiers.byName("generator"))
        embeddedServer.applicationContext.getBean(RSASignatureGeneratorConfiguration.class)
        embeddedServer.applicationContext.getBean(RSASignatureGeneratorConfiguration.class, Qualifiers.byName("generator"))
        embeddedServer.applicationContext.getBean(RSASignatureFactory.class)
        embeddedServer.applicationContext.getBean(SignatureConfiguration.class)
        embeddedServer.applicationContext.getBean(SignatureConfiguration.class, Qualifiers.byName("generator"))
        embeddedServer.applicationContext.getBean(SignatureGeneratorConfiguration.class)
        embeddedServer.applicationContext.getBean(SignatureGeneratorConfiguration.class, Qualifiers.byName("generator"))
        embeddedServer.applicationContext.getBean(TokenGenerator.class)

        when:
        embeddedServer.applicationContext.getBean(EncryptionConfiguration.class)

        then:
        thrown(NoSuchBeanException)

        when:
        JwtTokenGenerator tokenGenerator = embeddedServer.applicationContext.getBean(JwtTokenGenerator.class)

        then:
        tokenGenerator.getSignatureConfiguration() instanceof RSASignature
        tokenGenerator.getSignatureConfiguration() instanceof RSASignatureGenerator

        when:
        String token = loginWith(client,'user', 'password')

        then:
        token
        !(JWTParser.parse(token) instanceof EncryptedJWT)
        JWTParser.parse(token) instanceof SignedJWT

        when:
        get("/beans", token)

        then:
        noExceptionThrown()
    }
}
