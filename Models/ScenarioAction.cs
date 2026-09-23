using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace SmartHome
{
    public class ScenarioAction
    {
        public int TargetDeviceId
        { get; set; }

        public string Command
        { get; set; }

        public void Apply()
        {
            throw new System.NotImplementedException();
        }
    }
}