package com.zjgsu.courselens;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
@Component
public class DependencyClient {
    private final RestTemplate http;
    private final String base;
    public DependencyClient(@Value("${dependency.base-url}") String base,
            @Value("${dependency.connect-timeout-ms:1500}") int connect,
            @Value("${dependency.read-timeout-ms:2000}") int read) {
        this.base = base.replaceAll("/+$", "");
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connect);
        factory.setReadTimeout(read);
        this.http = new RestTemplate(factory);
    }
    public void require(String path) {
        try {
            var response = http.getForEntity(URI.create(base + path), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid dependency response");
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dependency resource does not exist");
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Dependency unavailable or timed out");
        }
    }
}
