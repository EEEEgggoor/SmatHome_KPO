using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace SmartHome
{
    public class Scenario
    {
        public int Id
        { get; set; }

        public string Name
        { get; set; }

        public bool IsEnabled
        { get; set; }

        public Trigger Trigger
        { get; set; }

        public List<ScenarioAction> Actions
        { get; set; }

        public void Execute()
        {
            throw new System.NotImplementedException();
        }
    }
}