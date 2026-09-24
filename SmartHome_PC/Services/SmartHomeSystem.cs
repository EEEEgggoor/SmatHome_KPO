using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace SmartHome
{
    public class SmartHomeSystem
    {
        private CommandValidator validator;
        private NotificationService notifier;
        private DeviceRepository repository;
        private DiscoveryService discovery;

        public SystemMode Mode
        { get; set; }

        public List<Device> Devices
        { get; set; }

        public List<Scenario> Scenarios
        { get; set; }

        public List<User> Users
        { get; set; }

        public List<LogEntry> Log
        { get; set; }

        public event Action<string> AlertRaised;

        public SmartHomeSystem()
        {
            validator = new CommandValidator();
            notifier = new NotificationService();
            notifier.Alert += OnAlert;
            Devices = new List<Device>();
            Scenarios = new List<Scenario>();
            Users = new List<User>();
            Log = new List<LogEntry>();
            Mode = SystemMode.Normal;
        }
        private void OnAlert(string msg)
        {
            if (AlertRaised != null)
            {
                AlertRaised(msg);
            }
        }

        public void SetMode(SystemMode mode)
        {
            Mode = mode;
            AddLog("Режим системы: " + mode);
            if (mode == SystemMode.Emergency)
            {
                foreach(Servo servo in Devices.OfType<Servo>())
                {
                    servo.SetAngle(0);
                    AddLog(servo.Name + ": закрыт");
                }
            }
        }

        public void RunScenario()
        {
            throw new System.NotImplementedException();
        }

        public bool SetBrightness(Light light, int level)
        {
            if (!validator.Validate(light, "PWM" + level))
            {
                AddLog("Команда отклонена");
                return false;
            }
            if(!light.Node.SendCommand("PWM" + level))
            {
                light.Status = DeviceStatus.Offline;
                AddLog("Потеряна связь с контроллером " + light.Node.Name);
                return false;
            }
            light.SetBrightness(level);
            AddLog(light.Name + ": яркость " + level + "%");
            return true;
        }

        public void AddLog(string message)
        {
            Log.Add(new LogEntry
            {
                Time = DateTime.Now,
                Message = message
            });
        }

        public void CheckSensor(Sensor sensor)
        {
            if (sensor.IsAlarm())
            {
                sensor.Status = DeviceStatus.Alarm;
                AddLog("ТРЕВОГА: " + sensor.Name);
                SetMode(SystemMode.Emergency);
                notifier.Notify("Тревога: " + sensor.Name);
            }
            else
            {
                sensor.Status = DeviceStatus.Normal;
            }
        }
    }
}