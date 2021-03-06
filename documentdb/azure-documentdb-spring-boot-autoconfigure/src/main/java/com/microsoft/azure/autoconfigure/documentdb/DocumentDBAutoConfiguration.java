/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */

package com.microsoft.azure.autoconfigure.documentdb;

import com.microsoft.azure.documentdb.ConnectionPolicy;
import com.microsoft.azure.documentdb.ConsistencyLevel;
import com.microsoft.azure.documentdb.DocumentClient;
import com.microsoft.azure.spring.common.GetHashMac;
import com.microsoft.azure.spring.data.documentdb.DocumentDbFactory;
import com.microsoft.azure.spring.data.documentdb.core.DocumentDbTemplate;
import com.microsoft.azure.spring.data.documentdb.core.convert.MappingDocumentDbConverter;
import com.microsoft.azure.spring.data.documentdb.core.mapping.DocumentDbMappingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScanner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.data.annotation.Persistent;

@Configuration
@ConditionalOnClass({DocumentClient.class, DocumentDbTemplate.class})
@ConditionalOnMissingBean(type =
        {"com.microsoft.azure.spring.data.documentdb.DocumentDbFactory",
                "com.microsoft.azure.documentdb.DocumentClient"})
@EnableConfigurationProperties(DocumentDBProperties.class)
public class DocumentDBAutoConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(DocumentDBAutoConfiguration.class);
    private static final String USER_AGENT_SUFFIX = "spring-boot-starter/0.1.6-beta";

    private final DocumentDBProperties properties;
    private final ConnectionPolicy connectionPolicy;
    private final ApplicationContext applicationContext;

    public DocumentDBAutoConfiguration(DocumentDBProperties properties,
                                       ObjectProvider<ConnectionPolicy> connectionPolicyObjectProvider,
                                       ApplicationContext applicationContext) {
        this.properties = properties;
        this.connectionPolicy = connectionPolicyObjectProvider.getIfAvailable();
        this.applicationContext = applicationContext;
    }

    @Bean
    @Scope("prototype")
    @ConditionalOnMissingBean
    public DocumentClient documentClient() {
        return createDocumentClient();
    }

    private DocumentClient createDocumentClient() {
        LOG.debug("createDocumentClient");
        final ConnectionPolicy policy = connectionPolicy == null ? ConnectionPolicy.GetDefault() : connectionPolicy;

        String userAgent = (policy.getUserAgentSuffix() == null ? "" : ";" + policy.getUserAgentSuffix()) +
                ";" + USER_AGENT_SUFFIX;

        if (properties.isAllowTelemetry() && GetHashMac.getHashMac() != null) {
            userAgent += ";" + GetHashMac.getHashMac();
        }
        policy.setUserAgentSuffix(userAgent);

        return new DocumentClient(properties.getUri(), properties.getKey(), policy,
                properties.getConsistencyLevel() == null ?
                        ConsistencyLevel.Session : properties.getConsistencyLevel());
    }

    @Bean
    @ConditionalOnMissingBean
    public DocumentDbFactory documentDbFactory() {
        return new DocumentDbFactory(this.documentClient());
    }

    @Bean
    @ConditionalOnMissingBean
    public DocumentDbTemplate documentDbTemplate() {
        return new DocumentDbTemplate(this.documentDbFactory(),
                mappingDocumentDbConverter(),
                properties.getDatabase());
    }

    @Bean
    @ConditionalOnMissingBean
    public DocumentDbMappingContext documentDbMappingContext() {
        try {
            final DocumentDbMappingContext documentDbMappingContext = new DocumentDbMappingContext();
            documentDbMappingContext.setInitialEntitySet(new EntityScanner(this.applicationContext)
                    .scan(Persistent.class));

            return documentDbMappingContext;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public MappingDocumentDbConverter mappingDocumentDbConverter() {
        return new MappingDocumentDbConverter(documentDbMappingContext());
    }
}
