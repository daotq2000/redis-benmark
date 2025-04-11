package com.spring.concurency.springconcurency;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/{email}")
    public ResponseEntity<UserEntity> findUser(@PathVariable String email) {
        var user = userService.findUser(email);
        return ResponseEntity.ok(user);
    }

}