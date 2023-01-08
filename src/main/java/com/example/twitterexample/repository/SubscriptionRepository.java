package com.example.twitterexample.repository;

import com.example.twitterexample.entity.Subscription;
import com.example.twitterexample.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

@Mapper
public interface SubscriptionRepository {
    @Select("SELECT * FROM subscription WHERE id = #{id}")
    Subscription findById(@Param("id") Long id);

    @Insert("INSERT INTO subscription (author_id, follower_id, created_at) VALUES(#{authorId}, , #{followerId}, #{createdAt})")
    void create(@Param("authorId") Long authorId,
                @Param("followerId") Long followerId,
                @Param("createdAt") Instant createdAt);

    @Select("SELECT * FROM subscription WHERE author_id = #{authorId}")
    List<Subscription> findByAuthor(@Param("authorId") Long authorId);

    @Select("SELECT * FROM subscription WHERE follower_id = #{followerId}")
    List<Subscription> findByFollower(@Param("followerId") Long followerId);
}
