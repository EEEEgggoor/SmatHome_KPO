using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace SmartHome
{
    public class User
    {
        public int Id
        { get; set; }

        public string Login
        { get; set; }

        public UserRole Role
        { get; set; }

        public bool CanControl()
        {
            throw new System.NotImplementedException();
        }
    }
}