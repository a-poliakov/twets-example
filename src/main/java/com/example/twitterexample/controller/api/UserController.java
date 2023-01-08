package com.example.twitterexample.controller.api;

import com.example.twitterexample.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping
    public ResponseEntity<Long> addUserAction(@RequestParam("login") String login) {
        Long userId = userService.saveUser(login);
        if (userId == null) {
            return ResponseEntity.status(400).build();
        }
        return ResponseEntity.status(200).body(userId);
    }
}
