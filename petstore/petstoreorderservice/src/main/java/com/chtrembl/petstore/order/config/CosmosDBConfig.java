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
//                .key("iNV4Fd84ubJeVukLSkUTTC6yYdWC95o69hn0EeZt3649YQhFeqEkjju1a5BnFq27QLYc7yuoftY5ACDbRoaaBw==")
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
