package com.example.twitterexample.repository;

import com.example.twitterexample.entity.Tweet;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface TweetRepository {
    @Select("SELECT * FROM tweet WHERE author_id IN #{authorIds} ORDER BY created_at DESC LIMIT #{count}")
    List<Tweet> getByAuthorIds(@Param("authorIds") List<Long> authorIds, @Param("count") Integer count);

    @Insert("INSERT INTO tweet (author_id, text, created_at) VALUES(#{authorId}, #{text}, #{createdAt})")
    void create(@Param("authorId") Long authorId,
                @Param("text") String text,
                @Param("createdAt") Instant createdAt);

    @Select("SELECT * FROM tweet WHERE id = #{id}")
    Tweet findById(@Param("id") Long id);

    @Select("SELECT * FROM tweet WHERE author_id = #{authorId}")
    List<Tweet> findByAuthorId(@Param("authorId") Long authorId);

    @Delete("DELETE FROM tweet WHERE id = #{id} AND author_id = #{authorId}")
    void deleteByIdAndAuthorId(@Param("id") Long id, @Param("authorId") Long authorId);

    @Delete("DELETE FROM tweet WHERE id = #{id}")
    void deleteById(@Param("id") Long id);

    @Update("TRUNCATE TABLE tweet CASCADE")
    void truncateTable();
}
