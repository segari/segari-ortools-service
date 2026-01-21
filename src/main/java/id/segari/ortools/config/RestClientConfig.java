package id.segari.ortools.config;

import id.segari.ortools.error.SegariRoutingErrors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Value("${osrm.base-url}")
    private String osrmBaseUrl;

    @Bean
    public ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return factory;
    }

    @Bean(name = "osrmRestClient")
    public RestClient osrmRestClient(RestClient.Builder builder, ClientHttpRequestFactory requestFactory) {
        return builder
                .baseUrl(osrmBaseUrl)
                .requestFactory(requestFactory)
                .defaultStatusHandler(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        (request, response) -> {
                            throw SegariRoutingErrors.osrmApiError(response.getStatusCode().toString());
                        }
                )
                .build();
    }
}
