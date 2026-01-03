package io.github.thegreywanderer_uc.chatr;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

public class ServerAITest {

    @Test
    public void testWeatherDetermination() {
        // Test clear weather
        World mockWorld = Mockito.mock(World.class);
        when(mockWorld.isThundering()).thenReturn(false);
        when(mockWorld.hasStorm()).thenReturn(false);

        String weather = ServerAI.determineWeather(mockWorld);
        assertEquals("clear", weather);

        // Test rain weather
        when(mockWorld.isThundering()).thenReturn(false);
        when(mockWorld.hasStorm()).thenReturn(true);

        weather = ServerAI.determineWeather(mockWorld);
        assertEquals("rain", weather);

        // Test thunderstorm weather
        when(mockWorld.isThundering()).thenReturn(true);
        when(mockWorld.hasStorm()).thenReturn(true);

        weather = ServerAI.determineWeather(mockWorld);
        assertEquals("thunderstorm", weather);
    }

    @Test
    public void testTimeFormatting() {
        // Test dawn (0 ticks = 6:00)
        assertEquals("06:00", ServerAI.formatTime(0));

        // Test noon (6000 ticks = 12:00)
        assertEquals("12:00", ServerAI.formatTime(6000));

        // Test dusk (12000 ticks = 18:00)
        assertEquals("18:00", ServerAI.formatTime(12000));

        // Test midnight (18000 ticks = 00:00)
        assertEquals("00:00", ServerAI.formatTime(18000));

        // Test wrap around (24000 ticks = 06:00)
        assertEquals("06:00", ServerAI.formatTime(24000));

        // Test partial minutes (6500 ticks ≈ 12:30)
        assertEquals("12:30", ServerAI.formatTime(6500));
    }

    @Test
    public void testCoordinateFormatting() {
        // Test positive coordinate
        assertEquals("123.5", ServerAI.formatCoordinate(123.456));

        // Test negative coordinate
        assertEquals("-456.8", ServerAI.formatCoordinate(-456.789));

        // Test zero
        assertEquals("0.0", ServerAI.formatCoordinate(0.0));

        // Test rounding
        assertEquals("1.2", ServerAI.formatCoordinate(1.23456));
    }
}