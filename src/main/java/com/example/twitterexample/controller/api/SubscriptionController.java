package com.example.twitterexample.controller.api;

import com.example.twitterexample.entity.User;
import com.example.twitterexample.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subscription")
@RequiredArgsConstructor
public class SubscriptionController {
    private final SubscriptionService subscriptionService;

    @GetMapping("/list-by-author")
    public ResponseEntity<List<User>> listSubscriptionByAuthorAction(@RequestParam("authorId") Long authorId) {
        List<User> followers = subscriptionService.getFollowers(authorId);
        int code = CollectionUtils.isEmpty(followers) ? 204 : 200;
        return ResponseEntity.status(code).body(followers);
    }

    @GetMapping("/list-by-follower")
    public ResponseEntity<List<User>>  listSubscriptionByFollowerAction(@RequestParam("followerId") Long followerId) {
        List<User> authors = subscriptionService.getAuthors(followerId);
        int code = CollectionUtils.isEmpty(authors) ? 204 : 200;
        return ResponseEntity.status(code).body(authors);
    }

    @PostMapping
    public ResponseEntity<Void> subscribeAction(@RequestParam("authorId") Long authorId, @RequestParam("followerId") Long followerId) {
        boolean success = subscriptionService.subscribe(authorId, followerId);
        int code = success ? 200 : 400;
        return ResponseEntity.status(code).build();
    }
}
