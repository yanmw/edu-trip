package com.cui.edu.vo.system;

import lombok.Data;

@Data
public class LoginVO {

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 登录类型
     * 例如：PC、小程序、app
     */
    private String loginType;
}
