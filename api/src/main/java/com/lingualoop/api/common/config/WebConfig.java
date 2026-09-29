package com.lingualoop.api.common.config;

import java.nio.file.Path;

import com.lingualoop.api.audio.AudioProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AudioProperties audioProperties;

    public WebConfig(AudioProperties audioProperties) {
        this.audioProperties = audioProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(audioProperties.directory()).toAbsolutePath().normalize() + "/";
        registry.addResourceHandler("/audio/**").addResourceLocations("file:" + location);
    }
}
