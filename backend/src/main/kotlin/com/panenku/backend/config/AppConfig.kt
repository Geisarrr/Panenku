package com.panenku.backend.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@ConfigurationProperties(prefix = "app")
data class AppConfig(val jwt: JwtConfig = JwtConfig(), val minio: MinioConfig = MinioConfig()) {
    data class JwtConfig(val secret: String = "", val issuer: String = "panenku", val expiration: Long = 86400000)
    data class MinioConfig(val endpoint: String = "localhost:9000", val accessKey: String = "", val secretKey: String = "", val bucket: String = "panenku-files")
}

@Configuration
@EnableConfigurationProperties(AppConfig::class)
class AppPropertiesConfiguration

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = ["scheduler.enabled"], havingValue = "true", matchIfMissing = true)
class SchedulingConfiguration
