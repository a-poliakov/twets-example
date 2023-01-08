package com.example.twitterexample.controller.cmd;

import com.example.twitterexample.service.SubscriptionService;
import com.example.twitterexample.service.TweetService;
import com.example.twitterexample.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequestMapping("/cmd/v1/send-tweets")
@RequiredArgsConstructor
public class SendTweetsCommand {
    public static final int OK = 0;
    public static final int GENERAL_ERROR = 1;

    private final UserService userService;
    private final TweetService tweetService;

    @PostMapping
    public int execute(@RequestParam("authorId") Long authorId, @RequestParam("count") Integer count) {
        if (!userService.userExists(authorId)) {
            log.error("<error>User with ID $authorId doesn't exist</error>\n");
            return GENERAL_ERROR;
        }
        if (count < 0) {
            log.error("<error>Count should be positive integer</error>\n");
            return GENERAL_ERROR;
        }
        int savedTweets = 0;
        for (int i = 0; i < count; i++) {
            try {
                tweetService.saveTweet(authorId, "Sample tweet from #." + authorId);
                savedTweets++;
            } catch (Throwable e) {
                log.error("<error>Tweet #{} couldn't be created</error>\n", i);
            }
        }
        log.info("<info>{} tweets were created</info>\n", savedTweets);
        return OK;
    }
}
