package com.zjgsu.courselens;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import java.net.*;
import java.net.http.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiTests {
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
    @org.springframework.test.context.DynamicPropertySource
    static void properties(org.springframework.test.context.DynamicPropertyRegistry r) {
        r.add("dependency.base-url", () -> "http://127.0.0.1:"+upstream.getAddress().getPort());
    }
    @AfterAll static void stop() { upstream.stop(0); }

    @Autowired CourseRepository repository;
    String body="{\"ownerId\":\"11111111-1111-1111-1111-111111111111\",\"title\":\"Demo\",\"sourceReference\":\"demo.srt\"}";
    @BeforeEach void clean() { repository.deleteAll(); upstreamStatus=200; slow=false; }
    @Test void successUsesHttpAndPersists() throws Exception {
        assertEquals(201,request("POST","/api/courses",body).statusCode());
        var item=repository.findAll().getFirst();
        assertEquals(200,request("GET","/api/courses/"+item.id,"").statusCode());
        assertEquals(1,repository.count());
    }
    @Test void missingDependencyDoesNotWrite() throws Exception {
        upstreamStatus=404;
        assertEquals(404,request("POST","/api/courses",body).statusCode());
        assertEquals(0,repository.count());
    }
    @Test void unavailableDependencyDoesNotWrite() throws Exception {
        upstreamStatus=500;
        assertEquals(503,request("POST","/api/courses",body).statusCode());
        assertEquals(0,repository.count());
    }
    @Test void timeoutDoesNotWrite() throws Exception {
        slow=true;
        assertEquals(503,request("POST","/api/courses",body).statusCode());
        assertEquals(0,repository.count());
    }
    @Test void invalidInputDoesNotWrite() throws Exception {
        assertEquals(400,request("POST","/api/courses","{}").statusCode());
        assertEquals(0,repository.count());
    }
    @Test void health() throws Exception { assertEquals(200,request("GET","/actuator/health","").statusCode()); }

}
