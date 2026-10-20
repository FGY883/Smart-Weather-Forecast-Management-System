package com.weather.controller;

import com.weather.entity.SysUser;
import com.weather.service.SysUserService;
import com.weather.util.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class UserController {

    @Autowired
    private SysUserService userService;

    @Autowired
    private UserContext userContext;

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String error,
                            @RequestParam(required = false) String logout,
                            @RequestParam(required = false) String expired,
                            @RequestParam(required = false) String locked,
                            Model model) {
        if ("true".equals(error)) model.addAttribute("errorMsg", "用户名或密码错误");
        if ("true".equals(logout)) model.addAttribute("successMsg", "已成功退出登录");
        if ("true".equals(expired)) model.addAttribute("errorMsg", "会话已过期，请重新登录");
        if ("true".equals(locked)) model.addAttribute("errorMsg", "失败次数过多，账号已被临时锁定，请约 10 分钟后再试");
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
        if (username == null || username.trim().length() < 3 || username.trim().length() > 30) {
            model.addAttribute("errorMsg", "用户名长度需在 3-30 位之间");
            return "register";
        }
        if (!username.matches("^[A-Za-z0-9_\\u4e00-\\u9fa5]+$")) {
            model.addAttribute("errorMsg", "用户名仅支持中英文、数字和下划线");
            return "register";
        }
        if (password == null || password.length() < 6) {
            model.addAttribute("errorMsg", "密码长度不能少于 6 位");
            return "register";
        }
        if (email != null && !email.trim().isEmpty() && !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            model.addAttribute("errorMsg", "邮箱格式不正确");
            return "register";
        }
        if (phone != null && !phone.trim().isEmpty() && !phone.matches("^1[3-9]\\d{9}$")) {
            model.addAttribute("errorMsg", "手机号格式不正确");
            return "register";
        }

        SysUser user = new SysUser();
        user.setUsername(username.trim());
        user.setPassword(password);
        user.setNickname(nickname != null && !nickname.trim().isEmpty() ? nickname.trim() : username.trim());
        user.setEmail(email != null ? email.trim() : "");
        user.setPhone(phone != null ? phone.trim() : "");
        if (userService.register(user)) {
            model.addAttribute("successMsg", "注册成功，请登录");
            return "login";
        }
        model.addAttribute("errorMsg", "注册未完成，请更换用户名或稍后重试");
        return "register";
    }

    @GetMapping("/user/profile")
    public String profilePage(Model model) {
        SysUser user = userContext.currentUser();
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
        SysUser user = userContext.currentUser();
        if (user == null) return "redirect:/login";
        if (email != null && !email.trim().isEmpty() && !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            redirectAttributes.addFlashAttribute("errorMsg", "邮箱格式不正确");
            return "redirect:/user/profile";
        }
        if (phone != null && !phone.trim().isEmpty() && !phone.matches("^1[3-9]\\d{9}$")) {
            redirectAttributes.addFlashAttribute("errorMsg", "手机号格式不正确");
            return "redirect:/user/profile";
        }
        user.setNickname(nickname);
        user.setEmail(email);
        user.setPhone(phone);
        if (city != null && !city.isEmpty()) user.setCity(city);
        if (cityCode != null && !cityCode.isEmpty()) user.setCityCode(cityCode);
        if (pushEnabled != null) user.setPushEnabled(pushEnabled);
        userService.updateUser(user);
        redirectAttributes.addFlashAttribute("successMsg", "个人信息已更新");
        return "redirect:/user/profile";
    }

    @PostMapping("/user/password")
    public String changePassword(@RequestParam String oldPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes redirectAttributes) {
        SysUser user = userContext.currentUser();
        if (user == null) return "redirect:/login";
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("errorMsg", "两次输入的密码不一致");
            return "redirect:/user/profile";
        }
        if (newPassword.length() < 6) {
            redirectAttributes.addFlashAttribute("errorMsg", "新密码长度不能少于 6 位");
            return "redirect:/user/profile";
        }
        if (userService.changePassword(user.getId(), oldPassword, newPassword)) {
            redirectAttributes.addFlashAttribute("successMsg", "密码修改成功");
        } else {
            redirectAttributes.addFlashAttribute("errorMsg", "原密码错误");
        }
        return "redirect:/user/profile";
    }
}
