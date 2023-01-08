package com.example.twitterexample.repository;

import com.example.twitterexample.entity.Tweet;
import com.example.twitterexample.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;
@Mapper
public interface UserRepository {
    @Select("SELECT * FROM \"user\" WHERE id = #{id}")
    User findById(@Param("id") Long id);

    @Insert("INSERT INTO \"user\" (login, created_at) VALUES(#{login}, #{createdAt})")
    void create(@Param("login") String login,
                @Param("createdAt") Instant createdAt);
}
