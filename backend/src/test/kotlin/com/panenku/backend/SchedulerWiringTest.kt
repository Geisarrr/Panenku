package com.panenku.backend

import com.panenku.backend.config.SchedulingConfiguration
import com.panenku.backend.modules.pembeli.repository.OrderRepository
import com.panenku.backend.scheduler.ExpirationJob
import com.panenku.backend.scheduler.OrderExpirationService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor
import java.time.Clock
import java.util.function.Supplier

class SchedulerWiringTest {
    private val context = ApplicationContextRunner()
        .withUserConfiguration(SchedulingConfiguration::class.java, ExpirationJob::class.java)
        .withBean(OrderRepository::class.java, Supplier { mock(OrderRepository::class.java) })
        .withBean(OrderExpirationService::class.java, Supplier { mock(OrderExpirationService::class.java) })
        .withBean(Clock::class.java, Supplier { Clock.systemUTC() })
        .withPropertyValues("scheduler.expiration.initialDelay=3600000")

    @Test fun `enabled scheduler registers expiration task`() {
        context.withPropertyValues("scheduler.enabled=true").run { ctx ->
            assertNull(ctx.startupFailure)
            assertEquals(1, ctx.getBeansOfType(ExpirationJob::class.java).size)
            assertEquals(1, ctx.getBean(ScheduledAnnotationBeanPostProcessor::class.java).scheduledTasks.size)
        }
    }
    @Test fun `disabled scheduler registers no expiration job or scheduling processor`() {
        context.withPropertyValues("scheduler.enabled=false").run { ctx ->
            assertNull(ctx.startupFailure)
            assertTrue(ctx.getBeansOfType(ExpirationJob::class.java).isEmpty())
            assertTrue(ctx.getBeansOfType(ScheduledAnnotationBeanPostProcessor::class.java).isEmpty())
        }
    }
}
