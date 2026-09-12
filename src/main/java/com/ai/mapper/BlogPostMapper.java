package com.ai.mapper;

import com.mybatisflex.core.BaseMapper;
import com.ai.model.entity.BlogPost;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface BlogPostMapper extends BaseMapper<BlogPost> {

    @Update("UPDATE blog_post SET view_count = view_count + 1 WHERE id = #{id}")
    int incrementViewCount(@Param("id") Long id);

    @Update("UPDATE blog_post SET like_count = like_count + 1 WHERE id = #{id}")
    int incrementLikeCount(@Param("id") Long id);
}
