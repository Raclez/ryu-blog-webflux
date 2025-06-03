package com.ryu.blog.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * R2DBC配置类
 * @author ryu
 */
@Configuration
@EnableR2dbcRepositories(basePackages = "com.ryu.blog.repository")
@EnableTransactionManagement
public class R2dbcConfig extends AbstractR2dbcConfiguration {

    private final ConnectionFactory connectionFactory;

    public R2dbcConfig(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public ConnectionFactory connectionFactory() {
        return connectionFactory;
    }

    @Bean
    public ReactiveTransactionManager transactionManager(ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
    }

    @Override
    protected List<Object> getCustomConverters() {
        List<Object> converters = new ArrayList<>();
        converters.add(new LocalDateTimeToMysqlDatetimeConverter());
        converters.add(new LongToLocalDateTimeConverter());
        converters.add(new StringToLocalDateTimeConverter());
        return converters;
    }

    /**
     * LocalDateTime转MySQL datetime兼容格式的转换器
     */
    @WritingConverter
    static class LocalDateTimeToMysqlDatetimeConverter implements Converter<LocalDateTime, String> {
        private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        @Override
        public String convert(LocalDateTime source) {
            return source == null ? null : source.format(DATETIME_FORMATTER);
        }
    }

    /**
     * MySQL datetime字符串转LocalDateTime的转换器
     */
    @ReadingConverter
    static class StringToLocalDateTimeConverter implements Converter<String, LocalDateTime> {
        private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        @Override
        public LocalDateTime convert(String source) {
            return source == null ? null : LocalDateTime.parse(source, DATETIME_FORMATTER);
        }
    }

    /**
     * Long转LocalDateTime的转换器
     */
    @ReadingConverter
    static class LongToLocalDateTimeConverter implements Converter<Long, LocalDateTime> {
        @Override
        public LocalDateTime convert(Long source) {
            return source == null ? null : LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(source), ZoneOffset.of("+8"));
        }
    }
} 