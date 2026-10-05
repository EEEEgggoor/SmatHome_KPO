package com.example.smarthome.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class RoomTest
{
    @Test
    public void constructor_validData_savesIdAndTrimmedName()
    {
        Room room = new Room(1, "  Гостиная  ");

        assertEquals(1, room.getId());
        assertEquals("Гостиная", room.getName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_zeroId_throwsException()
    {
        new Room(0, "Кухня");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_negativeId_throwsException()
    {
        new Room(-1, "Кухня");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullName_throwsException()
    {
        new Room(1, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_blankName_throwsException()
    {
        new Room(1, "   ");
    }

    @Test
    public void constructor_nameWith24Characters_isAllowed()
    {
        String name = "123456789012345678901234";

        Room room = new Room(1, name);

        assertEquals(name, room.getName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nameWith25Characters_throwsException()
    {
        new Room(1, "1234567890123456789012345");
    }

    @Test
    public void setName_validName_changesNameAndKeepsId()
    {
        Room room = new Room(1, "Кухня");

        room.setName("  Кухонька  ");

        assertEquals("Кухонька", room.getName());
        assertEquals(1, room.getId());
    }

    @Test
    public void setName_invalidName_keepsPreviousName()
    {
        Room room = new Room(1, "Кухня");

        String[] invalidNames =
                {
                        null,
                        "",
                        "   ",
                        "1234567890123456789012345"
                };

        for (String invalidName : invalidNames)
        {
            try
            {
                room.setName(invalidName);
                fail("Ожидалось исключение для некорректного имени.");
            }
            catch (IllegalArgumentException exception)
            {
                assertEquals("Кухня", room.getName());
                assertEquals(1, room.getId());
            }
        }
    }
}