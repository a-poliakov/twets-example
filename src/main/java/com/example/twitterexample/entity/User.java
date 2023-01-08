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
public class User {
    /**
     * @Mapping\Column(name="id", type="bigint", unique=true)
     * @Mapping\Id
     * @Mapping\GeneratedValue(strategy="IDENTITY")
     */
    private Long id;

    /**
     * @var string
     *
     * @Mapping\Column(type="string", length=32, nullable=false)
     */
    private String login;

    private Instant createdAt;
}
