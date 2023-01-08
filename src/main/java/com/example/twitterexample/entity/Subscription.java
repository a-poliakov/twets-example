package com.example.twitterexample.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Subscription {
    private Long id;

    /**
     * @var User
     *
     * @Mapping\ManyToOne(targetEntity="User")
     * @Mapping\JoinColumns({
     *   @Mapping\JoinColumn(name="author_id", referencedColumnName="id")
     * })
     */
    private Long authorId;

    /**
     * @var User
     *
     * @Mapping\ManyToOne(targetEntity="User")
     * @Mapping\JoinColumns({
     *   @Mapping\JoinColumn(name="follower_id", referencedColumnName="id")
     * })
     */
    private Long followerId;

    private Instant createdAt;
}
