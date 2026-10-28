package com.easleydp.tempctrl.spring;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.easleydp.tempctrl.domain.Utils;
import com.easleydp.tempctrl.spring.config.AppProperties;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Autowired
    private AppProperties appProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws Exception {
                Utils.artificialDelayForDebugMode(appProperties.isDebug());
                return true;
            }
        });
    }

}
