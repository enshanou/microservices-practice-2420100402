package com.zjgsu.oes;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import java.net.*;
import java.net.http.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.context.annotation.Import(ApiTests.DiscoveryTestConfiguration.class)
class ApiTests {
    static volatile boolean noInstances=false;
    @LocalServerPort int port;
    HttpResponse<String> request(String method,String path,String body) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json");
        return HttpClient.newHttpClient().send(b.method(method,HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }

    static volatile int upstreamStatus=200;
    static volatile boolean slow=false;
    static final com.sun.net.httpserver.HttpServer upstream;
    static {
        try {
            upstream=com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            upstream.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
            upstream.createContext("/api/", ex -> {
                try {
                    if(slow) Thread.sleep(600);
                    var bytes="{\"id\":\"11111111-1111-1111-1111-111111111111\"}".getBytes();
                    ex.sendResponseHeaders(upstreamStatus,bytes.length); ex.getResponseBody().write(bytes);
                } catch(Exception ignored) { } finally { ex.close(); }
            });
            upstream.start();
        } catch(Exception e) { throw new ExceptionInInitializerError(e); }
    }
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class DiscoveryTestConfiguration {
        @org.springframework.context.annotation.Bean
        org.springframework.cloud.client.discovery.DiscoveryClient discoveryClientForTest() {
            return new org.springframework.cloud.client.discovery.DiscoveryClient() {
                public String description() { return "Local fake discovery"; }
                public java.util.List<String> getServices() {
                    return java.util.List.of("user-service", "course-service");
                }
                public java.util.List<org.springframework.cloud.client.ServiceInstance>
                        getInstances(String serviceId) {
                    if (noInstances) return java.util.List.of();
                    return java.util.List.of(
                        new org.springframework.cloud.client.DefaultServiceInstance(
                            "local-test", serviceId, "127.0.0.1",
                            upstream.getAddress().getPort(), false));
                }
            };
        }
    }
    @AfterAll static void stop() { upstream.stop(0); }

    @Autowired TaskRepository repository;
    String body="{\"courseId\":\"11111111-1111-1111-1111-111111111111\"}";
    @BeforeEach void clean() { repository.deleteAll(); upstreamStatus=200; slow=false; noInstances=false; }
    @Test void successUsesHttpAndPersists() throws Exception {
        assertEquals(201,request("POST","/api/tasks",body).statusCode());
        var item=repository.findAll().getFirst();
        assertEquals(200,request("GET","/api/tasks/"+item.id,"").statusCode());
        assertEquals(1,repository.count());
    }
    @Test void missingDependencyDoesNotWrite() throws Exception {
        upstreamStatus=404;
        assertEquals(404,request("POST","/api/tasks",body).statusCode());
        assertEquals(0,repository.count());
    }
    @Test void unavailableDependencyDoesNotWrite() throws Exception {
        upstreamStatus=500;
        assertEquals(503,request("POST","/api/tasks",body).statusCode());
        assertEquals(0,repository.count());
    }
    @Test void timeoutDoesNotWrite() throws Exception {
        slow=true;
        assertEquals(503,request("POST","/api/tasks",body).statusCode());
        assertEquals(0,repository.count());
    }
    @Test void invalidInputDoesNotWrite() throws Exception {
        assertEquals(400,request("POST","/api/tasks","{}").statusCode());
        assertEquals(0,repository.count());
    }
    @Test void health() throws Exception { assertEquals(200,request("GET","/actuator/health","").statusCode()); }

    @Test void duplicateAndTransitions() throws Exception {
        assertEquals(201,request("POST","/api/tasks",body).statusCode());
        assertEquals(409,request("POST","/api/tasks",body).statusCode());
        var id=repository.findAll().getFirst().id;
        assertEquals(409,request("PATCH","/api/tasks/"+id+"/status","{\"status\":\"SUCCEEDED\"}").statusCode());
        assertEquals(200,request("PATCH","/api/tasks/"+id+"/status","{\"status\":\"RUNNING\"}").statusCode());
        assertEquals(400,request("PATCH","/api/tasks/"+id+"/status","{\"status\":\"FAILED\"}").statusCode());
        assertEquals(200,request("PATCH","/api/tasks/"+id+"/status","{\"status\":\"SUCCEEDED\"}").statusCode());
        assertEquals(201,request("POST","/api/tasks",body).statusCode());
    }
    @Test void failureReleasesActiveSlot() throws Exception {
        request("POST","/api/tasks",body);
        var id=repository.findAll().getFirst().id;
        request("PATCH","/api/tasks/"+id+"/status","{\"status\":\"RUNNING\"}");
        assertEquals(200,request("PATCH","/api/tasks/"+id+"/status","{\"status\":\"FAILED\",\"failureReason\":\"Bad subtitle\"}").statusCode());
        assertNull(repository.findById(id).orElseThrow().activeCourseId);
        assertEquals(201,request("POST","/api/tasks",body).statusCode());
    }


    @Test void noInstancesDoesNotWrite() throws Exception {
        noInstances=true;
        assertEquals(503,request("POST","/api/tasks",body).statusCode());
        assertEquals(0,repository.count());
    }
    @Test void discoveryIsReadAgainForNextOperation() throws Exception {
        assertEquals(201,request("POST","/api/tasks",body).statusCode());
        noInstances=true;
        assertEquals(503,request("POST","/api/tasks",body).statusCode());
        assertEquals(1,repository.count());
    }
}
