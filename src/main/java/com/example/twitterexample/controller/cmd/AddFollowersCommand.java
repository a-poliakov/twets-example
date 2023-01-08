package com.example.twitterexample.controller.cmd;

import com.example.twitterexample.service.SubscriptionService;
import com.example.twitterexample.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequestMapping("/cmd/v1/add-followers")
@RequiredArgsConstructor
public class AddFollowersCommand {
    public static final int OK = 0;
    public static final int GENERAL_ERROR = 1;

    private final UserService userService;
    private final SubscriptionService subscriptionService;

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
        int createdFollowers = 0;
        for (int i = 0; i < count; i++) {
            try {
               Long userId = userService.saveUser("Reader #" + authorId + "." + i);
                if (userId != null) {
                    subscriptionService.subscribe(authorId, userId);
                    createdFollowers++;
                }
            } catch (Throwable e) {
                log.error("<error>User #{} couldn't be created</error>\n", i);
            }
        }
        log.info("<info>{} followers were created</info>\n", createdFollowers);
        return OK;
    }
}
