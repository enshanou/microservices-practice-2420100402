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

    @Autowired UserRepository repository;
    @BeforeEach void clean() { repository.deleteAll(); }
    @Test void createAndReadUser() throws Exception {
        assertEquals(201,request("POST","/api/users","{\"displayName\":\"Student\"}").statusCode());
        var u=repository.findAll().getFirst();
        assertTrue(request("GET","/api/users/"+u.id,"").body().contains("Student"));
        assertEquals(1,repository.count());
    }
    @Test void rejectsInvalidAndMissing() throws Exception {
        assertEquals(400,request("POST","/api/users","{\"displayName\":\" \"}").statusCode());
        assertEquals(404,request("GET","/api/users/missing","").statusCode());
        assertEquals(0,repository.count());
    }
    @Test void health() throws Exception { assertEquals(200,request("GET","/actuator/health","").statusCode()); }
}
