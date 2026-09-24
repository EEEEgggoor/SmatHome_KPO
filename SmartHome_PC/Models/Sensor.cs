using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace SmartHome
{
    public abstract class Sensor : Device
    {
        public double Value
        { get; set; }

        public string Unit
        { get; set; }

        public double Threshold
        { get; set; }

        public virtual double ReadValue()
        {
            return Value;
        }

        public bool IsAlarm()
        {
            return Value >= Threshold;
        }
    }
}