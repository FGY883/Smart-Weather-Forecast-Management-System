package com.weather.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.weather.entity.WeatherFavorite;
import java.util.List;

public interface WeatherFavoriteService extends IService<WeatherFavorite> {
    List<WeatherFavorite> getUserFavorites(Long userId);
    boolean addFavorite(Long userId, String city, String cityCode);
    boolean removeFavorite(Long userId, Long favoriteId);
    boolean isFavorited(Long userId, String cityCode);
    boolean removeByCityCode(Long userId, String cityCode);
}
