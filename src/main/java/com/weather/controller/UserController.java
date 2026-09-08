package com.weather.controller;

import com.weather.entity.SysUser;
import com.weather.service.SysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class UserController {

    @Autowired
    private SysUserService userService;

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String error,
                            @RequestParam(required = false) String logout,
                            @RequestParam(required = false) String expired,
                            Model model) {
        if ("true".equals(error)) model.addAttribute("errorMsg", "用户名或密码错误");
        if ("true".equals(logout)) model.addAttribute("successMsg", "已成功退出登录");
        if ("true".equals(expired)) model.addAttribute("errorMsg", "会话已过期，请重新登录");
        return "login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String doRegister(@RequestParam String username,
                             @RequestParam String password,
                             @RequestParam String nickname,
                             @RequestParam(required = false) String email,
                             @RequestParam(required = false) String phone,
                             Model model) {
        if (username == null || username.trim().isEmpty()) {
            model.addAttribute("errorMsg", "用户名不能为空");
            return "register";
        }
        if (password == null || password.length() < 6) {
            model.addAttribute("errorMsg", "密码长度不能少于6位");
            return "register";
        }
        SysUser user = new SysUser();
        user.setUsername(username.trim());
        user.setPassword(password);
        user.setNickname(nickname != null ? nickname.trim() : username.trim());
        user.setEmail(email != null ? email.trim() : "");
        user.setPhone(phone != null ? phone.trim() : "");
        if (userService.register(user)) {
            model.addAttribute("successMsg", "注册成功，请登录");
            return "login";
        }
        model.addAttribute("errorMsg", "用户名已存在");
        return "register";
    }

    @GetMapping("/user/profile")
    public String profilePage(Model model) {
        SysUser user = getCurrentUser();
        if (user == null) return "redirect:/login";
        model.addAttribute("user", user);
        return "profile";
    }

    @PostMapping("/user/profile")
    public String updateProfile(@RequestParam String nickname,
                                @RequestParam(required = false) String email,
                                @RequestParam(required = false) String phone,
                                @RequestParam(required = false) String city,
                                @RequestParam(required = false) String cityCode,
                                @RequestParam(required = false) Boolean pushEnabled,
                                RedirectAttributes redirectAttributes) {
        SysUser user = getCurrentUser();
        if (user == null) return "redirect:/login";
        user.setNickname(nickname);
        user.setEmail(email);
        user.setPhone(phone);
        if (city != null && !city.isEmpty()) user.setCity(city);
        if (cityCode != null && !cityCode.isEmpty()) user.setCityCode(cityCode);
        if (pushEnabled != null) user.setPushEnabled(pushEnabled);
        userService.updateUser(user);
        redirectAttributes.addFlashAttribute("successMsg", "个人信息更新成功");
        return "redirect:/user/profile";
    }

    @PostMapping("/user/password")
    public String changePassword(@RequestParam String oldPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes redirectAttributes) {
        SysUser user = getCurrentUser();
        if (user == null) return "redirect:/login";
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("errorMsg", "两次输入的密码不一致");
            return "redirect:/user/profile";
        }
        if (newPassword.length() < 6) {
            redirectAttributes.addFlashAttribute("errorMsg", "新密码长度不能少于6位");
            return "redirect:/user/profile";
        }
        if (userService.changePassword(user.getId(), oldPassword, newPassword)) {
            redirectAttributes.addFlashAttribute("successMsg", "密码修改成功");
        } else {
            redirectAttributes.addFlashAttribute("errorMsg", "原密码错误");
        }
        return "redirect:/user/profile";
    }

    private SysUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userService.findByUsername(auth.getName());
    }
}
