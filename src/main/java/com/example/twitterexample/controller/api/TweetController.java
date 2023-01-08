package com.example.twitterexample.controller.api;

import com.example.twitterexample.entity.Tweet;
import com.example.twitterexample.service.SubscriptionService;
import com.example.twitterexample.service.TweetService;
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
@RequestMapping("/api/v1/tweet")
@RequiredArgsConstructor
public class TweetController {
    private static final Integer DEFAULT_FEED_SIZE = 20;

    private final TweetService tweetService;
    private final SubscriptionService subscriptionService;


    @PostMapping
    public ResponseEntity<Void> postTweetAction(@RequestParam("authorId") Long authorId, @RequestParam("text") String text) {
        boolean success = tweetService.saveTweet(authorId, text);
        int code = success ? 201 : 400;
        return ResponseEntity.status(code).build();
    }

    @GetMapping("/feed")
    public ResponseEntity<List<Tweet>> getFeedAction(@RequestParam("userId") Long userId, @RequestParam("count") Integer count) {
        count = count == null ? DEFAULT_FEED_SIZE : count;
        List<Long> authorIds = subscriptionService.getAuthorIds(userId);
        List<Tweet> tweets = tweetService.getFeed(authorIds, count);
        int code = CollectionUtils.isEmpty(tweets) ? 204 : 200;
        return ResponseEntity.status(code).body(tweets);
    }
}
