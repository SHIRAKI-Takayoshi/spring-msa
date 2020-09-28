package com.example.account;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.proxy.ProxyConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.boot.autoconfigure.r2dbc.ConnectionFactoryBuilder;
import org.springframework.boot.autoconfigure.r2dbc.EmbeddedDatabaseConnection;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
public class AccountConfiguration {

    @Bean
    ConnectionFactory connectionFactory(R2dbcProperties properties, ResourceLoader resourceLoader) {
        ConnectionFactory original = ConnectionFactoryBuilder
                .of(properties, () -> EmbeddedDatabaseConnection.get(resourceLoader.getClassLoader())).build();
        ConnectionFactory proxyConnectionFactory
                = ProxyConnectionFactory.builder(original).listener(new TracingExecutionListener()).build();

//        return proxyConnectionFactory;

        R2dbcProperties.Pool pool = properties.getPool();
        ConnectionPoolConfiguration.Builder builder
                = ConnectionPoolConfiguration.builder(proxyConnectionFactory)
                .maxSize(pool.getMaxSize()).initialSize(pool.getInitialSize()).maxIdleTime(pool.getMaxIdleTime());
        if (StringUtils.hasText(pool.getValidationQuery())) {
            builder.validationQuery(pool.getValidationQuery());
        }
        return new ConnectionPool(builder.build());

    }
}
