package com.ai.mapper;

import com.mybatisflex.core.BaseMapper;
import com.ai.model.entity.BlogTag;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface BlogTagMapper extends BaseMapper<BlogTag> {

    @Update("UPDATE blog_tag SET count = count + 1 WHERE id = #{id}")
    int incrementCount(@Param("id") Long id);

    @Update("UPDATE blog_tag SET count = count - 1 WHERE id = #{id} AND count > 0")
    int decrementCount(@Param("id") Long id);
}
