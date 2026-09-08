package com.weather.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.weather.entity.SysUser;

public interface SysUserService extends IService<SysUser> {
    SysUser findByUsername(String username);
    boolean register(SysUser user);
    boolean updateUser(SysUser user);
    boolean changePassword(Long userId, String oldPassword, String newPassword);
}
