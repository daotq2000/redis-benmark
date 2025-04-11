package com.spring.concurency.springconcurency;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/emails")
public class EmailCheckerController {

    @Autowired
    private com.spring.concurency.springconcurency.EmailCheckerService emailCheckerService;

    @GetMapping("/check")
    public ResponseEntity<String> checkEmail(@RequestParam String email) {
        boolean isRegistered = emailCheckerService.isEmailRegistered(email);
        return ResponseEntity.ok("Email '" + email + "' is " + (isRegistered ? "registered" : "not registered"));
    }

    @PostMapping("/register")
    public ResponseEntity<String> registerEmail(@RequestParam String email) {
        boolean registered = emailCheckerService.registerEmail(email);
        if (registered) {
            return ResponseEntity.status(HttpStatus.CREATED).body("Email '" + email + "' registered successfully");
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Email '" + email + "' is already registered");
        }
    }

    @PostMapping("/dump-users")
    public ResponseEntity<String> dumpUsers() {
        String result = emailCheckerService.dumpUsersToPostgres();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/load-redis")
    public ResponseEntity<String> loadRedis() {
        String result = emailCheckerService.loadUsersToRedis();
        return ResponseEntity.ok(result);
    }
}