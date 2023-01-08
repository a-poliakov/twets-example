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
public class Tweet {
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
     * @var string
     *
     * @Mapping\Column(type="string", length=140, nullable=false)
     */
    private String text;
    private Instant createdAt;
}
