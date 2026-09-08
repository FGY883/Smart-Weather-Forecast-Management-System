package com.weather.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.weather.entity.WeatherFavorite;
import com.weather.mapper.WeatherFavoriteMapper;
import com.weather.service.WeatherFavoriteService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WeatherFavoriteServiceImpl extends ServiceImpl<WeatherFavoriteMapper, WeatherFavorite> implements WeatherFavoriteService {

    @Override
    public List<WeatherFavorite> getUserFavorites(Long userId) {
        return list(new LambdaQueryWrapper<WeatherFavorite>()
            .eq(WeatherFavorite::getUserId, userId)
            .orderByDesc(WeatherFavorite::getCreateTime));
    }

    @Override
    public boolean addFavorite(Long userId, String city, String cityCode) {
        if (isFavorited(userId, cityCode)) {
            return false;
        }
        WeatherFavorite fav = new WeatherFavorite();
        fav.setUserId(userId);
        fav.setCity(city);
        fav.setCityCode(cityCode);
        return save(fav);
    }

    @Override
    public boolean removeFavorite(Long userId, Long favoriteId) {
        return remove(new LambdaQueryWrapper<WeatherFavorite>()
            .eq(WeatherFavorite::getId, favoriteId)
            .eq(WeatherFavorite::getUserId, userId));
    }

    @Override
    public boolean isFavorited(Long userId, String cityCode) {
        return count(new LambdaQueryWrapper<WeatherFavorite>()
            .eq(WeatherFavorite::getUserId, userId)
            .eq(WeatherFavorite::getCityCode, cityCode)) > 0;
    }

    @Override
    public boolean removeByCityCode(Long userId, String cityCode) {
        return remove(new LambdaQueryWrapper<WeatherFavorite>()
            .eq(WeatherFavorite::getUserId, userId)
            .eq(WeatherFavorite::getCityCode, cityCode));
    }
}
