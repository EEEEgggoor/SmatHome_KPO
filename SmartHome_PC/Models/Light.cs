using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace SmartHome
{
    public class Light : Device
    {
        public int Brightness
        { get; set; }

        public void SetBrightness(int level)
        {
            if (level < 0 || level > 100)
            {
                throw new ArgumentOutOfRangeException("level", "Яркость должна быть от 0 до 100");
            }
            Brightness = level;
            if (level > 0)
            {
                Status = DeviceStatus.Active;
            }
            else
            {
                Status = DeviceStatus.Inactive;
            }
        }
    }
}