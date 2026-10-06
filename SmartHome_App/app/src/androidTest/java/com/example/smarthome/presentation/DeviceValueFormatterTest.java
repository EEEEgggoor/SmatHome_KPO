package com.example.smarthome.presentation;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class DeviceValueFormatterTest
{
    // 14.11.2023 22:14:20 в часовом поясе UTC.
    private static final long FIXED_CURRENT_TIME = 1700000060000L;

    private DeviceValueFormatter formatter;
    private TimeZone previousTimeZone;

    @Before
    public void setUp()
    {
        // Сохраняем исходный часовой пояс процесса тестов.
        previousTimeZone = TimeZone.getDefault();

        // Фиксируем UTC, чтобы ожидаемые даты были одинаковыми.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        Resources resources = createResourcesForLocale(
                Locale.forLanguageTag("ru-RU")
        );

        formatter = new DeviceValueFormatter(resources);
    }

    @After
    public void tearDown()
    {
        // Возвращаем часовой пояс после каждого теста.
        if (previousTimeZone != null)
        {
            TimeZone.setDefault(previousTimeZone);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullResources_throwsException()
    {
        new DeviceValueFormatter(null);
    }

    @Test
    public void formatWaterLevel_null_returnsNoData()
    {
        assertEquals(
                "Нет данных",
                formatter.formatWaterLevel(null)
        );
    }

    @Test
    public void formatWaterLevel_validValues_returnsPercent()
    {
        assertEquals("0 %", formatter.formatWaterLevel(0));
        assertEquals("64 %", formatter.formatWaterLevel(64));
        assertEquals("100 %", formatter.formatWaterLevel(100));
    }

    @Test
    public void formatTemperature_null_returnsNoData()
    {
        assertEquals(
                "Нет данных",
                formatter.formatTemperature(null)
        );
    }

    @Test
    public void formatTemperature_russianLocale_returnsTwoDecimalPlaces()
    {
        assertEquals(
                "22,40 °C",
                formatter.formatTemperature(22.4)
        );

        assertEquals(
                "22,46 °C",
                formatter.formatTemperature(22.456)
        );

        assertEquals(
                "-5,68 °C",
                formatter.formatTemperature(-5.678)
        );

        assertEquals(
                "0,00 °C",
                formatter.formatTemperature(0.0)
        );
    }

    @Test
    public void formatTemperature_englishLocale_usesDecimalPoint()
    {
        Resources englishResources = createResourcesForLocale(
                Locale.forLanguageTag("en-US")
        );

        DeviceValueFormatter englishFormatter =
                new DeviceValueFormatter(englishResources);

        assertEquals(
                "22.46 °C",
                englishFormatter.formatTemperature(22.456)
        );
    }

    @Test
    public void formatLastUpdate_null_returnsNoData()
    {
        assertEquals(
                "Нет данных",
                formatter.formatLastUpdate(null)
        );

        assertEquals(
                "Нет данных",
                formatter.formatLastUpdate(null, FIXED_CURRENT_TIME)
        );
    }

    @Test
    public void formatLastUpdate_sameTime_returnsJustNow()
    {
        assertEquals(
                "Только что",
                formatter.formatLastUpdate(
                        FIXED_CURRENT_TIME,
                        FIXED_CURRENT_TIME
                )
        );
    }

    @Test
    public void formatLastUpdate_59999MillisecondsAgo_returnsJustNow()
    {
        long lastUpdate = FIXED_CURRENT_TIME - 59_999L;

        assertEquals(
                "Только что",
                formatter.formatLastUpdate(
                        lastUpdate,
                        FIXED_CURRENT_TIME
                )
        );
    }

    @Test
    public void formatLastUpdate_exactlyOneMinuteAgo_returnsDate()
    {
        long lastUpdate = FIXED_CURRENT_TIME - 60_000L;

        assertEquals(
                "14.11.2023 22:13:20",
                formatter.formatLastUpdate(
                        lastUpdate,
                        FIXED_CURRENT_TIME
                )
        );
    }

    @Test
    public void formatLastUpdate_oldMeasurement_publicMethodReturnsDate()
    {
        // 01.01.1970 00:01:00 UTC — заведомо старое измерение.
        long lastUpdate = 60_000L;

        assertEquals(
                "01.01.1970 00:01:00",
                formatter.formatLastUpdate(lastUpdate)
        );
    }

    @Test
    public void formatLastUpdate_futureMeasurement_returnsDate()
    {
        long lastUpdate = FIXED_CURRENT_TIME + 1_000L;

        assertEquals(
                "14.11.2023 22:14:21",
                formatter.formatLastUpdate(
                        lastUpdate,
                        FIXED_CURRENT_TIME
                )
        );
    }

    @Test
    public void formatLastUpdate_otherTimeZone_changesDisplayedTime()
    {
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+03:00"));

        long lastUpdate = FIXED_CURRENT_TIME - 60_000L;

        assertEquals(
                "15.11.2023 01:13:20",
                formatter.formatLastUpdate(
                        lastUpdate,
                        FIXED_CURRENT_TIME
                )
        );
    }

    @Test
    public void formatLevelStatus_null_returnsNoData()
    {
        assertEquals(
                "Нет данных",
                formatter.formatLevelStatus(null)
        );
    }

    @Test
    public void formatLevelStatus_blankText_returnsNoData()
    {
        assertEquals(
                "Нет данных",
                formatter.formatLevelStatus("")
        );

        assertEquals(
                "Нет данных",
                formatter.formatLevelStatus("   ")
        );

        assertEquals(
                "Нет данных",
                formatter.formatLevelStatus("\t\n")
        );
    }

    @Test
    public void formatLevelStatus_validText_returnsTrimmedText()
    {
        assertEquals(
                "Норма",
                formatter.formatLevelStatus("  Норма  ")
        );

        assertEquals(
                "Низкий уровень",
                formatter.formatLevelStatus("Низкий уровень")
        );
    }

    @Test
    public void formatValveState_null_returnsNoData()
    {
        assertEquals(
                "Нет данных",
                formatter.formatValveState(null)
        );
    }

    @Test
    public void formatValveState_knownState_returnsCorrectText()
    {
        assertEquals(
                "Открыт",
                formatter.formatValveState(true)
        );

        assertEquals(
                "Закрыт",
                formatter.formatValveState(false)
        );
    }

    @Test
    public void formatOnlineState_returnsCorrectText()
    {
        assertEquals(
                "В сети",
                formatter.formatOnlineState(true)
        );

        assertEquals(
                "Не в сети",
                formatter.formatOnlineState(false)
        );
    }

    @Test
    public void formatPowerState_returnsCorrectText()
    {
        assertEquals(
                "Включено",
                formatter.formatPowerState(true)
        );

        assertEquals(
                "Выключено",
                formatter.formatPowerState(false)
        );
    }

    @Test
    public void formatBrightness_validValues_returnsPercent()
    {
        assertEquals("0 %", formatter.formatBrightness(0));
        assertEquals("75 %", formatter.formatBrightness(75));
        assertEquals("100 %", formatter.formatBrightness(100));
    }

    @Test
    public void formatLightTemperature_validValues_returnsPercent()
    {
        assertEquals("0 %", formatter.formatLightTemperature(0));
        assertEquals("40 %", formatter.formatLightTemperature(40));
        assertEquals("100 %", formatter.formatLightTemperature(100));
    }

    private Resources createResourcesForLocale(Locale locale)
    {
        Context appContext = InstrumentationRegistry
                .getInstrumentation()
                .getTargetContext();

        Configuration configuration = new Configuration(
                appContext.getResources().getConfiguration()
        );

        configuration.setLocale(locale);

        Context localizedContext = appContext.createConfigurationContext(
                configuration
        );

        return localizedContext.getResources();
    }
}