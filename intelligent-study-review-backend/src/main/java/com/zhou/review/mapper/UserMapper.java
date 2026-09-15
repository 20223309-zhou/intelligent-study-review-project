package com.zhou.review.mapper;

import com.zhou.review.model.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 用户 Mapper 接口
 * </p>
 *
 * @author zhou
 * @since 2026-06-23
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

}
