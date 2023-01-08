package com.example.twitterexample.service;

import com.example.twitterexample.entity.Tweet;
import com.example.twitterexample.entity.User;
import com.example.twitterexample.repository.TweetRepository;
import com.example.twitterexample.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TweetService {
    private final TweetRepository tweetRepository;
    private final UserRepository userRepository;

    public boolean saveTweet(Long authorId, String text){
        Tweet tweet = new Tweet();
        User author = userRepository.findById(authorId);
        tweet.setAuthorId(author.getId());
        tweet.setText(text);
        tweet.setCreatedAt(Instant.now());
        tweetRepository.create(tweet.getAuthorId(), tweet.getText(), tweet.getCreatedAt());
        return true;
    }

    /**
     * @param authorIds authorIds
     *
     * @return List<Tweet>
     */
    public List<Tweet> getFeed(List<Long> authorIds, int $count) {
        return tweetRepository.getByAuthorIds(authorIds, $count);
    }
}
