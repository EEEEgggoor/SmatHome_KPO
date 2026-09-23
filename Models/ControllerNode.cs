using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace SmartHome
{
    public class ControllerNode
    {
        public string Name
        { get; set; }

        public string IpAddress
        { get; set; }

        public bool IsOnline {get; set;} = true;

        public NodeType Type
        { get; set; }

        public bool SendCommand(string command)
        {
            return IsOnline;
        }
    }
}