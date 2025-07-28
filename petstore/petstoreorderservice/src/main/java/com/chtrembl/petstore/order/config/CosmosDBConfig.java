package com.chtrembl.petstore.order.config;

import com.azure.spring.data.cosmos.core.CosmosTemplate;
import com.azure.spring.data.cosmos.config.CosmosConfig;
import com.azure.spring.data.cosmos.core.convert.MappingCosmosConverter;
import com.azure.spring.data.cosmos.core.mapping.CosmosMappingContext;
import com.azure.cosmos.CosmosClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CosmosDBConfig {
//    @Bean
//    public CosmosTemplate cosmosTemplate(CosmosConfig cosmosConfig, CosmosClientBuilder cosmosClientBuilder) {
//        return new CosmosTemplate(cosmosClientBuilder.buildAsyncClient(),"petOderDB", cosmosConfig,new MappingCosmosConverter());
//    }
//
//    @Bean
//    public CosmosClientBuilder cosmosClientBuilder() {
//        return new CosmosClientBuilder()
//                .key("")
//                .endpoint("https://demo-cosmos-db.documents.azure.com:443/");
//    }
//
//    @Bean
//    public CosmosConfig cosmosConfig() {
//        return CosmosConfig.builder()
//                .enableQueryMetrics(true)
//                .build();
//    }
}
