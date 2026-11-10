package com.panenku.backend

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

/**
 * Entry point aplikasi backend Panenku.
 *
 * Spring Boot 3 + Kotlin + Spring Data JPA + PostgreSQL.
 *
 * Semua konfigurasi tambahan (Security, JPA, MinIO, dll) dapat ditambahkan
 * melalui class @Configuration di package config atau lewat application.properties.
 */
@SpringBootApplication
class Application

fun main(args: Array<String>) {
    SpringApplication.run(Application::class.java, *args)
}
