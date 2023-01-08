package com.example.twitterexample.service;

import com.example.twitterexample.entity.Subscription;
import com.example.twitterexample.entity.User;
import com.example.twitterexample.repository.SubscriptionRepository;
import com.example.twitterexample.repository.TweetRepository;
import com.example.twitterexample.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubscriptionService {
    private final TweetRepository tweetRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;

    /**
     * @return int[]
     */
    public List<Long> getFollowerIds(Long authorId)
    {
        List<Subscription> subscriptions = getSubscriptionsByAuthorId(authorId);
        return subscriptions.stream().map(Subscription::getFollowerId).collect(Collectors.toList());
    }

    /**
     * @return User[]
     */
    public List<User> getFollowers(Long authorId)
    {
        List<Subscription> subscriptions = getSubscriptionsByAuthorId(authorId);

        return subscriptions.stream()
                .map(subscription -> userRepository.findById(subscription.getFollowerId()))
                .collect(Collectors.toList());
    }

    /**
     * @return int[]
     */
    public List<Long> getAuthorIds(Long followerId)
    {
        List<Subscription> subscriptions = getSubscriptionsByFollowerId(followerId);

        return subscriptions.stream().map(Subscription::getAuthorId).collect(Collectors.toList());
    }

    /**
     * @return User[]
     */
    public List<User> getAuthors(Long followerId)
    {
        List<Subscription> subscriptions = getSubscriptionsByFollowerId(followerId);


        return subscriptions.stream()
                .map(subscription -> userRepository.findById(subscription.getAuthorId()))
                .collect(Collectors.toList());
    }

    public boolean subscribe(Long authorId, Long followerId)
    {
        User author = userRepository.findById(authorId);
        if (author == null) {
            return false;
        }
        User follower = userRepository.findById(followerId);
        if (follower == null) {
            return false;
        }

        Subscription subscription = new Subscription();
        subscription.setAuthorId(authorId);
        subscription.setFollowerId(followerId);
        subscription.setCreatedAt(Instant.now());
        subscriptionRepository.create(subscription.getAuthorId(), subscription.getFollowerId(), subscription.getCreatedAt());

        return true;
    }

    /**
     * @return Subscription[]
     */
    private List<Subscription> getSubscriptionsByAuthorId(Long authorId)
    {
        User author = userRepository.findById(authorId);
        if (author == null) {
            return Collections.emptyList();
        }
        return subscriptionRepository.findByAuthor(authorId);
    }

    /**
     * @return Subscription[]
     */
    private List<Subscription> getSubscriptionsByFollowerId(Long followerId)
    {
        User follower = userRepository.findById(followerId);
        if (follower == null) {
            return Collections.emptyList();
        }
        return subscriptionRepository.findByFollower(followerId);
    }
}
