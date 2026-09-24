using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Data;
using System.Drawing;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace SmartHome
{
    public partial class Form1 : Form
    {

        private SmartHomeSystem system;
        private Light lamp;
        private LeakSensor leak;
        private ControllerNode lightNode;
        public Form1()
        {
            InitializeComponent();
            trackBrightness.MouseUp += trackBrightness_MouseUp;
            Text = "Умный дом";
            SetupDemo();
        }
        private void trackBrightness_MouseUp(object sender, MouseEventArgs e)
        {
            try
            {
                if (!system.SetBrightness(lamp, trackBrightness.Value))
                {
                    MessageBox.Show(
                        "Команда не выполнена: нет связи с контроллером",
                        "Ошибка",
                        MessageBoxButtons.OK,
                        MessageBoxIcon.Error);
                }
            }
            catch (ArgumentOutOfRangeException ex)
            {
                MessageBox.Show(ex.Message, "Ошибка ввода");
            }

            RefreshView();
        }
        private void SetupDemo()
        {
            system = new SmartHomeSystem();
            system.AlertRaised += msg =>
                MessageBox.Show(msg, "Авария", MessageBoxButtons.OK, MessageBoxIcon.Warning);

            var room = new Room { Id = 1, Name = "Гостиная" };
            lightNode = new ControllerNode { Name = "Освещение", IpAddress = "192.168.1.10", Type = NodeType.Lighting };
            var leakNode = new ControllerNode { Name = "Протечка", IpAddress = "192.168.1.11", Type = NodeType.Leak };

            lamp = new Light { Id = 1, Name = "Лампа", Room = room, Node = lightNode, Status = DeviceStatus.Inactive };
            leak = new LeakSensor { Id = 2, Name = "Датчик воды", Room = room, Node = leakNode, Threshold = 1, Status = DeviceStatus.Normal };
            var valve = new Servo { Id = 3, Name = "Кран", Room = room, Node = leakNode, Angle = 90, Status = DeviceStatus.Inactive };

            system.Devices.AddRange(new Device[] { lamp, leak, valve });
            RefreshView();
        }

        private void RefreshView()
        {
            lblBrightness.Text = "Яркость: " + lamp.Brightness + "%";
            lblStatus.Text = "Лампа: " + lamp.Status + "; датчик воды: " + leak.Status;
            lblMode.Text = "Режим: " + system.Mode;
            lstLog.Items.Clear();
            foreach (LogEntry entry in system.Log)
                lstLog.Items.Add(entry.Time.ToString("HH:mm:ss") + "  " + entry.Message);
        }

        private void Form1_Load(object sender, EventArgs e)
        {

        }

        private void lblStatus_Click(object sender, EventArgs e)
        {

        }

        private void lblBrightness_Click(object sender, EventArgs e)
        {

        }

        private void chkLink_CheckedChanged(object sender, EventArgs e)
        {
            lightNode.IsOnline = chkLink.Checked;
        }

        private void lstLog_SelectedIndexChanged(object sender, EventArgs e)
        {

        }

        private void trackBrightness_Scroll(object sender, EventArgs e)
        {
            //try
            //{
            //    if (!system.SetBrightness(lamp, trackBrightness.Value))
            //        MessageBox.Show("Команда не выполнена: нет связи с контроллером", "Ошибка",
            //            MessageBoxButtons.OK, MessageBoxIcon.Error);
            //}
            //catch (ArgumentOutOfRangeException ex)
            //{
            //    MessageBox.Show(ex.Message, "Ошибка ввода");
            //}
            //RefreshView();
        }

        private void btnLeak_Click(object sender, EventArgs e)
        {
            leak.Value = 1;
            system.CheckSensor(leak);
            RefreshView();
        }

        private void btnReset_Click(object sender, EventArgs e)
        {
            leak.Value = 0;
            system.CheckSensor(leak);
            system.SetMode(SystemMode.Normal);
            RefreshView();
        }
    }
}
