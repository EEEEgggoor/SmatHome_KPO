namespace SmartHome
{
    partial class Form1
    {
        /// <summary>
        /// Обязательная переменная конструктора.
        /// </summary>
        private System.ComponentModel.IContainer components = null;

        /// <summary>
        /// Освободить все используемые ресурсы.
        /// </summary>
        /// <param name="disposing">истинно, если управляемый ресурс должен быть удален; иначе ложно.</param>
        protected override void Dispose(bool disposing)
        {
            if (disposing && (components != null))
            {
                components.Dispose();
            }
            base.Dispose(disposing);
        }

        #region Код, автоматически созданный конструктором форм Windows

        /// <summary>
        /// Требуемый метод для поддержки конструктора — не изменяйте 
        /// содержимое этого метода с помощью редактора кода.
        /// </summary>
        private void InitializeComponent()
        {
            this.trackBrightness = new System.Windows.Forms.TrackBar();
            this.lblBrightness = new System.Windows.Forms.Label();
            this.lblStatus = new System.Windows.Forms.Label();
            this.lblMode = new System.Windows.Forms.Label();
            this.chkLink = new System.Windows.Forms.CheckBox();
            this.btnLeak = new System.Windows.Forms.Button();
            this.btnReset = new System.Windows.Forms.Button();
            this.lstLog = new System.Windows.Forms.ListBox();
            ((System.ComponentModel.ISupportInitialize)(this.trackBrightness)).BeginInit();
            this.SuspendLayout();
            // 
            // trackBrightness
            // 
            this.trackBrightness.Location = new System.Drawing.Point(29, 10);
            this.trackBrightness.Maximum = 100;
            this.trackBrightness.Name = "trackBrightness";
            this.trackBrightness.Size = new System.Drawing.Size(160, 56);
            this.trackBrightness.TabIndex = 0;
            this.trackBrightness.Scroll += new System.EventHandler(this.trackBrightness_Scroll);
            // 
            // lblBrightness
            // 
            this.lblBrightness.AutoSize = true;
            this.lblBrightness.Location = new System.Drawing.Point(235, 50);
            this.lblBrightness.Name = "lblBrightness";
            this.lblBrightness.Size = new System.Drawing.Size(84, 16);
            this.lblBrightness.TabIndex = 1;
            this.lblBrightness.Text = "lblBrightness";
            this.lblBrightness.Click += new System.EventHandler(this.lblBrightness_Click);
            // 
            // lblStatus
            // 
            this.lblStatus.AutoSize = true;
            this.lblStatus.Location = new System.Drawing.Point(356, 50);
            this.lblStatus.Name = "lblStatus";
            this.lblStatus.Size = new System.Drawing.Size(58, 16);
            this.lblStatus.TabIndex = 2;
            this.lblStatus.Text = "lblStatus";
            this.lblStatus.Click += new System.EventHandler(this.lblStatus_Click);
            // 
            // lblMode
            // 
            this.lblMode.AutoSize = true;
            this.lblMode.Location = new System.Drawing.Point(461, 50);
            this.lblMode.Name = "lblMode";
            this.lblMode.Size = new System.Drawing.Size(56, 16);
            this.lblMode.TabIndex = 3;
            this.lblMode.Text = "lblMode";
            // 
            // chkLink
            // 
            this.chkLink.AutoSize = true;
            this.chkLink.Checked = true;
            this.chkLink.CheckState = System.Windows.Forms.CheckState.Checked;
            this.chkLink.Location = new System.Drawing.Point(578, 46);
            this.chkLink.Name = "chkLink";
            this.chkLink.Size = new System.Drawing.Size(190, 20);
            this.chkLink.TabIndex = 4;
            this.chkLink.Text = "«Связь с контроллером»";
            this.chkLink.UseVisualStyleBackColor = true;
            this.chkLink.CheckedChanged += new System.EventHandler(this.chkLink_CheckedChanged);
            // 
            // btnLeak
            // 
            this.btnLeak.Location = new System.Drawing.Point(29, 159);
            this.btnLeak.Name = "btnLeak";
            this.btnLeak.Size = new System.Drawing.Size(233, 23);
            this.btnLeak.TabIndex = 5;
            this.btnLeak.Text = "«Симулировать протечку»";
            this.btnLeak.UseVisualStyleBackColor = true;
            this.btnLeak.Click += new System.EventHandler(this.btnLeak_Click);
            // 
            // btnReset
            // 
            this.btnReset.Location = new System.Drawing.Point(29, 396);
            this.btnReset.Name = "btnReset";
            this.btnReset.Size = new System.Drawing.Size(168, 23);
            this.btnReset.TabIndex = 6;
            this.btnReset.Text = "«Сбросить тревогу»";
            this.btnReset.UseVisualStyleBackColor = true;
            this.btnReset.Click += new System.EventHandler(this.btnReset_Click);
            // 
            // lstLog
            // 
            this.lstLog.FormattingEnabled = true;
            this.lstLog.ItemHeight = 16;
            this.lstLog.Location = new System.Drawing.Point(464, 159);
            this.lstLog.Name = "lstLog";
            this.lstLog.Size = new System.Drawing.Size(304, 260);
            this.lstLog.TabIndex = 7;
            this.lstLog.SelectedIndexChanged += new System.EventHandler(this.lstLog_SelectedIndexChanged);
            // 
            // Form1
            // 
            this.AutoScaleDimensions = new System.Drawing.SizeF(8F, 16F);
            this.AutoScaleMode = System.Windows.Forms.AutoScaleMode.Font;
            this.ClientSize = new System.Drawing.Size(800, 450);
            this.Controls.Add(this.lstLog);
            this.Controls.Add(this.btnReset);
            this.Controls.Add(this.btnLeak);
            this.Controls.Add(this.chkLink);
            this.Controls.Add(this.lblMode);
            this.Controls.Add(this.lblStatus);
            this.Controls.Add(this.lblBrightness);
            this.Controls.Add(this.trackBrightness);
            this.Name = "Form1";
            this.Text = "Form1";
            this.Load += new System.EventHandler(this.Form1_Load);
            ((System.ComponentModel.ISupportInitialize)(this.trackBrightness)).EndInit();
            this.ResumeLayout(false);
            this.PerformLayout();

        }

        #endregion

        private System.Windows.Forms.TrackBar trackBrightness;
        private System.Windows.Forms.Label lblBrightness;
        private System.Windows.Forms.Label lblStatus;
        private System.Windows.Forms.Label lblMode;
        private System.Windows.Forms.CheckBox chkLink;
        private System.Windows.Forms.Button btnLeak;
        private System.Windows.Forms.Button btnReset;
        private System.Windows.Forms.ListBox lstLog;
    }
}

