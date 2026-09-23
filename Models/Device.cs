using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace SmartHome
{
    public abstract class Device
    {
        public int Id
        { get; set; }

        public string Name
        { get; set; }

        public DeviceStatus Status
        { get; set; }

        public Room Room
        { get; set; }

        public ControllerNode Node
        { get; set; }

        public virtual void TurnOn()
        {
            Status = DeviceStatus.Active;
        }

        public virtual void TurnOff()
        {
            Status = DeviceStatus.Inactive;
        }
    }
}