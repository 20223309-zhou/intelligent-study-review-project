package com.zhou.review.model.dto.user;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserLoginRequest implements Serializable {
    /**
     * 用户账号
     */
    private String userAccount;

    /**
     * 用户密码
     */
    private String userPassword;

    /**
     * 验证码key
     */
    private String captchaKey;

    /**
     * 验证码
     */
    private String captchaCode;
}
