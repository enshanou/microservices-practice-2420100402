package com.zjgsu.courselens;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class UserService {
    private final UserRepository users;
    public UserService(UserRepository users) { this.users=users; }
    public User create(String displayName) {
        var u=new User(); u.id=UUID.randomUUID().toString(); u.displayName=displayName.strip(); return users.saveAndFlush(u);
    }
    public List<User> list() { return users.findAll(); }
    public User get(String id) { return users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"User does not exist")); }
}
