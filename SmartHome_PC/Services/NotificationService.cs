using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace SmartHome
{
    public class NotificationService
    {
        public event Action<string> Alert;
        public void Notify(string message)
        {
            if (Alert != null)
            {
                Alert(message);
            }
        }
    }
}