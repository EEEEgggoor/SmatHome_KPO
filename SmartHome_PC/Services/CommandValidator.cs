using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace SmartHome
{
    public class CommandValidator
    {
        public bool Validate(Device device, string command)
        {
            return device != null && device.Node != null && !string.IsNullOrWhiteSpace(command);
        }
    }
}