package com.example.activation.utils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class LogBuilderTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(LogBuilderTest.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void writesMessageFollowedByFieldsInInsertionOrder() {
        UUID requestId = UUID.randomUUID();

        LogBuilder.info(logger, "Activation request created")
                .with("requestId", requestId)
                .with("externalId", "EXT-1")
                .log();

        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage())
                .isEqualTo("Activation request created requestId=" + requestId + " externalId=EXT-1");
    }

    @Test
    void exposesFieldsAsKeyValuePairsForStructuredLogs() {
        LogBuilder.warn(logger, "Provider call failed")
                .with("requestId", "abc")
                .with("attempts", 2)
                .log();

        assertThat(appender.list.getFirst().getKeyValuePairs())
                .extracting(pair -> pair.key, pair -> pair.value)
                .containsExactly(tuple("requestId", "abc"), tuple("attempts", 2));
    }

    @Test
    void attachesException() {
        IllegalStateException failure = new IllegalStateException("boom");

        LogBuilder.error(logger, "Unexpected error").exception(failure).log();

        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getMessage()).isEqualTo("Unexpected error");
        assertThat(event.getThrowableProxy().getMessage()).isEqualTo("boom");
    }

    @Test
    void writesNullValues() {
        LogBuilder.info(logger, "Provider result applied").with("lastError", null).log();

        assertThat(appender.list.getFirst().getFormattedMessage()).isEqualTo("Provider result applied lastError=null");
    }

    @Test
    void skipsDisabledLevels() {
        LogBuilder.debug(logger, "Processing cycle finished").with("processed", 1).log();

        assertThat(appender.list).isEmpty();
    }
}
