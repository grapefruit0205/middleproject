package com.middleproject.reminder.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class DeviceWebConfig implements WebMvcConfigurer {

    private final DeviceBearerAuthInterceptor interceptor;

    public DeviceWebConfig(DeviceBearerAuthInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/device/**")
                .excludePathPatterns("/api/device/exchange");
    }
}
