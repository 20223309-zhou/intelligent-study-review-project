package com.zhou.review.utils;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * 封装redis缓存工具类
 */
@Component
@Slf4j
public class RedisCacheUtil {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    /**
     * 根据对象序列化存入redis
     * @param key
     * @param value
     * @param <T>
     */
    public <T> void setObject(final String key, final T value, final Integer timeOut, final TimeUnit timeUnit){
        String jsonStr = JSONUtil.toJsonStr(value);
        if (jsonStr == null) {
            log.warn("对象序列化为空: {}", key);
            return;
        }
        stringRedisTemplate.opsForValue().set(key, jsonStr,timeOut,timeUnit);
    }

    /**
     * 根据key获取对象
     * @param key key
     * @param clazz 对象类型
     * @return 对象
     */
    public <T> T getObject(final String key,Class<T> clazz){
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        if(jsonStr == null || StringUtils.isEmpty(jsonStr)){
            log.warn("从Redis获取对象为空: {}", key);
            return null;
        }
        return JSONUtil.toBean(jsonStr, clazz);
    }

    public String getObject(final String key){
        String value = stringRedisTemplate.opsForValue().get(key);
        if(value == null || StringUtils.isEmpty(value)){
            log.warn("从Redis获取对象为空: {}", key);
            return null;
        }
        return value;
    }

    public <T> T getObject(final String key, TypeReference<T> typeReference){
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        if(jsonStr == null || StringUtils.isEmpty(jsonStr)){
            log.warn("从Redis获取对象为空: {}", key);
            return null;
        }
        // 先解析为 JSON 对象，再通过 toBean(TypeReference) 转换
        return JSONUtil.parse(jsonStr).toBean(typeReference);
    }

    /**
     * 删除单个对象
     *
     * @param key
     */
    public boolean deleteObject(final String key)
    {
        return stringRedisTemplate.delete(key);
    }
}
