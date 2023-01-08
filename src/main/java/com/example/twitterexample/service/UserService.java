package com.example.twitterexample.service;

import com.example.twitterexample.entity.Tweet;
import com.example.twitterexample.entity.User;
import com.example.twitterexample.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public Long saveUser(String login) {
        User user = new User();
        user.setLogin(login);
        user.setCreatedAt(Instant.now());
        userRepository.create(user.getLogin(), user.getCreatedAt());
        return user.getId();
    }

    public boolean userExists(Long userId) {
        return userRepository.findById(userId) != null;
    }
}
