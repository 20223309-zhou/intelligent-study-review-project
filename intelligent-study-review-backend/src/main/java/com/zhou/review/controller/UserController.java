package com.zhou.review.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.wf.captcha.SpecCaptcha;
import com.zhou.review.annotation.AuthCheck;
import com.zhou.review.common.BaseResponse;
import com.zhou.review.common.DeleteRequest;
import com.zhou.review.common.ResultUtils;
import com.zhou.review.constant.UserConstant;
import com.zhou.review.exception.BusinessException;
import com.zhou.review.exception.ErrorCode;
import com.zhou.review.model.dto.user.UserAddRequest;
import com.zhou.review.model.dto.user.UserLoginRequest;
import com.zhou.review.model.dto.user.UserRegisterRequest;
import com.zhou.review.model.entity.User;
import com.zhou.review.model.vo.LoginUserVO;
import com.zhou.review.service.UserService;
import com.zhou.review.utils.RedisCacheUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 用户 前端控制器
 * </p>
 *
 * @author zhou
 * @since 2026-06-23
 */
@RestController
@RequestMapping("/user")
public class UserController {
    @Resource
    private UserService userService;

    @Resource
    private RedisCacheUtil redisCacheUtil;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        if (userRegisterRequest == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        long result = userService.userRegister(userAccount, userPassword, checkPassword);
        return ResultUtils.success(result);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        if (userLoginRequest == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        LoginUserVO loginUserVO;
        // 获取验证码key
        String captchaKey = userLoginRequest.getCaptchaKey();
        String captchaCode = userLoginRequest.getCaptchaCode();
        try {
            if (captchaKey == null || captchaKey.isEmpty() || captchaCode == null || captchaCode.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码key不能为空");
            }
            // 从redis中获取验证码
            String cacheCode = redisCacheUtil.getObject("captcha:" + captchaKey);
            if(cacheCode == null){
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码已过期");
            }
            if (!Objects.equals(cacheCode.toLowerCase(), captchaCode.toLowerCase())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码错误");
            }
            String userAccount = userLoginRequest.getUserAccount();
            String userPassword = userLoginRequest.getUserPassword();
            loginUserVO = userService.userLogin(userAccount, userPassword, request);
            redisCacheUtil.deleteObject("captcha:" + captchaKey);
        } finally {
            redisCacheUtil.deleteObject("captcha:" + captchaKey);
        }
        return ResultUtils.success(loginUserVO);
    }

    /**
     * 生成验证码
     * @return
     */
    @PostMapping("/getCaptcha")
    public BaseResponse<Map<String, String>> getCaptcha(@RequestParam(required = false) String captchaKey) {
        // 删除旧验证码
        if (captchaKey != null){
            redisCacheUtil.deleteObject("captcha:" + captchaKey);
        }
        SpecCaptcha captcha = new SpecCaptcha(130, 48, 4);
        String genCaptchaKey = IdUtil.fastSimpleUUID();
        redisCacheUtil.setObject(
                "captcha:" + genCaptchaKey, captcha.text(), 5, TimeUnit.MINUTES);
        return ResultUtils.success(Map.of(
                "captchaKey", genCaptchaKey,
                "captchaImage", captcha.toBase64()
        ));
    }

    /**
     * 获取当前登录用户
     */
    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userService.getLoginUserVO(loginUser));
    }

    /**
     * 用户注销
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        if (request == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }

    /**
     * 创建用户（管理员）
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        if (userAddRequest == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = new User();
        BeanUtil.copyProperties(userAddRequest, user);
        // 默认密码 12345678
        final String DEFAULT_PASSWORD = "12345678";
        String encryptPassword = userService.getEncryptPassword(DEFAULT_PASSWORD);
        user.setUserPassword(encryptPassword);
        boolean result = userService.save(user);
        if (!result){
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
        return ResultUtils.success(user.getId());
    }

    /**
     * 删除用户（管理员）
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User deletedUser = userService.getById(deleteRequest.getId());
        if(deletedUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }
        if (deletedUser.getUserRole().equals(UserConstant.ADMIN_ROLE)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "不能删除管理员");
        }
        boolean b = userService.removeById(deleteRequest.getId());
        if (!b) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "删除失败");
        }
        return ResultUtils.success(b);
    }

}
