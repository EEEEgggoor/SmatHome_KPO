using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace SmartHome
{
    public class Servo : Device
    {
        public int Angle
        { get; set; }

        public void SetAngle(int angle)
        {
            Angle = angle;
            Status = DeviceStatus.Active;
        }
    }
}