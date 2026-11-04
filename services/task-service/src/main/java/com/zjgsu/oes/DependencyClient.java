package com.zjgsu.oes;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Component
public class DependencyClient {
    private static final Logger log = LoggerFactory.getLogger(DependencyClient.class);
    private final DiscoveryClient discovery;
    private final String serviceName;
    private final RestTemplate http;

    public DependencyClient(DiscoveryClient discovery,
            @Value("${dependency.service-name}") String serviceName,
            @Value("${dependency.connect-timeout-ms:1500}") int connect,
            @Value("${dependency.read-timeout-ms:2000}") int read) {
        this.discovery = discovery;
        this.serviceName = serviceName;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connect);
        factory.setReadTimeout(read);
        this.http = new RestTemplate(factory);
    }

    public void require(String path) {
        ServiceInstance instance;
        try {
            // 每次业务操作都读取当前已知实例，不把某个地址永久保存。
            var instances = discovery.getInstances(serviceName);
            if (instances == null || instances.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "No available instance for " + serviceName);
            }
            instance = instances.getFirst();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Service discovery unavailable", e);
        }

        URI uri = URI.create(instance.getUri().toString().replaceAll("/+$", "") + path);
        // 只记录服务名及实例地址，方便截图，不记录凭据。
        log.info("Discovered service={} uri={}", serviceName, uri);
        try {
            var response = http.getForEntity(uri, String.class);
            if (!response.getStatusCode().is2xxSuccessful()
                    || response.getBody() == null || response.getBody().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Invalid dependency response");
            }
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Dependency resource does not exist");
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Dependency unavailable or timed out");
        }
    }
}
