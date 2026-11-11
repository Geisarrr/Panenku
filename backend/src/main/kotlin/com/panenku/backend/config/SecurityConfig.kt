package com.panenku.backend.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.panenku.backend.common.exception.ErrorResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain
import javax.crypto.spec.SecretKeySpec
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Configuration
class SecurityConfig {
    @Bean fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun jwtDecoder(@Value("\${panenku.jwt.secret}") secret: String, @Value("\${panenku.jwt.issuer}") issuer: String, clock: Clock): JwtDecoder {
        require(secret.toByteArray(Charsets.UTF_8).size >= 32) { "JWT_SECRET minimal 32 byte UTF-8" }
        val decoder = NimbusJwtDecoder.withSecretKey(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            .macAlgorithm(MacAlgorithm.HS256).build()
        decoder.setJwtValidator(DelegatingOAuth2TokenValidator(
            JwtTimestampValidator().also { it.setClock(clock) }, JwtIssuerValidator(issuer),
            JwtClaimValidator<Instant>("exp") { it != null },
            JwtClaimValidator<String?>("sub") { value -> value != null && runCatching { UUID.fromString(value) }.isSuccess }
        ))
        return decoder
    }

    @Bean
    fun security(http: HttpSecurity, mapper: ObjectMapper): SecurityFilterChain {
        val unauthorized = AuthenticationEntryPoint { request, response, ex ->
            BearerTokenAuthenticationEntryPoint().commence(request, response, ex)
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            mapper.writeValue(response.outputStream, ErrorResponse(code = "UNAUTHORIZED", message = "Autentikasi diperlukan atau token tidak valid."))
        }
        val denied = AccessDeniedHandler { request, response, ex ->
            BearerTokenAccessDeniedHandler().handle(request, response, ex)
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            mapper.writeValue(response.outputStream, ErrorResponse(code = "FORBIDDEN", message = "Akses tidak diizinkan."))
        }
        return http.csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/buyer/products", "/api/v1/buyer/products/*", "/api/v1/buyer/commodities").permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { it.authenticationEntryPoint(unauthorized).accessDeniedHandler(denied) }
            .oauth2ResourceServer { it.jwt { }.authenticationEntryPoint(unauthorized).accessDeniedHandler(denied) }
            .build()
    }
}
