package org.homio.addon.weather;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.text.SimpleDateFormat;
import java.util.function.Function;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.homio.api.Context;
import org.homio.api.service.WeatherEntity;
import org.homio.api.service.WeatherEntity.WeatherInfo;
import org.homio.api.service.WeatherEntity.WeatherService;
import org.homio.api.state.DecimalType;
import org.homio.api.state.JsonType;
import org.homio.api.state.State;
import org.homio.api.workspace.WorkspaceBlock;
import org.homio.api.workspace.scratch.ArgumentType;
import org.homio.api.workspace.scratch.MenuBlock.ServerMenuBlock;
import org.homio.api.workspace.scratch.MenuBlock.StaticMenuBlock;
import org.homio.api.workspace.scratch.Scratch3Block;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.springframework.stereotype.Component;

@Getter
@Component
public class Scratch3WeatherBlocks extends Scratch3ExtensionBlocks {

    public static final String CITY = "CITY";

    @JsonIgnore
    private final Scratch3Block weatherApi;

    @JsonIgnore
    private final Scratch3Block getWeatherInfo;
    private final ServerMenuBlock weatherMenu;
    private final StaticMenuBlock<WeatherTypeInfo> weatherInfoType;
    private final Scratch3Block getWeatherInfoByTime;

    public Scratch3WeatherBlocks(Context context) {
        super("#3B798C", context, null, "weather");
        setParent(ScratchParent.misc);

        this.weatherMenu = menuServerItems("weatherMenu", WeatherEntity.class, "-");
        this.weatherInfoType = menuStatic("type", WeatherTypeInfo.class, WeatherTypeInfo.temperature);

        this.weatherApi = ofCity(blockReporter(11, "weather", "Weather of city [CITY] (JSON) of [VALUE]", this::readWeather));

        this.getWeatherInfo =
                ofCity(blockReporter(22, "weather_info", "Weather [TYPE] of city [CITY] of [VALUE]",
                        this::readWeatherInfo, scratch3Block -> {
                            scratch3Block.addArgument("TYPE", weatherInfoType);
                        }));

        this.getWeatherInfoByTime =
                ofCity(blockReporter(33, "weather_info_dt", "Weather [TYPE] of city [CITY] by time [TIMER] of [VALUE]",
                        this::readWeatherInfoByDate, scratch3Block -> {
                            scratch3Block.addArgument("TYPE", weatherInfoType);
                            scratch3Block.addArgument("TIMER", ArgumentType.calendar, "06/01/2023");
                        }));
    }

    @SneakyThrows
    private State readWeatherInfoByDate(WorkspaceBlock workspaceBlock) {
        WeatherEntity<? extends WeatherService> entity = workspaceBlock.getMenuValueEntityRequired(VALUE, weatherMenu);
        String date = workspaceBlock.getInputWorkspaceBlock("TIMER").getField("TEXT");
        long time = new SimpleDateFormat("dd/MM/yyyy").parse(date).getTime() / 1000;
        return workspaceBlock.getMenuValue("TYPE", weatherInfoType).fetcher.apply(
                getInfo(workspaceBlock, entity, time));
    }

    private State readWeatherInfo(WorkspaceBlock workspaceBlock) {
        WeatherEntity<? extends WeatherService> entity = workspaceBlock.getMenuValueEntityRequired(VALUE, weatherMenu);
        return workspaceBlock.getMenuValue("TYPE", weatherInfoType).fetcher.apply(
                getInfo(workspaceBlock, entity, null));
    }

    private State readWeather(WorkspaceBlock workspaceBlock) {
        WeatherEntity<? extends WeatherService> entity = workspaceBlock.getMenuValueEntityRequired(VALUE, weatherMenu);
        return new JsonType(getInfo(workspaceBlock, entity, null));
    }

    private static WeatherInfo getInfo(
            WorkspaceBlock workspaceBlock,
            WeatherEntity<? extends WeatherService> entity,
            Long timestamp) {
        return entity.getService().readWeather(workspaceBlock.getInputString(CITY), timestamp);
    }

    private Scratch3Block ofCity(Scratch3Block scratch3Block) {
        scratch3Block.addArgument(CITY, "London");
        scratch3Block.addArgument(VALUE, weatherMenu);
        return scratch3Block;
    }

    @RequiredArgsConstructor
    public enum WeatherTypeInfo {
        temperature(weatherInfo -> new DecimalType(weatherInfo.getTemperature())),
        humidity(weatherInfo -> new DecimalType(weatherInfo.getHumidity())),
        pressure(weatherInfo -> new DecimalType(weatherInfo.getPressure())),
        sunrise(weatherInfo -> new DecimalType(weatherInfo.getSunrise())),
        sunset(weatherInfo -> new DecimalType(weatherInfo.getSunset())),
        feelsLike(weatherInfo -> new DecimalType(weatherInfo.getFeelsLike())),
        visibility(weatherInfo -> new DecimalType(weatherInfo.getVisibility())),
        windDegree(weatherInfo -> new DecimalType(weatherInfo.getWindDegree())),
        windSpeed(weatherInfo -> new DecimalType(weatherInfo.getWindSpeed()));

        private final Function<WeatherInfo, State> fetcher;
    }
}
