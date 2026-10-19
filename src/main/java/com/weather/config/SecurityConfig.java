package com.weather.config;

import com.weather.security.LoginAttemptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.ConcurrentSessionControlAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy(SessionRegistry sessionRegistry) {
        // 同一账号仅允许一个会话，新登录会自动踢掉旧会话
        ConcurrentSessionControlAuthenticationStrategy concurrent =
                new ConcurrentSessionControlAuthenticationStrategy(sessionRegistry);
        concurrent.setMaximumSessions(1);
        concurrent.setExceptionIfMaximumExceeded(false);
        return new CompositeSessionAuthenticationStrategy(Arrays.asList(
                concurrent,
                new RegisterSessionAuthenticationStrategy(sessionRegistry)
        ));
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           SessionAuthenticationStrategy sessionAuthStrategy,
                                           SessionRegistry sessionRegistry) throws Exception {
        AuthenticationSuccessHandler successHandler = (request, response, authentication) -> {
            loginAttemptService.reset(authentication.getName());
            response.sendRedirect(request.getContextPath() + "/");
        };
        AuthenticationFailureHandler failureHandler = (request, response, exception) -> {
            String username = request.getParameter("username");
            // 锁定状态（含 loadUserByUsername 提前抛出的 LockedException）：不计入失败、直接提示
            if (exception instanceof org.springframework.security.authentication.LockedException
                    || loginAttemptService.isLocked(username)) {
                response.sendRedirect(request.getContextPath() + "/login?locked=true");
            } else if (loginAttemptService.fail(username)) {
                response.sendRedirect(request.getContextPath() + "/login?locked=true");
            } else {
                response.sendRedirect(request.getContextPath() + "/login?error=true");
            }
        };

        http
            .sessionManagement(s -> s
                    .sessionAuthenticationStrategy(sessionAuthStrategy)
                    .maximumSessions(1)
                    .maxSessionsPreventsLogin(false)
                    .sessionRegistry(sessionRegistry))
            .authorizeRequests()
                .antMatchers("/", "/login", "/register", "/css/**", "/js/**", "/images/**", "/vendor/**", "/weather/**", "/api/aether/**", "/error").permitAll()
                .antMatchers("/user/**", "/favorite/**", "/push/**").authenticated()
                .anyRequest().authenticated()
            .and()
            .formLogin()
                .loginPage("/login")
                .loginProcessingUrl("/doLogin")
                .successHandler(successHandler)
                .failureHandler(failureHandler)
                .permitAll()
            .and()
            .logout()
                // POST 登出：避免 <img src="/logout"> 形式的 CSRF
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "POST"))
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            .and()
            .csrf(c -> c
                // Cookie 形式供前端 JS 读取 CookieCsrfTokenRepository 也可，
                // 但与 ServerCsrfTokenRequestAttributeHandler 配合最好。
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringAntMatchers("/api/aether/**")); // aether 异步查询只读，绕过 CSRF
        return http.build();
    }
}
